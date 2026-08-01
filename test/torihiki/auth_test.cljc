(ns torihiki.auth-test
  "Before this existed, `:account` was a number a client asserted."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.auth :as auth]
            [torihiki.state :as st]))

(def mkt (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))
(def ^:const chain "torihiki-test")

;; A stand-in signer: a signature is the payload plus the key. It is enough to
;; prove the PLUMBING — that what is signed is what is checked, that the key
;; binds, that the nonce is single-use — without importing a JVM-only crypto
;; library into a namespace whose point is that a browser can re-verify.
(defn- sign [k payload] (str k "|" payload))
(defn- verify [k payload sig] (= sig (sign k payload)))

(defn- fresh []
  (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}}))

(defn- envelope
  [account nonce key tx]
  {:tx tx :account account :nonce nonce :pubkey key
   :sig (sign key (auth/signing-payload chain account nonce tx))})

(defn- run [ex txs]
  (st/apply-block ex {:height 1 :ts 1 :txs txs}
                  {:verify-fn verify :chain-id chain}))

;; ── the payload ─────────────────────────────────────────────────────────────

(deftest the-payload-covers-the-chain
  (testing "a signature made for one chain must not authorise the other"
    (is (not= (auth/signing-payload "mainnet" 1 1 {:tx :deposit :amount 5})
              (auth/signing-payload "testnet" 1 1 {:tx :deposit :amount 5})))))

(deftest the-payload-covers-the-nonce-and-the-account
  (is (not= (auth/signing-payload chain 1 1 {:tx :deposit :amount 5})
            (auth/signing-payload chain 1 2 {:tx :deposit :amount 5})))
  (is (not= (auth/signing-payload chain 1 1 {:tx :deposit :amount 5})
            (auth/signing-payload chain 2 1 {:tx :deposit :amount 5}))))

(deftest the-payload-covers-every-field-that-changes-behaviour
  (let [base {:tx :order :market 1 :side 0 :level 100 :qty 5 :flags 0}
        p #(auth/signing-payload chain 1 1 %)]
    (doseq [[k v] [[:side 1] [:level 101] [:qty 6] [:flags 2] [:market 2]]]
      (is (not= (p base) (p (assoc base k v)))
          (str "changing " k " must change the payload")))
    (testing "and a trigger's embedded order too"
      (let [t {:tx :trigger :market 1 :trigger-price 90 :direction :below
               :order {:side 1 :level 0 :qty 5 :flags 0}}]
        (is (not= (p t) (p (assoc-in t [:order :qty] 6))))
        (is (not= (p t) (p (assoc t :direction :above))))))))

;; ── authentication ──────────────────────────────────────────────────────────

(deftest a-good-signature-is-accepted-and-binds-the-key
  (let [ex (run (fresh) [(envelope 5 1 "key-5" {:tx :deposit :amount 1000})])]
    (is (empty? (:rejected ex)))
    (is (= 1000 (get-in ex [:clearing :accounts 5 :collateral])))
    (is (= "key-5" (get-in ex [:account-keys 5])) "first use binds the key")
    (is (= 1 (get-in ex [:nonces 5])))))

(deftest an-unsigned-transaction-is-refused
  (let [ex (run (fresh) [{:tx {:tx :deposit :amount 1000} :account 5 :nonce 1}])]
    (is (= [:unsigned] (mapv :reason (:rejected ex))))
    (is (nil? (get-in ex [:clearing :accounts 5])))))

(deftest a-forged-signature-is-refused
  (let [e (envelope 5 1 "key-5" {:tx :deposit :amount 1000})
        ex (run (fresh) [(assoc e :sig "not-the-signature")])]
    (is (= [:bad-signature] (mapv :reason (:rejected ex))))
    (is (nil? (get-in ex [:clearing :accounts 5])))))

(deftest signing-someone-elses-account-is-refused
  (testing "the whole reason this namespace exists"
    (let [ex (run (fresh) [(envelope 5 1 "key-5" {:tx :deposit :amount 1000})])
          ;; attacker holds their own key and claims account 5
          attack (envelope 5 2 "attacker-key" {:tx :withdraw :amount 1000})
          ex' (st/apply-block ex {:height 2 :ts 2 :txs [attack]}
                              {:verify-fn verify :chain-id chain})]
      (is (= [:wrong-key] (mapv :reason (:rejected ex'))))
      (is (= 1000 (get-in ex' [:clearing :accounts 5 :collateral]))
          "the balance is untouched"))))

(deftest a-signature-from-another-chain-is-refused
  (let [tx {:tx :deposit :amount 1000}
        e {:tx tx :account 5 :nonce 1 :pubkey "key-5"
           :sig (sign "key-5" (auth/signing-payload "other-chain" 5 1 tx))}
        ex (run (fresh) [e])]
    (is (= [:bad-signature] (mapv :reason (:rejected ex))))))

;; ── replay ──────────────────────────────────────────────────────────────────

(deftest a-signature-cannot-be-replayed
  (let [e (envelope 5 1 "key-5" {:tx :deposit :amount 1000})
        ex (run (fresh) [e e e])]
    (is (= 1000 (get-in ex [:clearing :accounts 5 :collateral]))
        "credited exactly once")
    (is (= [:bad-nonce :bad-nonce] (mapv :reason (:rejected ex))))))

(deftest nonces-must-be-strictly-sequential
  (testing "a gap would let a holder sign several and choose the order later"
    (let [ex (run (fresh) [(envelope 5 1 "key-5" {:tx :deposit :amount 100})
                           (envelope 5 3 "key-5" {:tx :deposit :amount 100})])]
      (is (= [:bad-nonce] (mapv :reason (:rejected ex))))
      (is (= 100 (get-in ex [:clearing :accounts 5 :collateral])))
      (is (= 1 (get-in ex [:nonces 5]))))))

(deftest an-authenticated-but-invalid-transaction-still-spends-its-nonce
  (testing "otherwise the signature stays reusable, which is what the nonce is for"
    (let [ex (run (fresh) [(envelope 5 1 "key-5" {:tx :deposit :amount -5})])]
      (is (= [:bad-amount] (mapv :reason (:rejected ex)))
          "rejected by validation, not by authentication")
      (is (= 1 (get-in ex [:nonces 5])) "and the nonce is spent")
      (is (= "key-5" (get-in ex [:account-keys 5])) "and the key is bound"))))

(deftest a-failed-authentication-spends-nothing
  (let [ex (run (fresh) [(assoc (envelope 5 1 "key-5" {:tx :deposit :amount 100})
                                :sig "forged")])]
    (is (nil? (get-in ex [:nonces 5])) "never from the account, so nothing consumed")
    (is (nil? (get-in ex [:account-keys 5])) "and no key was bound")))

;; ── it is state ─────────────────────────────────────────────────────────────

(deftest the-root-commits-to-nonces-and-keys
  (let [a (run (fresh) [(envelope 5 1 "key-5" {:tx :deposit :amount 1000})])
        ;; the same resulting balance, reached without authentication
        b (st/apply-block (fresh) {:height 1 :ts 1
                                   :txs [{:tx :deposit :account 5 :amount 1000}]})]
    (is (= (get-in a [:clearing :accounts 5 :collateral])
           (get-in b [:clearing :accounts 5 :collateral])))
    (is (not= (st/state-root a) (st/state-root b))
        "identical balances, different auth state, different root")))

(deftest a-different-bound-key-changes-the-root
  (let [a (run (fresh) [(envelope 5 1 "key-a" {:tx :deposit :amount 1})])
        c (run (fresh) [(envelope 5 1 "key-b" {:tx :deposit :amount 1})])]
    (is (not= (st/state-root a) (st/state-root c)))))

;; ── it composes with the rest ───────────────────────────────────────────────

(deftest authenticated-trading-works-end-to-end
  (let [ex (-> (fresh)
               (st/apply-block
                {:height 1 :ts 1
                 :txs [(envelope 1 1 "k1" {:tx :deposit :amount 100000000})
                       (envelope 2 1 "k2" {:tx :deposit :amount 100000000})
                       (envelope 1 2 "k1" {:tx :oracle :market 1 :price 1000})
                       (envelope 2 2 "k2" {:tx :order :market 1 :side bk/ask
                                           :level 1001 :qty 50})
                       (envelope 1 3 "k1" {:tx :order :market 1 :side bk/bid
                                           :level 1001 :qty 20})]}
                {:verify-fn verify :chain-id chain}))]
    (is (empty? (:rejected ex)))
    (is (= 20 (:size (cl/position (:clearing ex) 1 1))))
    (is (= -20 (:size (cl/position (:clearing ex) 2 1))))
    (is (= 3 (get-in ex [:nonces 1])))
    (is (= 2 (get-in ex [:nonces 2])))))

(deftest unauthenticated-mode-still-works
  (testing "replaying an already-agreed log does not re-check signatures"
    (let [ex (st/apply-block (fresh) {:height 1 :ts 1
                                      :txs [{:tx :deposit :account 5 :amount 10}]})]
      (is (empty? (:rejected ex)))
      (is (= 10 (get-in ex [:clearing :accounts 5 :collateral]))))))
