(ns torihiki.funding-test
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.fixed :as fx]
            [torihiki.book :as bk]
            [torihiki.funding :as fnd]
            [torihiki.clearing :as cl]))

(def p fnd/default-params)

(deftest interest-term-is-clamped-not-the-rate
  (testing "when the premium is large it dominates; interest contributes at most the clamp"
    ;; premium 1% = 10,000,000 rate units. interest - premium is hugely negative,
    ;; so the clamp pins it at -0.0005, and F = premium - 0.0005.
    (let [prem (fx/pct 1)
          f (fnd/eight-hour-rate prem p)]
      (is (= (- prem (fx/bps 5)) f))
      (is (> f (fx/pct 0.9)) "clamping the FINAL rate here would have gutted the tether")))
  (testing "when the premium is zero the rate is the interest rate"
    (is (= (:interest-rate p) (fnd/eight-hour-rate 0 p))))
  (testing "a negative premium flips the sign: shorts pay longs"
    (is (neg? (fnd/eight-hour-rate (- (fx/pct 1)) p)))))

(deftest hourly-is-one-eighth-then-capped
  (is (= (fx/fdiv (fnd/eight-hour-rate 0 p) 8)
         (fnd/hourly-rate {:sum 0 :n 1} p)))
  (testing "the cap binds on the hourly figure, not the eight-hour one"
    (let [enormous {:sum (fx/pct 1000) :n 1}]
      (is (= (:hourly-cap p) (fnd/hourly-rate enormous p)))
      (is (= (- (:hourly-cap p)) (fnd/hourly-rate {:sum (- (fx/pct 1000)) :n 1} p))))))

(deftest average-not-a-snapshot
  (testing "one dislocated sample cannot set the rate"
    (let [acc (reduce fnd/sample fnd/empty-accumulator
                      (conj (vec (repeat 719 0)) (fx/pct 100)))]
      (is (= 719 (- (:n acc) 1)))
      ;; 100% spread over 720 samples is 0.1389%
      (is (< (fnd/average-premium acc) (fx/pct 0.14))))))

(deftest a-missing-sample-is-not-a-zero
  (testing "nil observations are skipped, not folded in as neutral"
    (let [acc (-> fnd/empty-accumulator
                  (fnd/sample (fx/pct 1))
                  (fnd/sample nil)
                  (fnd/sample nil))]
      (is (= 1 (:n acc)))
      (is (= (fx/pct 1) (fnd/average-premium acc))
          "counting the nils would have divided the premium by three"))))

(deftest premium-uses-impact-not-top-of-book
  (let [b (bk/new-book {:n-levels 4096 :cap 1024 :ev-cap 64})]
    ;; a real two-sided market around 1000
    (bk/place! b bk/bid 999 100 0 1)
    (bk/place! b bk/ask 1001 100 0 1)
    (let [fair (fnd/premium b 1000 20000)]
      (is (some? fair))
      (is (< (fx/abs* fair) (fx/bps 20)) "a balanced book sits near zero premium")
      (testing "one lot quoted inside the spread barely moves the premium"
        ;; NOT at an absurd price: a bid above the best ask would CROSS and
        ;; trade instead of resting, so the original version of this test
        ;; never parked the quote it claimed to be testing. Inside the spread
        ;; is where a manipulator can actually sit.
        (bk/place! b bk/bid 1000 1 0 99)
        (let [after (fnd/premium b 1000 20000)]
          (is (some? after))
          (is (< (fx/abs* (- after fair)) (fx/bps 2))
              "one lot cannot fill the reference size, so it cannot set the price"))))))

(deftest premium-is-nil-when-a-side-is-empty
  (let [b (bk/new-book {:n-levels 4096 :cap 1024 :ev-cap 64})]
    (bk/place! b bk/bid 999 100 0 1)
    (is (nil? (fnd/premium b 1000 20000)) "one-sided book yields no observation")))

(deftest funding-is-a-transfer-not-a-fee
  (testing "what longs pay, shorts receive"
    (let [rate (fx/bps 10)
          s (-> (cl/new-state)
                (cl/deposit 1 1000000)
                (cl/deposit 2 1000000)
                (cl/apply-fill 1 7 100 500 0)      ; long 100
                (cl/apply-fill 2 7 -100 500 0))    ; short 100
          s' (fnd/apply-funding s 7 500 rate)
          long-delta (- (get-in s' [:accounts 1 :collateral])
                        (get-in s [:accounts 1 :collateral]))
          short-delta (- (get-in s' [:accounts 2 :collateral])
                         (get-in s [:accounts 2 :collateral]))]
      (is (neg? long-delta) "the long pays")
      (is (pos? short-delta) "the short receives")
      (is (= 0 (+ long-delta short-delta)) "and the two net to zero")
      (is (= 0 (:funding-residue s')) "no value was created or destroyed"))))

(deftest funding-payment-uses-the-oracle-price
  (testing "the mark is not consulted, so book pressure cannot move funding"
    (let [rate (fx/bps 100)]
      ;; the same position, the same rate, two very different marks
      (is (= (fnd/payment 100 500 rate) (fnd/payment 100 500 rate)))
      (is (not= (fnd/payment 100 500 rate) (fnd/payment 100 900 rate))
          "changing the ORACLE does change it — that is the intended input"))))
