(ns torihiki.log-test
  (:require [clojure.test :refer [deftest is testing]]
            [chain.core :as chain]
            [ipld.core :as ipld]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.state :as st]
            [torihiki.log :as log]))

(def mkt (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))

(defn- fresh [] (st/new-exchange {:market mkt
                                  :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}}))

(defn- store []
  (let [s (atom {})]
    {:put! (fn [cid bytes] (swap! s assoc cid bytes))
     :get-fn (fn [cid] (get @s cid))
     :raw s}))

(defn- blocks []
  [{:height 1 :ts 100
    :txs [{:tx :deposit :account 10 :amount 1000000}
          {:tx :deposit :account 11 :amount 1000000}]}
   {:height 2 :ts 200
    :txs [{:tx :order :account 10 :market 1 :side bk/ask :level 1000 :qty 5}
          {:tx :order :account 11 :market 1 :side bk/bid :level 1000 :qty 3}]}
   {:height 3 :ts 300
    :txs [{:tx :oracle :market 1 :price 1000}
          {:tx :order :account 11 :market 1 :side bk/bid :level 1000 :qty 2}]}])

;; ── wire codec ──────────────────────────────────────────────────────────────

(deftest wire-round-trip
  (doseq [tx (mapcat :txs (blocks))]
    (is (= tx (log/wire->tx (log/tx->wire tx))) (str "round trip " (:tx tx)))))

(deftest wire-rejects-what-it-cannot-validate
  (is (thrown? #?(:clj Exception :cljs :default)
               (log/tx->wire {:tx :not-a-real-kind :account 1}))
      "an unknown transaction kind must not reach the wire")
  (is (thrown? #?(:clj Exception :cljs :default)
               (log/tx->wire {:tx :deposit :account 1 :amount "lots"}))
      "a non-integer field must not reach the wire"))

;; ── producing and checking ──────────────────────────────────────────────────

(deftest log-is-well-formed-and-replays
  (let [{:keys [put! get-fn]} (store)
        r (log/commit-blocks! (fresh) put! get-fn nil (blocks))]
    (testing "the chain itself is intact"
      (is (chain/verify-chain get-fn (:cid r))))
    (testing "and replay agrees with every recorded root"
      (let [v (log/replay get-fn (:cid r) fresh)]
        (is (:ok v))
        (is (= 3 (:blocks v)))))
    (testing "the tip root matches what a replay recomputes"
      (is (= (log/tip-root get-fn (:cid r))
             (st/state-root (:exchange (log/replay get-fn (:cid r) fresh))))))
    (testing "the roots actually differ block to block"
      (is (= 3 (count (distinct (:roots r))))
          "identical roots would mean the blocks did nothing"))))

(deftest replay-catches-a-sequencer-that-invents-a-root
  (testing "a well-formed, untampered chain can still be a lie"
    (let [{:keys [put! get-fn raw]} (store)
          ;; the sequencer applies block 1 honestly, then publishes block 2
          ;; with a state root it did not compute
          ex1 (st/apply-block (fresh) (first (blocks)))
          cid1 (chain/commit! put! get-fn
                              (log/block->wire (first (blocks)) (st/state-root ex1) nil)
                              nil)
          cid2 (chain/commit! put! get-fn
                              (log/block->wire (second (blocks)) 123456789 nil)
                              cid1)]
      (is (chain/verify-chain get-fn cid2)
          "verify-chain passes — the log is internally consistent")
      (let [v (log/replay get-fn cid2 fresh)]
        (is (not (:ok v)) "but replay contradicts it")
        (is (= 2 (:height v)))
        (is (= 123456789 (:recorded v)))
        (is (not= 123456789 (:recomputed v))))
      (is (pos? (count @raw))))))

(deftest tampering-with-a-committed-block-breaks-the-chain
  (let [{:keys [put! get-fn raw]} (store)
        r (log/commit-blocks! (fresh) put! get-fn nil (blocks))
        entries (chain/chain get-fn (:cid r))
        victim (second entries)]
    (is (chain/verify-chain get-fn (:cid r)) "intact before tampering")
    ;; rewrite the stored bytes at an existing CID: a store that lies
    (swap! raw assoc (:cid victim)
           (ipld/encode {"state" (assoc (:state victim) "ts" 999999)
                         "prev" (some-> (:prev victim) ipld/link)
                         "seq" (:seq victim)}))
    (is (not (chain/verify-chain get-fn (:cid r)))
        "the CID no longer re-derives from its own bytes")))

;; ── signatures ──────────────────────────────────────────────────────────────
;;
;; A stand-in signer, not a cryptographic one. It is enough to prove the
;; PLUMBING — that what is signed is what is checked, that position is
;; covered, and that an unsigned block fails — without importing a JVM-only
;; crypto library into a namespace whose point is that a browser can run it.

(defn- fake-sign [payload] (str "sig:" (hash payload)))
(defn- fake-verify [payload sig] (= sig (fake-sign payload)))

(deftest signatures-verify-when-produced-honestly
  (let [{:keys [put! get-fn]} (store)
        r (log/commit-blocks! (fresh) put! get-fn nil (blocks) fake-sign)
        v (log/verify-signatures get-fn (:cid r) fake-verify)]
    (is (:ok v))
    (is (= 3 (:blocks v)))))

(deftest an-unsigned-block-is-a-failure-not-a-skip
  (let [{:keys [put! get-fn]} (store)
        r (log/commit-blocks! (fresh) put! get-fn nil (blocks))]  ; no sign-fn
    (let [v (log/verify-signatures get-fn (:cid r) fake-verify)]
      (is (not (:ok v)))
      (is (= :unsigned (:reason v)))
      (is (= 1 (:height v)) "it fails at the first unsigned block"))))

(deftest a-forged-signature-is-rejected
  (let [{:keys [put! get-fn]} (store)
        r (log/commit-blocks! (fresh) put! get-fn nil (blocks) (constantly "sig:garbage"))
        v (log/verify-signatures get-fn (:cid r) fake-verify)]
    (is (not (:ok v)))
    (is (= :bad-signature (:reason v)))))

(deftest the-signature-covers-the-parent-not-just-the-block
  (testing "the same block at a different position signs differently"
    (let [b (second (blocks))
          root 42
          at-genesis (log/signing-payload (:height b) (:ts b) root nil (:txs b))
          at-parent (log/signing-payload (:height b) (:ts b) root "bafyfake" (:txs b))]
      (is (not= at-genesis at-parent)
          "otherwise a signed block could be spliced under any parent"))))

(deftest the-signature-covers-the-state-root
  (let [b (second (blocks))]
    (is (not= (log/signing-payload 2 200 1 nil (:txs b))
              (log/signing-payload 2 200 2 nil (:txs b)))
        "a signature that ignored the root would authenticate an invented one")))
