(ns torihiki.clearing-test
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.fixed :as fx]
            [torihiki.clearing :as cl]))

(def btc (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))
(def alt (cl/market {:id 2 :max-leverage 3 :tick 1 :lot 1}))
(def markets {1 btc 2 alt})

(deftest margin-rates-are-derived-not-configured
  (testing "Hyperliquid's rule: maintenance is half the initial at max leverage"
    (is (= (fx/pct 2.5) (double (:initial-margin-rate btc))) "40x -> 2.5% initial")
    (is (= (:maintenance-margin-rate btc)
           (fx/fdiv (:initial-margin-rate btc) 2)) "1.25% maintenance")
    (is (= 333333333 (:initial-margin-rate alt)) "3x -> 33.3% initial")
    (is (= 166666666 (:maintenance-margin-rate alt)))))

(deftest open-and-mark
  (let [s (-> (cl/new-state)
              (cl/deposit 100 1000000)
              (cl/apply-fill 100 1 10 500 0))]
    (is (= 10 (:size (cl/position s 100 1))))
    (is (= 500 (cl/entry-price (cl/position s 100 1))))
    (is (= 0 (cl/unrealized (cl/position s 100 1) 500)))
    (is (= 1000 (cl/unrealized (cl/position s 100 1) 600)) "10 lots x 100 ticks")
    (is (= -500 (cl/unrealized (cl/position s 100 1) 450)))))

(deftest reducing-leaves-no-phantom-basis
  (testing "a position returned to flat must carry zero cost basis"
    (let [s (-> (cl/new-state)
                (cl/deposit 100 1000000)
                (cl/apply-fill 100 1 10 500 0)    ; long 10 @ 500
                (cl/apply-fill 100 1 -4 600 0)    ; sell 4 @ 600
                (cl/apply-fill 100 1 -6 550 0))]  ; sell the rest @ 550
      (is (= 0 (:size (cl/position s 100 1))))
      (is (= 0 (:entry-notional (cl/position s 100 1)))
          "pro-rating the stored notional is what keeps this exactly zero")
      ;; realised: 4*(600-500) + 6*(550-500) = 400 + 300 = 700
      (is (= (+ 1000000 700) (get-in s [:accounts 100 :collateral]))))))

(deftest flipping-through-zero
  (testing "a fill that crosses zero closes the old side and reopens at the fill price"
    (let [s (-> (cl/new-state)
                (cl/deposit 100 1000000)
                (cl/apply-fill 100 1 10 500 0)     ; long 10 @ 500
                (cl/apply-fill 100 1 -25 600 0))]  ; sell 25 @ 600 -> short 15
      (is (= -15 (:size (cl/position s 100 1))))
      (is (= 600 (cl/entry-price (cl/position s 100 1)))
          "the new short's basis is the fill price, not a blend with the old long")
      ;; realised on the closed long: 10 * (600-500) = 1000
      (is (= (+ 1000000 1000) (get-in s [:accounts 100 :collateral])))
      (is (= 0 (cl/unrealized (cl/position s 100 1) 600))))))

(deftest short-pnl-signs
  (let [s (-> (cl/new-state)
              (cl/deposit 100 1000000)
              (cl/apply-fill 100 1 -10 500 0))]
    (is (= -10 (:size (cl/position s 100 1))))
    (is (= 1000 (cl/unrealized (cl/position s 100 1) 400)) "a short profits as price falls")
    (is (= -1000 (cl/unrealized (cl/position s 100 1) 600)))))

(deftest fees-leave-the-account-and-are-accounted
  (let [s (-> (cl/new-state)
              (cl/deposit 100 1000000)
              (cl/apply-fill 100 1 10 500 (fx/bps 5)))]   ; 5 bp on 5000 notional
    (is (= 2 (:fees-collected s)) "5bp of 5000 = 2.5, floored to 2")
    (is (= (- 1000000 2) (get-in s [:accounts 100 :collateral])))))

(deftest liquidatable-at-the-right-boundary
  (let [marks {1 500}
        s (-> (cl/new-state)
              (cl/deposit 100 1000)
              (cl/apply-fill 100 1 10 500 0))]   ; 5000 notional, mm 1.25% = 62
    (is (not (cl/liquidatable? s 100 marks markets)) "1000 equity vs 62 required")
    ;; equity = 1000 + 10*(m-500) = 10m-4000; maintenance = floor(10m * 1.25%)
    ;; the two cross just above 405, so 406 must hold and 404 must not
    (testing "just above the boundary the account survives"
      (let [m {1 406}]
        (is (= 60 (cl/equity s 100 m)))
        (is (= 50 (cl/maintenance-margin s 100 m markets)))
        (is (not (cl/liquidatable? s 100 m markets)))))
    (testing "just below it the account is liquidatable"
      (let [m {1 404}]
        (is (= 40 (cl/equity s 100 m)))
        (is (= 50 (cl/maintenance-margin s 100 m markets)))
        (is (cl/liquidatable? s 100 m markets))))))

(deftest isolated-losses-cannot-reach-the-cross-pool
  (let [marks {1 500 2 500}
        s (-> (cl/new-state)
              (cl/deposit 100 100000)
              (cl/apply-fill 100 1 10 500 0)
              ;; 50,000 notional at 3x needs 8,333 of maintenance margin, so the
              ;; fenced amount has to clear that or the position is born
              ;; liquidatable — which is what a first draft of this test did
              (assoc-in [:accounts 100 :positions 2]
                        {:size 100 :entry-notional 50000 :isolated 20000}))]
    (testing "the isolated position is excluded from cross equity"
      (is (= 100000 (cl/equity s 100 {1 500 2 100}))
          "a catastrophic mark on the isolated market must not move cross equity"))
    (testing "but it is liquidatable on its own margin"
      (is (cl/isolated-liquidatable? s 100 2 {1 500 2 100} markets))
      (is (not (cl/isolated-liquidatable? s 100 2 marks markets))))))

(deftest withdrawals-respect-initial-margin
  (let [marks {1 500}
        s (-> (cl/new-state)
              (cl/deposit 100 10000)
              (cl/apply-fill 100 1 10 500 0))]   ; 5000 notional, im 2.5% = 125
    (is (= (- 10000 125) (cl/free-collateral s 100 marks markets)))
    (is (= 10000 (get-in (cl/withdraw s 100 10000 marks markets)
                         [:accounts 100 :collateral]))
        "an over-withdrawal is refused, leaving the state untouched")
    (is (= 125 (get-in (cl/withdraw s 100 9875 marks markets)
                       [:accounts 100 :collateral])))))
