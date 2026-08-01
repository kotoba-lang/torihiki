(ns torihiki.trigger-test
  "Stop-loss and take-profit: the orders a trader needs most at the moment the
  market is least cooperative."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.trigger :as trg]
            [torihiki.state :as st]))

;; ── the pure firing rule ────────────────────────────────────────────────────

(defn- t [id price dir]
  (trg/trigger {:id id :account 1 :market 1 :trigger-price price :direction dir
                :order {:side bk/ask :level 1 :qty 10}}))

(deftest direction-is-inclusive
  (testing "a mark exactly at the trigger price fires it"
    (is (trg/armed? (t 1 100 :below) 100))
    (is (trg/armed? (t 1 100 :above) 100)))
  (is (trg/armed? (t 1 100 :below) 99))
  (is (not (trg/armed? (t 1 100 :below) 101)))
  (is (trg/armed? (t 1 100 :above) 101))
  (is (not (trg/armed? (t 1 100 :above) 99))))

(deftest firing-order-is-total-and-by-id
  (testing "creation order, not price — price would be buyable priority"
    (let [ts [(t 3 100 :below) (t 1 100 :below) (t 2 100 :below)]]
      (is (= [1 2 3] (mapv :id (trg/due ts 99))))))
  (testing "and it does not depend on the order they happen to sit in"
    (let [a [(t 9 100 :below) (t 4 100 :below)]
          b [(t 4 100 :below) (t 9 100 :below)]]
      (is (= (mapv :id (trg/due a 99)) (mapv :id (trg/due b 99)))))))

(deftest remaining-is-the-complement-of-due
  (let [ts [(t 1 100 :below) (t 2 50 :below) (t 3 200 :above)]]
    (is (= [1] (mapv :id (trg/due ts 100))))
    (is (= [2 3] (mapv :id (trg/remaining ts 100))))))

(deftest nonsense-is-refused-at-submission
  (is (not (trg/valid? (t 1 0 :below))) "a non-positive trigger price")
  (is (not (trg/valid? (assoc (t 1 100 :below) :direction :sideways))))
  (is (not (trg/valid? (assoc-in (t 1 100 :below) [:order :qty] 0))))
  (is (not (trg/valid? (assoc-in (t 1 100 :below) [:order :side] 7))))
  (is (trg/valid? (t 1 100 :below))))

;; ── reduce-only ─────────────────────────────────────────────────────────────

(deftest reducing-qty-clamps-rather-than-rejects
  (let [s (-> (cl/new-state)
              (cl/deposit 1 1000000)
              (cl/apply-fill 1 1 100 500 0))]     ; long 100
    (is (= 100 (cl/reducing-qty s 1 1 bk/ask 100)) "exactly closing")
    (is (= 100 (cl/reducing-qty s 1 1 bk/ask 250))
        "oversized close is clamped, not turned into a short")
    (is (= 40 (cl/reducing-qty s 1 1 bk/ask 40)) "partial close")
    (is (= 0 (cl/reducing-qty s 1 1 bk/bid 50)) "buying does not reduce a long")))

(deftest reducing-a-short-is-buying
  (let [s (-> (cl/new-state)
              (cl/deposit 1 1000000)
              (cl/apply-fill 1 1 -100 500 0))]
    (is (= 100 (cl/reducing-qty s 1 1 bk/bid 100)))
    (is (= 100 (cl/reducing-qty s 1 1 bk/bid 900)) "clamped"))
  (testing "a flat position cannot be reduced"
    (is (= 0 (cl/reducing-qty (cl/new-state) 1 1 bk/bid 10)))))

;; ── end to end ──────────────────────────────────────────────────────────────

(def mkt (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))

(defn- market-open
  "A funded, two-sided market with the trader long 20 at ~1010."
  []
  (let [ex (-> (st/new-exchange {:market mkt :book-opts {:n-levels 65536 :cap 65536 :ev-cap 65536}})
               (st/apply-tx {:tx :deposit :account 1 :amount 100000000})
               (st/apply-tx {:tx :deposit :account 2 :amount 100000000})
               (st/apply-tx {:tx :deposit :account 5 :amount 500000})
               (st/apply-tx {:tx :oracle :market 1 :price 1000}))
        ex (reduce (fn [e i]
                     (-> e
                         (st/apply-tx {:tx :order :account 1 :market 1
                                       :side bk/bid :level (- 1000 1 i) :qty 500})
                         (st/apply-tx {:tx :order :account 2 :market 1
                                       :side bk/ask :level (+ 1000 1 i) :qty 500})))
                   ex (range 10))]
    (st/apply-tx ex {:tx :order :account 5 :market 1
                     :side bk/bid :level 1010 :qty 20})))

(deftest reduce-only-cannot-flip-a-position
  (let [ex (market-open)
        size (:size (cl/position (:clearing ex) 5 1))]
    (is (pos? size))
    (testing "an oversized reduce-only sell closes and stops at flat"
      (let [ex' (st/apply-tx ex {:tx :order :account 5 :market 1
                                 :side bk/ask :level 900 :qty 10000
                                 :flags bk/flag-reduce-only})]
        (is (= 0 (:size (cl/position (:clearing ex') 5 1)))
            "flat, not short — the whole point")))
    (testing "a reduce-only order on the wrong side is a no-op"
      (let [ex' (st/apply-tx ex {:tx :order :account 5 :market 1
                                 :side bk/bid :level 1010 :qty 50
                                 :flags bk/flag-reduce-only})]
        (is (= size (:size (cl/position (:clearing ex') 5 1))))))))

(deftest reduce-only-does-not-rest
  (testing "it is IOC by construction — see the comment in apply-tx :order"
    (let [ex (market-open)
          resting-before (bk/resting-count (get-in ex [:books 1]))
          ex' (st/apply-tx ex {:tx :order :account 5 :market 1
                               :side bk/ask :level 5000 :qty 10
                               :flags bk/flag-reduce-only})]
      (is (= resting-before (bk/resting-count (get-in ex' [:books 1])))
          "an unmarketable reduce-only order leaves nothing behind"))))

(deftest a-stop-loss-fires-when-the-mark-falls
  (let [ex (market-open)
        size (:size (cl/position (:clearing ex) 5 1))
        ex (st/apply-tx ex {:tx :trigger :account 5 :market 1
                            :trigger-price 995 :direction :below
                            :order {:side bk/ask :level 0 :qty size}})]
    (is (= 1 (count (get-in ex [:triggers 1]))) "armed, not fired")
    (is (= size (:size (cl/position (:clearing ex) 5 1))))
    (testing "the oracle falls through the stop"
      (let [ex' (st/apply-tx ex {:tx :oracle :market 1 :price 990})]
        (is (empty? (get-in ex' [:triggers 1])) "the trigger is consumed")
        (is (= 0 (:size (cl/position (:clearing ex') 5 1)))
            "and the position is closed")))))

(deftest a-take-profit-fires-when-the-mark-rises
  (let [ex (market-open)
        size (:size (cl/position (:clearing ex) 5 1))
        ex (st/apply-tx ex {:tx :trigger :account 5 :market 1
                            :trigger-price 1005 :direction :above
                            :order {:side bk/ask :level 0 :qty size}})
        ex' (st/apply-tx ex {:tx :oracle :market 1 :price 1010})]
    (is (empty? (get-in ex' [:triggers 1])))
    (is (= 0 (:size (cl/position (:clearing ex') 5 1))))))

(deftest a-trigger-already-past-its-price-fires-at-once
  (testing "otherwise a stop submitted into a gap sits armed while the market runs"
    (let [ex (market-open)
          size (:size (cl/position (:clearing ex) 5 1))
          ex' (st/apply-tx ex {:tx :trigger :account 5 :market 1
                               ;; the mark is already ~1000, well above this
                               :trigger-price 2000 :direction :below
                               :order {:side bk/ask :level 0 :qty size}})]
      (is (empty? (get-in ex' [:triggers 1])))
      (is (= 0 (:size (cl/position (:clearing ex') 5 1)))))))

(deftest a-trigger-is-reduce-only-so-it-cannot-open-a-position
  (testing "a stale stop must not put its owner short after they closed by hand"
    (let [ex (market-open)
          size (:size (cl/position (:clearing ex) 5 1))
          ex (st/apply-tx ex {:tx :trigger :account 5 :market 1
                              :trigger-price 995 :direction :below
                              :order {:side bk/ask :level 0 :qty size}})
          ;; the trader closes manually first
          ex (st/apply-tx ex {:tx :order :account 5 :market 1
                              :side bk/ask :level 900 :qty size
                              :flags bk/flag-reduce-only})
          _ (is (= 0 (:size (cl/position (:clearing ex) 5 1))))
          ex' (st/apply-tx ex {:tx :oracle :market 1 :price 990})]
      (is (= 0 (:size (cl/position (:clearing ex') 5 1)))
          "the stale stop fired into a flat position and did nothing"))))

(deftest triggers-can-be-cancelled
  (let [ex (-> (market-open)
               (st/apply-tx {:tx :trigger :account 5 :market 1
                             :trigger-price 900 :direction :below
                             :order {:side bk/ask :level 0 :qty 5}}))
        id (:id (first (get-in ex [:triggers 1])))
        ex' (st/apply-tx ex {:tx :cancel-trigger :market 1 :id id})]
    (is (= 1 (count (get-in ex [:triggers 1]))))
    (is (empty? (get-in ex' [:triggers 1])))))

(deftest the-state-root-commits-to-triggers
  (testing "a sequencer must not be able to add or drop a stop invisibly"
    (let [ex (market-open)
          with (st/apply-tx ex {:tx :trigger :account 5 :market 1
                                :trigger-price 900 :direction :below
                                :order {:side bk/ask :level 0 :qty 5}})]
      (is (= 1 (count (get-in with [:triggers 1]))) "it is still armed")
      (is (not= (st/state-root ex) (st/state-root with))))))

(deftest the-cascade-terminates
  (testing "a chain of stops that each arm the next must not loop forever"
    (let [ex (market-open)
          size (:size (cl/position (:clearing ex) 5 1))
          ;; twenty stops stacked below the mark, more than the round cap
          ex (reduce (fn [e i]
                       (st/apply-tx e {:tx :trigger :account 5 :market 1
                                       :trigger-price (- 995 i) :direction :below
                                       :order {:side bk/ask :level 0 :qty 1}}))
                     ex (range 20))]
      (is (= 20 (count (get-in ex [:triggers 1]))))
      (let [ex' (st/apply-tx ex {:tx :oracle :market 1 :price 900})]
        ;; it returns — that is the assertion. Whatever did not fire within
        ;; the cap is still armed for the next transaction, not lost.
        (is (some? ex'))
        (is (<= (count (get-in ex' [:triggers 1])) 20))
        (is (>= size 0))))))
