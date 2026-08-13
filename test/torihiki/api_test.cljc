(ns torihiki.api-test
  "The property that matters most here is not that bad input is refused — it
  is that refusing it cannot stop the chain."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.api :as api]
            [torihiki.state :as st]
            [torihiki.auth :as auth]))

(def mkt (assoc (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                :taker-fee-rate 350000 :maker-fee-rate 0))

(defn- fresh []
  (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}}))

(defn- funded []
  (-> (fresh)
      (st/apply-tx {:tx :deposit :account 1 :amount 100000000})
      (st/apply-tx {:tx :deposit :account 2 :amount 100000000})
      (st/apply-tx {:tx :oracle :market 1 :price 1000})))

;; ── validation ──────────────────────────────────────────────────────────────

(deftest good-transactions-validate
  (let [ex (funded)]
    (doseq [t [{:tx :order :account 1 :market 1 :side 0 :level 999 :qty 5}
               {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 5 :flags 3}
               {:tx :cancel :market 1 :oid 12345}
               {:tx :deposit :account 1 :amount 10}
               {:tx :withdraw :account 1 :amount 10}
               {:tx :oracle :market 1 :price 1000}
               {:tx :funding-sample :market 1}
               {:tx :funding-settle :market 1}
               {:tx :liquidate :market 1}
               {:tx :trigger :account 1 :market 1 :trigger-price 900 :direction :below
                :order {:side 1 :level 0 :qty 5}}]]
      (is (nil? (api/validate ex t)) (str t)))))

(deftest every-rejection-is-in-the-closed-set
  (let [ex (funded)
        bad [{:tx :nonsense}
             {:tx :order :account 1 :market 99 :side 0 :level 1 :qty 1}
             {:tx :order :account "one" :market 1 :side 0 :level 1 :qty 1}
             {:tx :order :account 1 :market 1 :side 5 :level 1 :qty 1}
             {:tx :order :account 1 :market 1 :side 0 :level 1 :qty 0}
             {:tx :order :account 1 :market 1 :side 0 :level 1 :qty -3}
             {:tx :order :account 1 :market 1 :side 0 :level 999999 :qty 1}
             {:tx :order :account 1 :market 1 :side 0 :level -1 :qty 1}
             {:tx :order :account 1 :market 1 :side 0 :level 1 :qty 1 :flags 99}
             {:tx :deposit :account 1 :amount 0}
             {:tx :deposit :account 1 :amount -5}
             {:tx :cancel :market 1 :oid nil}
             {:tx :oracle :market 1 :price 0}
             {:tx :trigger :account 1 :market 1 :trigger-price 0 :direction :below
              :order {:side 1 :level 0 :qty 5}}
             {:tx :trigger :account 1 :market 1 :trigger-price 9 :direction :sideways
              :order {:side 1 :level 0 :qty 5}}
             {:tx :trigger :account 1 :market 1 :trigger-price 9 :direction :below
              :order {:side 1 :level 0 :qty 0}}]]
    (doseq [t bad]
      (let [r (api/validate ex t)]
        (is (some? r) (str "should be rejected: " t))
        (is (contains? api/reasons r)
            (str "reason " r " is outside the closed set, for " t))))))

;; ── the liveness property ───────────────────────────────────────────────────

(deftest a-malformed-transaction-cannot-halt-a-block
  (testing "the whole reason validation was pulled out of apply-tx"
    (let [ex (funded)
          block {:height 1 :ts 1
                 :txs [{:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 5}
                       ;; a level far outside the ladder — this used to throw
                       {:tx :order :account 2 :market 1 :side 0 :level 99999999 :qty 5}
                       {:tx :nonsense :market 1}
                       {:tx :order :account 2 :market 1 :side 0 :level 1001 :qty 5}]}
          ex' (st/apply-block ex block)]
      (is (= 2 (count (:rejected ex'))) "both bad ones were refused")
      (is (= [:bad-price-level :unknown-tx] (mapv :reason (:rejected ex'))))
      (is (= [1 2] (mapv :index (:rejected ex')))
          "the index identifies which transaction was refused")
      (testing "and the good transactions on either side of them still executed"
        (is (= 5 (:size (cl/position (:clearing ex') 2 1))))
        (is (= -5 (:size (cl/position (:clearing ex') 1 1))))))))

(deftest a-block-of-nothing-but-garbage-still-produces-a-state
  (let [ex (funded)
        ex' (st/apply-block ex {:height 1 :ts 1
                                :txs (vec (repeat 50 {:tx :order :account 1 :market 1
                                                      :side 9 :level -7 :qty -1}))})]
    (is (= 50 (count (:rejected ex'))))
    (is (string? (st/state-root ex')))))

(deftest the-root-commits-to-what-was-refused
  (testing "a sequencer must not be able to drop a transaction invisibly"
    (let [ex (funded)
          good {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 5}
          bad {:tx :order :account 1 :market 1 :side 0 :level 99999999 :qty 5}
          with-bad (st/apply-block ex {:height 1 :ts 1 :txs [good bad]})
          without (st/apply-block (funded) {:height 1 :ts 1 :txs [good]})]
      (is (= 1 (count (:rejected with-bad))))
      (is (= 0 (count (:rejected without))))
      (is (not= (st/state-root with-bad) (st/state-root without))
          "identical executed effects, different rejections, different root"))))

;; ── read models ─────────────────────────────────────────────────────────────

(deftest book-snapshot-is-client-shaped
  (let [ex (-> (funded)
               (st/apply-tx {:tx :order :account 1 :market 1 :side 0 :level 998 :qty 30})
               (st/apply-tx {:tx :order :account 1 :market 1 :side 0 :level 999 :qty 10})
               (st/apply-tx {:tx :order :account 2 :market 1 :side 1 :level 1001 :qty 20}))
        snap (api/book-snapshot ex 1 5)]
    (is (= [999 998] (mapv :level (:bids snap))) "best first")
    (is (= [10 40] (mapv :cum (:bids snap))) "cumulative running total")
    (is (= [1001] (mapv :level (:asks snap))))
    (is (= 3 (:resting snap)))))

(deftest account-state-answers-can-i-open-this
  (let [ex (-> (funded)
               (st/apply-tx {:tx :order :account 2 :market 1 :side 1 :level 1001 :qty 50})
               (st/apply-tx {:tx :order :account 1 :market 1 :side 0 :level 1001 :qty 20}))
        a (api/account-state ex 1)]
    (is (= 1 (:account a)))
    (is (= 20 (get-in a [:positions 1 :size])))
    (is (pos? (:equity a)))
    (is (pos? (:initial-margin a)))
    (is (= (:free-collateral a)
           (max 0 (- (:equity a) (:initial-margin a))))
        "free collateral is equity less what open positions already consume")
    (is (false? (:liquidatable a)))))

(deftest account-state-surfaces-triggers
  (let [ex (-> (funded)
               (st/apply-tx {:tx :order :account 2 :market 1 :side 1 :level 1001 :qty 50})
               (st/apply-tx {:tx :order :account 1 :market 1 :side 0 :level 1001 :qty 20})
               (st/apply-tx {:tx :trigger :account 1 :market 1
                             :trigger-price 900 :direction :below
                             :order {:side 1 :level 0 :qty 20}}))
        a (api/account-state ex 1)]
    (is (= 1 (count (:triggers a))))
    (is (= 900 (:trigger-price (first (:triggers a)))))
    (is (= 1 (:market (first (:triggers a)))))))

(deftest market-info-has-no-internals
  (let [m (api/market-info (funded) 1)]
    (is (= 40 (:max-leverage m)))
    (is (pos? (:maintenance-margin-rate m)))
    (is (contains? m :mark))
    (is (contains? m :last))
    (is (not (contains? m :books)) "read models do not hand back the slab layout")))

;; ── open-interest cap ───────────────────────────────────────────────────────

(def capped (assoc (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1
                               :open-interest-cap 200})
                   :taker-fee-rate 0 :maker-fee-rate 0))

(defn- capped-ex []
  (-> (st/new-exchange {:market capped :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}})
      (st/apply-tx {:tx :deposit :account 1 :amount 100000000})
      (st/apply-tx {:tx :deposit :account 2 :amount 100000000})
      (st/apply-tx {:tx :oracle :market 1 :price 1000})))

(deftest an-order-within-the-cap-is-allowed
  (is (nil? (api/validate (capped-ex)
                          {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 150}))))

(deftest an-order-that-could-breach-the-cap-is-refused
  (is (= :open-interest-cap
         (api/validate (capped-ex)
                       {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 500}))))

(deftest the-cap-counts-what-is-already-open
  (let [ex (-> (capped-ex)
               (st/apply-tx {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 150})
               (st/apply-tx {:tx :order :account 2 :market 1 :side 0 :level 1001 :qty 150}))]
    (is (= 150 (cl/open-interest (:clearing ex) 1)))
    (is (= :open-interest-cap
           (api/validate ex {:tx :order :account 1 :market 1 :side 1 :level 1002 :qty 60}))
        "150 open + 60 would pass 200")
    (is (nil? (api/validate ex {:tx :order :account 1 :market 1 :side 1 :level 1002 :qty 40})))))

(deftest a-market-without-a-cap-is-unconstrained
  (is (nil? (api/validate (funded)
                          {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 99999}))))

(deftest the-cap-blocks-a-block-rather-than-halting-it
  (let [ex (st/apply-block (capped-ex)
                           {:height 1 :ts 1
                            :txs [{:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 500}
                                  {:tx :order :account 1 :market 1 :side 1 :level 1001 :qty 50}]})]
    (is (= [:open-interest-cap] (mapv :reason (:rejected ex))))
    (is (= 50 (bk/level-qty (get-in ex [:books 1]) bk/ask 1001))
        "the second order still executed")))

;; ── where collateral comes from ─────────────────────────────────────────────

(defn- bridged
  "An exchange whose only source of collateral is account 900."
  []
  (st/new-exchange {:market (cl/market {:id 1 :max-leverage 20 :tick 10 :lot 1})
                    :book-opts {:n-levels 4096 :cap 4096 :ev-cap 4096}
                    :bridge-authority 900}))

(deftest without-a-bridge-any-account-credits-itself
  (testing "the default, and the reason the default has to be stated: every
            margin, liquidation and ADL number is exact arithmetic over
            collateral that was conjured"
    (is (nil? (api/validate (fresh) {:tx :deposit :account 7 :amount 1000})))))

(deftest with-a-bridge-only-the-bridge-credits
  (let [ex (bridged)]
    (is (nil? (api/validate ex {:tx :deposit :account 900 :amount 1000})))
    (is (= :not-the-bridge (api/validate ex {:tx :deposit :account 7 :amount 1000}))
        "an account that can credit itself is not depositing, it is minting")))

(deftest the-bridge-does-not-get-to-skip-the-shape-checks
  (let [ex (bridged)]
    (is (= :bad-amount (api/validate ex {:tx :deposit :account 900 :amount 0})))
    (is (= :bad-amount (api/validate ex {:tx :deposit :account 900 :amount -5})))))

(deftest a-holder-still-withdraws-without-asking-the-bridge
  (testing "the signature is the whole authority for moving your own
            collateral out, and the clearinghouse already refuses to take more
            than is free"
    (let [ex (bridged)]
      (is (nil? (api/validate ex {:tx :withdraw :account 7 :amount 1000})))
      (is (= :bad-amount (api/validate ex {:tx :withdraw :account 7 :amount 0}))))))

(deftest a-refused-deposit-credits-nothing
  (testing "asserted through apply-block, because validate returning a keyword
            and the balance moving anyway is exactly the failure a shape test
            cannot see"
    (let [ex (st/apply-block (bridged)
                             {:height 1 :ts 1000
                              :txs [{:tx :deposit :account 7 :amount 5000}
                                    {:tx :deposit :account 900 :amount 5000}]})]
      (is (= [:not-the-bridge] (mapv :reason (:rejected ex))))
      (is (= 0 (get-in ex [:clearing :accounts 7 :collateral] 0)))
      (is (= 5000 (get-in ex [:clearing :accounts 900 :collateral] 0))))))

(deftest account-state-says-which-nonce-is-next-and-who-owns-the-id
  (testing "a client that tracks its own nonce drifts the moment a request is
            lost in flight, and one that cannot see the binding discovers it as
            a rejection after signing"
    (let [ex (fresh)]
      (is (= 1 (:next-nonce (api/account-state ex 7))))
      (is (nil? (:bound-key (api/account-state ex 7))))
      (let [ex (auth/accept ex {:account 7 :nonce 1 :pubkey "pk-7"})]
        (is (= 2 (:next-nonce (api/account-state ex 7))))
        (is (= "pk-7" (:bound-key (api/account-state ex 7))))
        (is (nil? (:bound-key (api/account-state ex 8))) "a different id is free")))))

;; ── the bridge pays somebody other than itself ──────────────────────────────

(deftest a-bridge-may-credit-another-account
  (let [ex (assoc (fresh) :bridge-authority 7)]
    (is (nil? (api/validate ex {:tx :deposit :account 7 :credit 42 :amount 100})))))

(deftest only-the-bridge-may-deposit-when-one-is-configured
  (let [ex (assoc (fresh) :bridge-authority 7)]
    (is (= :not-the-bridge
           (api/validate ex {:tx :deposit :account 9 :credit 9 :amount 100})))
    (is (= :not-the-bridge
           (api/validate ex {:tx :deposit :account 9 :credit 42 :amount 100})))))

(deftest without-a-bridge-an-account-may-only-pay-itself
  ;; Not a safety property on its own — with no authority anyone can mint
  ;; anyway — but crediting a stranger is a way to obscure where collateral
  ;; came from on a chain where the mint is meant to be the only source.
  (let [ex (fresh)]
    (is (nil? (api/validate ex {:tx :deposit :account 9 :amount 100})))
    (is (nil? (api/validate ex {:tx :deposit :account 9 :credit 9 :amount 100})))
    (is (= :not-the-bridge
           (api/validate ex {:tx :deposit :account 9 :credit 42 :amount 100})))))

(deftest a-credit-that-is-not-an-account-is-refused
  (let [ex (assoc (fresh) :bridge-authority 7)]
    (is (= :bad-account
           (api/validate ex {:tx :deposit :account 7 :credit "42" :amount 100})))))

(deftest order-id-zero-is-a-real-order
  ;; `bk/oid-of` is `slot * gen-mod + generation`, so the first order to occupy
  ;; slot 0 has id 0. `validate` required `pos?`, which made that order
  ;; uncancellable by anybody — seen on the deployed chain, where `/orders`
  ;; showed a resting order with `"oid":0`.
  (let [ex (st/new-exchange {:market (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                             :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})]
    (is (nil? (api/validate ex {:tx :cancel :account 1 :market 1 :oid 0}))
        "order id 0 must be accepted")
    (is (= :missing-field (api/validate ex {:tx :cancel :account 1 :market 1 :oid -1}))
        "and a negative id must still be refused")))
