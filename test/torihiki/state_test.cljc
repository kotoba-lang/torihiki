(ns torihiki.state-test
  "The tests that matter for a chain: two replays of the same block must reach
  the same state root, and any difference in the block must change it."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.fixed :as fx]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.liquidation :as liq]
            [torihiki.state :as st]))

(def mkt (assoc (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                :taker-fee-rate (fx/bps 3)
                :maker-fee-rate 0))

(defn- fresh []
  (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 65536 :ev-cap 65536}}))

(defn- funded [ex accts amount]
  (reduce (fn [e a] (st/apply-tx e {:tx :deposit :account a :amount amount})) ex accts))

(defn- tape
  "A deterministic block: deposits, two-sided quoting, and takers that cross."
  [n]
  (vec
   (for [i (range n)]
     (let [side (mod i 2)]
       (if (zero? (mod i 7))
         {:tx :order :account (+ 10 (mod i 5)) :market 1
          :side side :level (if (zero? side) 2050 1950) :qty (inc (mod i 4))}
         {:tx :order :account (+ 10 (mod i 5)) :market 1
          :side side :level (if (zero? side) (- 2000 1 (mod i 20))
                                             (+ 2000 1 (mod i 20)))
          :qty (inc (mod i 6))})))))

(deftest replay-is-deterministic
  (testing "the same block applied twice from genesis yields the same root"
    (let [block {:height 1 :ts 1000 :txs (tape 3000)}
          run #(-> (fresh) (funded (range 10 15) 100000000) (st/apply-block block))
          a (run) c (run)]
      (is (= (st/state-root a) (st/state-root c)))
      (is (pos? (bk/event-count (get-in a [:books 1])))
          "the tape must actually trade, or this proves nothing"))))

(deftest the-root-notices-a-changed-block
  (let [base (tape 500)
        run (fn [txs] (-> (fresh) (funded (range 10 15) 100000000)
                          (st/apply-block {:height 1 :ts 1000 :txs txs})))
        r0 (st/state-root (run base))]
    (testing "one extra lot on one order changes the root"
      (is (not= r0 (st/state-root (run (update-in base [7 :qty] inc))))))
    (testing "reordering two crossing orders changes the root"
      (let [swapped (assoc base 0 (nth base 1) 1 (nth base 0))]
        (is (not= r0 (st/state-root (run swapped)))
            "order is the only thing consensus decides; it had better matter")))
    (testing "a different block timestamp changes the root"
      (is (not= r0 (st/state-root
                    (-> (fresh) (funded (range 10 15) 100000000)
                        (st/apply-block {:height 1 :ts 1001 :txs base}))))))))

(deftest fills-credit-both-sides
  (let [ex (-> (fresh)
               (funded [10 11] 10000000)
               (st/apply-block
                {:height 1 :ts 1
                 :txs [{:tx :order :account 10 :market 1 :side bk/ask :level 1000 :qty 5}
                       {:tx :order :account 11 :market 1 :side bk/bid :level 1000 :qty 5}]}))
        maker (cl/position (:clearing ex) 10 1)
        taker (cl/position (:clearing ex) 11 1)]
    (is (= -5 (:size maker)) "the resting seller is short")
    (is (= 5 (:size taker)) "the aggressor is long")
    (is (= 0 (+ (:size maker) (:size taker))) "positions net to zero")
    (is (= 1000 (get-in ex [:marks 1])) "the fill sets the mark")))

(deftest positions-always-net-to-zero
  (testing "a perp market cannot create net exposure out of nothing"
    (let [ex (-> (fresh) (funded (range 10 15) 100000000)
                 (st/apply-block {:height 1 :ts 1 :txs (tape 2000)}))
          total (reduce + 0 (for [[_ a] (get-in ex [:clearing :accounts])
                                  [_ p] (:positions a)]
                              (:size p)))]
      (is (= 0 total)))))

(deftest liquidation-waterfall-reaches-the-book-first
  (testing "when the book can absorb the position, no vault or fund is touched"
    (let [markets {1 mkt}
          ;; thin account, long position, mark dropped enough to breach
          s (-> (cl/new-state)
                (assoc :insurance-fund 1000 :backstop-vault st/vault-account
                       :liquidation-clock {})
                (cl/deposit 100 1000)
                (cl/apply-fill 100 1 10 500 0))
          marks {1 404}
          take-fn (fn [delta _mark] [delta 404])]   ; the book absorbs it at the mark
      (is (cl/liquidatable? s 100 marks markets))
      (let [r (liq/liquidate s 100 1 404 0 markets liq/default-params take-fn)]
        (is (= :book (:stage r)))
        (is (= 0 (:size (cl/position (:state r) 100 1))) "the position is closed")
        (is (> (:insurance-fund (:state r)) 1000) "the liquidation fee funds the insurance pool")))))

(deftest large-positions-liquidate-in-slices
  (testing "a position above the notional threshold closes 20% at a time"
    (let [params liq/default-params
          ;; 1000 lots at mark 500 = 500,000 notional, well over the 100,000 threshold
          slice (liq/slice-size 1000 500 params)]
      (is (= 200 slice) "20% of 1000")
      (is (= 10 (liq/slice-size 10 500 params)) "a small position closes in one shot")
      (is (= 1 (liq/slice-size 1000000 1 (assoc params :partial-fraction 0)))
          "a slice never rounds to zero, or the position could never be closed"))))

(deftest cooldown-uses-logical-time
  (let [params liq/default-params
        s {:liquidation-clock {[100 1] 1000}}]
    (is (liq/cooling-down? s 100 1 1010 params) "10 logical seconds into a 30s cooldown")
    (is (not (liq/cooling-down? s 100 1 1030 params)))
    (is (not (liq/cooling-down? s 999 1 1010 params)) "a different account is unaffected")))

(deftest adl-ranking-is-a-total-order
  (testing "equal scores are broken by account id, so two nodes rank identically"
    (let [s {:accounts {5 {:collateral 1000 :positions {1 {:size 10 :entry-notional 4000}}}
                        3 {:collateral 1000 :positions {1 {:size 10 :entry-notional 4000}}}
                        9 {:collateral 1000 :positions {1 {:size 10 :entry-notional 4000}}}}}
          ranked (liq/adl-ranking s 1 500 -1)]
      (is (= 3 (count ranked)) "all three are profitable longs facing a short")
      (is (= [3 5 9] (mapv :account ranked))
          "identical scores must still produce one canonical order")
      (is (apply = (map :score ranked))))))

(deftest adl-only-ranks-the-opposite-side
  (let [s {:accounts {1 {:collateral 1000 :positions {7 {:size 10 :entry-notional 4000}}}
                      2 {:collateral 1000 :positions {7 {:size -10 :entry-notional -6000}}}}}]
    (testing "closing a short can only be absorbed by longs"
      (is (= [1] (mapv :account (liq/adl-ranking s 7 500 -1)))))
    (testing "and closing a long only by shorts"
      (is (= [2] (mapv :account (liq/adl-ranking s 7 500 1)))))))
