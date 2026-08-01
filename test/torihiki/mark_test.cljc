(ns torihiki.mark-test
  "The attack this namespace exists to stop: move the mark with one small
  fill, then collect somebody else's liquidated position."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.fixed :as fx]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.mark :as mk]
            [torihiki.state :as st]))

(def params mk/default-params)

(defn- book-with
  "A two-sided book around `mid` with `size` lots at each of ten levels."
  [mid size]
  (let [b (bk/new-book {:n-levels 65536 :cap 8192 :ev-cap 8192})]
    (dotimes [i 10]
      (bk/place! b bk/bid (- mid 1 i) size 0 1)
      (bk/place! b bk/ask (+ mid 1 i) size 0 2))
    b))

(deftest a-thin-book-does-not-get-to-price-itself
  (testing "when neither side can absorb the reference size, the mark is the oracle"
    (let [b (bk/new-book {:n-levels 65536 :cap 128 :ev-cap 128})]
      (bk/place! b bk/bid 999 1 0 1)
      (bk/place! b bk/ask 1001 1 0 2)
      (is (nil? (mk/impact-mid b (:impact-notional params))))
      (is (= 1000 (mk/mark-price b 1000 params))))))

(deftest one-sided-book-falls-back-to-the-oracle
  (let [b (bk/new-book {:n-levels 65536 :cap 8192 :ev-cap 8192})]
    (dotimes [i 10] (bk/place! b bk/bid (- 1000 1 i) 500 0 1))
    (is (nil? (mk/impact-mid b (:impact-notional params))))
    (is (= 1000 (mk/mark-price b 1000 params)))))

(deftest a-deep-balanced-book-marks-at-the-oracle
  (let [b (book-with 1000 500)
        m (mk/mark-price b 1000 params)]
    (is (< (fx/abs* (- m 1000)) 3)
        (str "a balanced book should not push the mark off the oracle; got " m))))

(deftest the-band-bounds-a-successful-manipulation
  (testing "even real size cannot drag the mark past the band"
    ;; a book quoting far above the oracle, with genuine depth behind it
    (let [b (book-with 2000 5000)
          oracle 1000
          m (mk/mark-price b oracle params)
          limit (fx/mul-rate oracle (:band params))]
      (is (pos? limit))
      (is (= (+ oracle limit) m)
          "the mark is pinned at oracle + band, not at the book's price")
      (is (< m 1010) "50 bp of 1000 is 5 ticks, so the mark stays near 1000"))
    (testing "and symmetrically below"
      (let [b (book-with 500 5000)
            oracle 1000
            m (mk/mark-price b oracle params)
            limit (fx/mul-rate oracle (:band params))]
        (is (= (- oracle limit) m))))))

(deftest a-genuine-premium-inside-the-band-is-respected
  (testing "the mark is not just the oracle — a real, size-backed premium shows"
    (let [b (book-with 1003 5000)
          m (mk/mark-price b 1000 params)]
      (is (> m 1000) "a book bid up on real size should mark above the oracle")
      (is (<= m 1005) "but still inside the 50 bp band"))))

;; ── the attack, end to end ──────────────────────────────────────────────────

(def mkt (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))

(defn- exchange []
  (st/new-exchange {:market mkt :book-opts {:n-levels 65536 :cap 65536 :ev-cap 65536}}))

(deftest one-thin-fill-cannot-move-the-mark
  (testing "the whole reason this namespace exists"
    (let [ex (-> (exchange)
                 (st/apply-tx {:tx :deposit :account 1 :amount 100000000})
                 (st/apply-tx {:tx :deposit :account 2 :amount 100000000})
                 (st/apply-tx {:tx :deposit :account 9 :amount 100000000})
                 (st/apply-tx {:tx :oracle :market 1 :price 1000}))
          ;; a real two-sided market
          ex (reduce (fn [e i]
                       (-> e
                           (st/apply-tx {:tx :order :account 1 :market 1
                                         :side bk/bid :level (- 1000 1 i) :qty 500})
                           (st/apply-tx {:tx :order :account 2 :market 1
                                         :side bk/ask :level (+ 1000 1 i) :qty 500})))
                     ex (range 10))
          mark-before (get-in ex [:marks 1])
          ;; The attacker cannot simply print at 1200 while cheaper asks rest
          ;; below — a bid at 1200 crosses those first. So: clear the ask side
          ;; (which costs real money, as it should), then park one lot high and
          ;; lift it. That IS the cheap-print attack, executed properly.
          ex' (-> ex
                  (st/apply-tx {:tx :order :account 9 :market 1
                                :side bk/bid :level 1010 :qty 5000})
                  (st/apply-tx {:tx :order :account 9 :market 1
                                :side bk/ask :level 1200 :qty 1})
                  (st/apply-tx {:tx :order :account 9 :market 1
                                :side bk/bid :level 1200 :qty 1}))
          mark-after (get-in ex' [:marks 1])]
      (is (= 1200 (get-in ex' [:last 1]))
          "the print really did happen at 1200 — this is not a no-op")
      ;; the ask side is now gone, so the book cannot price the reference size
      ;; and the mark falls back to the oracle rather than following the print
      (is (< (fx/abs* (- mark-after 1000)) 6)
          (str "the mark must stay anchored to the oracle; got " mark-after
               " (was " mark-before ", last print 1200)"))
      (is (not= 1200 mark-after) "the entire point"))))

(deftest the-mark-not-the-last-print-decides-liquidation
  (testing "a manipulated print must not put a healthy account under water"
    (let [ex (-> (exchange)
                 (st/apply-tx {:tx :deposit :account 1 :amount 100000000})
                 (st/apply-tx {:tx :deposit :account 2 :amount 100000000})
                 (st/apply-tx {:tx :deposit :account 5 :amount 3000})
                 (st/apply-tx {:tx :oracle :market 1 :price 1000}))
          ex (reduce (fn [e i]
                       (-> e
                           (st/apply-tx {:tx :order :account 1 :market 1
                                         :side bk/bid :level (- 1000 1 i) :qty 500})
                           (st/apply-tx {:tx :order :account 2 :market 1
                                         :side bk/ask :level (+ 1000 1 i) :qty 500})))
                     ex (range 10))
          ;; account 5 opens a modest long against the resting asks
          ex (st/apply-tx ex {:tx :order :account 5 :market 1
                              :side bk/bid :level 1010 :qty 20})
          marks (:marks ex)]
      (is (pos? (:size (cl/position (:clearing ex) 5 1))) "the long is open")
      (is (not (cl/liquidatable? (:clearing ex) 5 marks {1 mkt}))
          "and healthy at the honest mark")
      ;; now somebody sweeps the bids down and prints far below
      (let [ex' (-> ex
                    (st/apply-tx {:tx :deposit :account 9 :amount 100000000})
                    (st/apply-tx {:tx :order :account 9 :market 1
                                  :side bk/ask :level 700 :qty 6000})
                    (st/apply-tx {:tx :order :account 9 :market 1
                                  :side bk/bid :level 700 :qty 1}))
            ex' (st/apply-tx ex' {:tx :order :account 9 :market 1
                                  :side bk/ask :level 700 :qty 1})]
        (is (not (cl/liquidatable? (:clearing ex') 5 (:marks ex') {1 mkt}))
            "the print must not be able to liquidate account 5")))))

(deftest liquidation-refuses-to-run-without-a-mark
  (testing "no oracle, no book: the engine stops rather than guessing"
    (let [ex (-> (exchange)
                 (st/apply-tx {:tx :deposit :account 1 :amount 1000}))]
      (is (thrown? #?(:clj Exception :cljs :default)
                   (st/apply-tx ex {:tx :liquidate :market 1}))))))
