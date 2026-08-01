(ns torihiki.book-test
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.book :as bk]
            [torihiki.slab :as slab]))

(defn- fresh [] (bk/new-book {:n-levels 1024 :cap 4096 :ev-cap 4096}))

(deftest empty-book
  (let [b (fresh)]
    (is (= -1 (bk/best b bk/bid)))
    (is (= -1 (bk/best b bk/ask)))
    (is (= 0 (bk/resting-count b)))))

(deftest rest-and-find-best
  (testing "the bit ladder reports the top of book, not merely some occupied level"
    (let [b (fresh)]
      (bk/place! b bk/bid 100 10 0 1)
      (bk/place! b bk/bid 105 10 0 1)
      (bk/place! b bk/bid 99 10 0 1)
      (bk/place! b bk/ask 200 10 0 2)
      (bk/place! b bk/ask 150 10 0 2)
      (is (= 105 (bk/best b bk/bid)) "best bid is the highest")
      (is (= 150 (bk/best b bk/ask)) "best ask is the lowest")
      (is (= 3 (- (bk/resting-count b) 2))))))

(deftest ladder-cascade
  (testing "clearing the last order in a word clears its parent bits too"
    (let [b (fresh)]
      ;; two levels far enough apart to sit in different level-0 words
      (let [o1 (bk/place! b bk/ask 40 5 0 1)
            _  (bk/place! b bk/ask 900 5 0 1)]
        (is (= 40 (bk/best b bk/ask)))
        (bk/cancel! b o1)
        (is (= 900 (bk/best b bk/ask))
            "after the near level empties, best ask must jump to the far one")))))

(deftest price-time-priority
  (testing "same price fills in arrival order"
    (let [b (fresh)]
      (bk/place! b bk/ask 100 5 0 11)   ; maker A, first
      (bk/place! b bk/ask 100 5 0 22)   ; maker B, second
      (bk/place! b bk/bid 100 7 0 33)   ; taker sweeps 7
      (let [f (bk/fills b)]
        (is (= 2 (count f)))
        (is (= 11 (:maker-owner (first f))) "the earlier maker fills first")
        (is (= 5 (:qty (first f))))
        (is (= 22 (:maker-owner (second f))))
        (is (= 2 (:qty (second f))) "the later maker fills only the remainder")))))

(deftest price-priority-across-levels
  (testing "a taker sweeps the best level before a worse one"
    (let [b (fresh)]
      (bk/place! b bk/ask 102 5 0 1)
      (bk/place! b bk/ask 100 5 0 2)
      (bk/place! b bk/ask 101 5 0 3)
      (bk/place! b bk/bid 102 15 0 9)
      (is (= [100 101 102] (mapv :level (bk/fills b)))))))

(deftest partial-fill-rests-remainder
  (let [b (fresh)]
    (bk/place! b bk/ask 100 3 0 1)
    (let [oid (bk/place! b bk/bid 100 10 0 2)]
      (is (pos? oid) "the unfilled remainder rests")
      (is (= 100 (bk/best b bk/bid)))
      (is (= 7 (bk/level-qty b bk/bid 100)))
      (is (= -1 (bk/best b bk/ask)) "the ask side is now empty"))))

(deftest ioc-does-not-rest
  (let [b (fresh)]
    (bk/place! b bk/ask 100 3 0 1)
    (is (= -1 (bk/place! b bk/bid 100 10 bk/flag-ioc 2)))
    (is (= -1 (bk/best b bk/bid)) "the IOC remainder was cancelled, not rested")
    (is (= 1 (count (bk/fills b))) "but the part that could fill did")))

(deftest post-only-rejects-a-taker
  (let [b (fresh)]
    (bk/place! b bk/ask 100 3 0 1)
    (is (= -1 (bk/place! b bk/bid 100 5 bk/flag-post-only 2))
        "post-only that would cross is rejected outright")
    (is (= 0 (count (bk/fills b))))
    (is (pos? (bk/place! b bk/bid 99 5 bk/flag-post-only 2))
        "post-only that would not cross rests normally")))

(deftest cancel-frees-the-slot
  (let [b (fresh)
        oid (bk/place! b bk/bid 100 10 0 1)]
    (is (= 10 (bk/cancel! b oid)))
    (is (= -1 (bk/best b bk/bid)))
    (is (= 0 (bk/cancel! b oid)) "cancelling twice is a no-op, not a crash")))

(deftest stale-id-cannot-cancel-a-stranger
  (testing "the generation counter is what makes slot reuse safe"
    (let [b (fresh)
          oid1 (bk/place! b bk/bid 100 10 0 1)]
      (bk/cancel! b oid1)
      (let [oid2 (bk/place! b bk/bid 100 7 0 2)]
        (is (not= oid1 oid2) "the reused slot yields a different id")
        (is (= 0 (bk/cancel! b oid1)) "the stale id must not cancel the new order")
        (is (= 7 (bk/level-qty b bk/bid 100)))))))

(deftest determinism-of-replay
  (testing "the same tape produces the same book and the same fills"
    (let [tape (vec (for [i (range 2000)]
                      [(mod i 2) (+ 400 (mod (* i 37) 200)) (inc (mod i 9)) 0 (mod i 5)]))
          run (fn []
                (let [b (fresh)]
                  (doseq [[s l q f o] tape] (bk/place! b s l q f o))
                  {:fills (bk/fills b)
                   :bid (bk/best b bk/bid)
                   :ask (bk/best b bk/ask)
                   :resting (bk/resting-count b)}))
          a (run) c (run)]
      (is (= a c))
      (is (pos? (count (:fills a))) "the tape must actually cross, or this proves nothing"))))

(deftest conservation-of-quantity
  (testing "every lot submitted is either filled, resting, or explicitly cancelled"
    (let [b (fresh)
          submitted (atom 0)
          cancelled (atom 0)
          oids (atom [])]
      (dotimes [i 1500]
        (let [side (mod i 2)
              level (+ 300 (mod (* i 53) 120))
              qty (inc (mod i 7))]
          (swap! submitted + qty)
          (let [oid (bk/place! b side level qty 0 (mod i 4))]
            (when (pos? oid) (swap! oids conj oid)))))
      (doseq [oid (take 200 @oids)]
        (swap! cancelled + (bk/cancel! b oid)))
      (let [filled (reduce + (map :qty (bk/fills b)))
            resting (reduce + (for [s [bk/bid bk/ask] l (range 1024)]
                                (bk/level-qty b s l)))]
        ;; each fill consumes one lot from a taker and one from a maker
        (is (= @submitted (+ (* 2 filled) resting @cancelled)))))))

(deftest impact-price-is-size-weighted
  (testing "a tiny quote at an absurd price cannot set the impact price"
    (let [b (fresh)]
      (bk/place! b bk/ask 100 1 0 1)      ; 1 lot at 100  -> notional 100
      (bk/place! b bk/ask 110 1000 0 1)   ; deep at 110
      ;; a 10000-notional order eats the 1 lot at 100 and ~90 lots at 110,
      ;; so the average must sit near 110, not at 100
      (let [ip (bk/impact-price b bk/ask 10000)]
        (is (> ip 108) (str "impact price " ip " should be dragged toward the deep level"))
        (is (<= ip 110))))))

(deftest next-occupied-walks-the-ladder
  (let [b (fresh)]
    (bk/place! b bk/ask 10 1 0 1)
    (bk/place! b bk/ask 700 1 0 1)
    (is (= 700 (bk/next-occupied b bk/ask 10)))
    (is (= -1 (bk/next-occupied b bk/ask 700)))
    (bk/place! b bk/bid 5 1 0 1)
    (bk/place! b bk/bid 900 1 0 1)
    (is (= 5 (bk/next-occupied b bk/bid 900)))
    (is (= -1 (bk/next-occupied b bk/bid 5)))))
