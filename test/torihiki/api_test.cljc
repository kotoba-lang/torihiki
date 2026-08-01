(ns torihiki.api-test
  "The property that matters most here is not that bad input is refused — it
  is that refusing it cannot stop the chain."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.api :as api]
            [torihiki.state :as st]))

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
