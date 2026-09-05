;; falsify-1: account-binding race under a Byzantine leader (no code changes).
;; Precedent: engi `torihiki-on-engi` harness (engi/README.md:160-205) —
;; account 1 ended at -50, the thief's order, with 34 owner txs refused.
;; Claim under test (torihiki README + auth.cljc:42-56): WITHOUT derive-fn,
;; first-come binding lets the block proposer claim any unbound id before the
;; owner, and the owner is then refused :wrong-key permanently. WITH
;; derive-account, the thief's claim is refused :not-your-account.
(ns falsify1
  (:require [clojure.pprint :refer [pprint]]
            [torihiki.clearing :as cl]
            [torihiki.state :as st]
            [torihiki.auth :as auth]
            [clojure.string :as str]))

(def chain "falsify-1")
(def mkt (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))

(defn- sign [k payload] (str k "|" payload))
(defn- verify [k payload sig] (= sig (sign k payload)))

(def owner-key "key-owner")
(def thief-key "key-thief")            ; the Byzantine leader's validator key
(def victim-id 1)

(defn- derive-account [pubkey] (if (= pubkey owner-key) victim-id 999))

(defn- env
  [account nonce key tx]
  {:tx tx :account account :nonce nonce :pubkey key
   :sig (sign key (auth/signing-payload chain account nonce tx))})

(defn- fresh []
  (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}}))

(defn- apply-block*
  [ex height txs opts]
  (st/apply-block ex {:height height :ts height :txs txs}
                  (merge {:verify-fn verify :chain-id chain} opts)))

;; Owner's deposit then a market buy — the honest path the owner would take.
(defn- owner-txs [base-nonce n]
  (vec (concat [(env victim-id base-nonce owner-key {:tx :deposit :amount 1000})]
               (for [i (range (dec n))]
                 (env victim-id (+ base-nonce 1 i) owner-key
                      {:tx :order :market 1 :side 0 :level 990 :qty 1 :flags 0})))))

;; The leader injects its own order as the victim's id, signed with its own
;; validator key, first in the block it proposes.
(def thief-claim
  (env victim-id 1 thief-key {:tx :order :market 1 :side 1 :level 1010 :qty 5 :flags 0}))

(defn- summarize [ex]
  {:rejected-reasons (frequencies (map :reason (:rejected ex)))
   :binding (get-in ex [:account-keys victim-id])
   :victim-collateral (get-in ex [:clearing :accounts victim-id :collateral])
   :account-1-txs (count (filter #(= victim-id (:account %)) (:rejected ex)))})

(defn -main []
  (println "=== falsify-1: account-binding race (engi harness precedent) ===")

  ;; Scenario A — no derive-fn (README: "ids stay first-come"). The Byzantine
  ;; leader proposes block 1 with its own claim for id 1 BEFORE the owner's
  ;; transactions, which it saw in the mempool (pending binding).
  (let [ex (apply-block* (fresh) 1 (into [thief-claim] (owner-txs 2 5)) {})]
    (println "\n[A] no derive-fn, leader front-runs the pending binding:")
    (clojure.pprint/pprint (summarize ex))
    ;; Permanence: the owner retries in a later block with fresh nonces.
    (let [ex2 (apply-block* ex 2 (owner-txs 10 3) {})]
      (println "    owner retries next block:")
      (clojure.pprint/pprint (select-keys (summarize ex2) [:rejected-reasons :binding]))))

  ;; Scenario B — derive-fn supplied: a key may only bind the id derived from it.
  (let [ex (apply-block* (fresh) 1 (into [thief-claim] (owner-txs 1 5))
                         {:derive-account derive-account})]
    (println "\n[B] derive-fn supplied, same leader behaviour:")
    (clojure.pprint/pprint (summarize ex)))

  ;; Determinism: the scenario re-run produces the same outcome (replica parity).
  (let [runs (repeatedly 3 #(apply-block* (fresh) 1
                                          (into [thief-claim] (owner-txs 2 5)) {}))]
    (println "\n[C] determinism across 3 re-runs (no derive-fn):"
             (if (apply = (map summarize runs)) "IDENTICAL" "DIVERGENT"))))

(-main)
