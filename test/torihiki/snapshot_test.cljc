(ns torihiki.snapshot-test
  "A snapshot is only worth having if restoring it is INDISTINGUISHABLE from
  never having restarted. Not close — indistinguishable, because two replicas
  that differ by one order id put different entries in `:rejected` and
  `:rejected` is in the state root.

  So these tests do not check that a restore 'looks right'. They check that
  the canonical bytes are equal, that the order ids are equal, and that the
  NEXT allocation is equal — the last one being the part a structural
  comparison cannot see."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.book :as bk]
            [torihiki.snapshot :as snap]
            [torihiki.state :as st]
            [torihiki.clearing :as cl]))

;; ── a book with history, not a fresh one ────────────────────────────────────

(defn- churned-book
  "A book that has been placed into, filled against, and cancelled out of, so
  that its slots are NOT a clean prefix and its generations are NOT all zero.
  A snapshot test against a freshly filled book proves almost nothing: every
  interesting failure lives in the slots that have been reused."
  []
  (let [b (bk/new-book {:n-levels 256 :cap 128 :ev-cap 256})
        oids (atom [])]
    ;; rest a ladder on both sides. `place!` returns the resting order id, or
    ;; -1 when nothing rested.
    (doseq [l (range 10 20)]
      (swap! oids conj [(bk/place! b bk/bid l (+ 1 l) 0 (+ 100 l)) (+ 100 l)]))
    (doseq [l (range 30 40)]
      (swap! oids conj [(bk/place! b bk/ask l (+ 2 l) 0 (+ 200 l)) (+ 200 l)]))
    ;; cancel every third, so slots come back and generations move. The owner
    ;; is carried beside the id because `cancel!` requires it now — an id alone
    ;; used to be enough, which is what let anybody cancel anybody.
    (doseq [[i [oid owner]] (map-indexed vector @oids)
            :when (and (zero? (mod i 3)) (not (neg? oid)))]
      (bk/cancel! b oid owner))
    ;; take some liquidity, so a level partially empties
    (bk/place! b bk/ask 12 5 0 999)
    (bk/reset-events! b)
    ;; and rest more, which must reuse the freed slots
    (doseq [l (range 20 25)]
      (bk/place! b bk/bid l 3 0 (+ 300 l)))
    b))

(defn- book-shape
  "Everything observable about a book except its identity: every level, in
  order, with the full resting queue INCLUDING order ids."
  [b]
  {:resting (bk/resting-count b)
   :last-price (bk/last-price b)
   :levels (into {}
                 (for [side [bk/bid bk/ask]
                       l (range 256)
                       :let [os (bk/level-orders b side l)]
                       :when (seq os)]
                   [[side l] {:qty (bk/level-qty b side l) :orders os}]))})

(deftest a-restored-book-is-indistinguishable
  (let [b (churned-book)
        r (bk/restore (bk/snapshot b))]
    (is (= (book-shape b) (book-shape r)))
    (testing "order ids survive — a client cancels by id"
      (let [oids (mapcat (fn [[_ {:keys [orders]}]] (map :oid orders))
                         (:levels (book-shape b)))]
        (is (seq oids))
        (is (= (set oids)
               (set (mapcat (fn [[_ {:keys [orders]}]] (map :oid orders))
                            (:levels (book-shape r))))))))))

(deftest the-next-slot-is-the-same-slot
  ;; The part a structural comparison cannot see. With the old free LIST this
  ;; is exactly what diverged: same book, different next id.
  (let [b (churned-book)
        r (bk/restore (bk/snapshot b))
        a (bk/place! b bk/bid 5 1 0 777)
        c (bk/place! r bk/bid 5 1 0 777)]
    (is (= a c)
        "a restored book handed out a different order id from the one it restored")
    (is (= (book-shape b) (book-shape r)))))

(deftest a-cancel-issued-before-the-snapshot-still-works-after
  (let [b (churned-book)
        ;; Any live order — picking a fixed level would silently test nothing
        ;; the day the fixture cancels that one.
        oid (first (for [l (range 256)
                         o (bk/level-orders b bk/bid l)]
                     (:oid o)))
        r (bk/restore (bk/snapshot b))]
    (is (some? oid))
    (is (= (bk/cancel! b oid 1) (bk/cancel! r oid 1)))
    (is (= (book-shape b) (book-shape r)))))

(deftest generations-of-freed-slots-are-carried
  ;; A generation stops a stale cancel from hitting whoever inherited a reused
  ;; slot. Resetting them on restore would hand out an id a cancel in flight
  ;; already names.
  (let [b (churned-book)
        s (bk/snapshot b)]
    (is (seq (:gens s)) "no generation moved — the fixture is not churning")
    (is (every? pos? (vals (:gens s))))
    (is (<= (:hwm s) (:cap s)))))

(deftest the-high-water-mark-tracks-peak-resting-not-total-placed
  ;; This is the property that keeps a snapshot small: with lowest-free
  ;; allocation, a book that churns forever through a few slots never grows
  ;; its high-water mark.
  (let [b (bk/new-book {:n-levels 256 :cap 1024 :ev-cap 256})]
    (dotimes [_ 500]
      (bk/cancel! b (bk/place! b bk/bid 50 1 0 1) 1))
    (is (= 1 (:hwm (bk/snapshot b)))
        "500 placements should reuse one slot, not walk the slab")))

;; ── the whole exchange ──────────────────────────────────────────────────────

(def market (cl/market {:id 1 :max-leverage 40 :tick 10 :lot 1}))

(defn- exchange-with-history []
  (let [ex (st/new-exchange {:market market
                             :book-opts {:n-levels 1024 :cap 256 :ev-cap 1024}})]
    (-> ex
        (st/apply-block
         {:height 1 :ts 1000
          :txs [{:tx :deposit :account 1 :amount 100000000}
                {:tx :deposit :account 2 :amount 100000000}
                {:tx :oracle :market 1 :price 500}
                {:tx :order :account 1 :market 1 :side 0 :level 495 :qty 10}
                {:tx :order :account 1 :market 1 :side 0 :level 490 :qty 20}
                {:tx :order :account 2 :market 1 :side 1 :level 505 :qty 15}
                {:tx :order :account 2 :market 1 :side 1 :level 510 :qty 25}]})
        (st/apply-block
         {:height 2 :ts 2000
          :txs [{:tx :order :account 2 :market 1 :side 1 :level 495 :qty 4}
                {:tx :trigger :account 1 :market 1 :trigger-price 520
                 :direction :above
                 :order {:side 1 :level 515 :qty 3 :flags 4}}
                ;; a rejection, so the taxonomy is exercised
                {:tx :order :account 1 :market 1 :side 0 :level -1 :qty 1}]}))))

(deftest the-canonical-bytes-survive-a-round-trip
  ;; The only comparison that matters. Structural equality is not enough:
  ;; two states can be `=` and encode differently, and it is the encoding a
  ;; validator signs.
  (let [ex (exchange-with-history)
        r (snap/restore (snap/capture ex))]
    (is (= (st/state-root ex) (st/state-root r)))
    (is (= (vec (st/canonical-bytes ex)) (vec (st/canonical-bytes r))))))

(deftest an-edn-round-trip-survives-too
  ;; Because the snapshot is going to disk, not into another local var.
  (let [ex (exchange-with-history)
        r (-> ex snap/capture snap/write-string snap/read-string* snap/restore)]
    (is (= (st/state-root ex) (st/state-root r)))))

(deftest applying-the-same-block-to-both-gives-the-same-root
  ;; The real question is not whether the restore looks like the original but
  ;; whether the two stay together under load.
  (let [ex (exchange-with-history)
        r (-> ex snap/capture snap/write-string snap/read-string* snap/restore)
        blk {:height 3 :ts 3000
             :txs [{:tx :order :account 1 :market 1 :side 0 :level 505 :qty 6}
                   {:tx :order :account 2 :market 1 :side 1 :level 480 :qty 30}
                   {:tx :cancel :account 1 :market 1 :oid 999999999}]}
        ex' (st/apply-block ex blk)
        r' (st/apply-block r blk)]
    (is (= (st/state-root ex') (st/state-root r')))
    (is (= (:rejected ex') (:rejected r'))
        "a cancel of an unknown id must be refused identically on both")))

(deftest rejected-is-carried-because-the-root-commits-to-it
  ;; It looks like block scratch and is not: `encode-rejections` is part of
  ;; `canonical-bytes`, so the root after block N includes block N's
  ;; rejections. Dropping them restores to a different root while everything
  ;; a human would check — book, balances, nonces — still looks right.
  (let [ex (exchange-with-history)]
    (is (seq (:rejected ex)) "the fixture stopped producing a rejection")
    (is (= (:rejected ex) (:rejected (snap/capture ex))))
    (is (= (:rejected ex) (:rejected (snap/restore (snap/capture ex)))))))

;; ── the host's views are not state, and must not ride in the state write ────

(defn- with-views
  "The same exchange with a host's views folded in beside it, the way
  `torihiki-node.validator`'s apply-fn does. Big enough that carrying them
  would be visible in the write, because the failure being tested for is a
  write that grew until the platform reset the object holding it."
  [ex]
  (assoc ex
         :tape (vec (for [i (range 2000)]
                      {:m 1 :level (+ 400 (mod i 200)) :qty 3 :side 0
                       :taker 1 :maker 2 :h i}))
         :candles {1 (vec (for [i (range 4000)]
                            {:o 500 :h* 505 :l 495 :c 502 :v 9 :h i}))
                   2 (vec (for [i (range 4000)]
                            {:o 20 :h* 21 :l 19 :c 20 :v 4 :h i}))}
         :refused (vec (repeat 5000 :insufficient-margin))))

(deftest the-views-are-outside-the-root-which-is-why-they-can-be-dropped
  ;; The premise of dropping them. If any of these were under the root,
  ;; dropping them would be a divergence rather than a shorter chart.
  (let [ex (exchange-with-history)
        v (with-views ex)]
    (is (= (st/state-root ex) (st/state-root v)))
    (is (= (vec (st/canonical-bytes ex)) (vec (st/canonical-bytes v))))))

(deftest capture-does-not-carry-the-hosts-views
  (let [v (with-views (exchange-with-history))
        c (snap/capture v)]
    (doseq [k snap/view-keys]
      (is (not (contains? c k))
          (str "the state write is still carrying " k)))
    (testing "and the state it does carry is unchanged"
      (is (= (st/state-root v) (st/state-root (snap/restore c))))
      (is (= (vec (st/canonical-bytes v))
             (vec (st/canonical-bytes (snap/restore c))))))
    (testing "which is the whole point: the write got smaller"
      (is (< (count (snap/write-string c))
             (quot (count (snap/write-string (assoc c :tape (:tape v)
                                                   :candles (:candles v)
                                                   :refused (:refused v))))
                   10))
          "dropping the views did not shrink the state write by much"))))

(deftest rejected-is-not-refused
  ;; Two keys one letter apart, and only one of them is state. `:rejected` is
  ;; under the root and is carried; `:refused` is the host's running tally of
  ;; reasons and is not. Getting these the wrong way round would drop state.
  (let [v (with-views (exchange-with-history))
        c (snap/capture v)]
    (is (seq (:rejected c)) "the fixture stopped producing a rejection")
    (is (= (:rejected v) (:rejected c)))
    (is (not (contains? c :refused)))))

(deftest an-older-snapshot-that-still-has-views-restores
  ;; Every checkpoint written before this change carries them. Refusing those
  ;; would make all four replicas replay the log at once, which is the outage
  ;; a snapshot exists to prevent — so the version did not move and an old
  ;; shape still loads, views and all.
  (let [ex (exchange-with-history)
        old (-> (snap/capture ex)
                (assoc :tape [{:m 1 :level 500 :qty 1 :h 7}]
                       :candles {1 [{:o 500 :c 500 :h 7}]}
                       :refused [:unknown-order]))
        r (-> old snap/write-string snap/read-string* snap/restore)]
    (is (= (st/state-root ex) (st/state-root r)))
    (is (= [{:m 1 :level 500 :qty 1 :h 7}] (:tape r))
        "an old snapshot's views were dropped on the way back in")))

(deftest an-unknown-format-version-is-refused-not-guessed
  (let [s (assoc (snap/capture (exchange-with-history)) :snapshot/version 999)]
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (snap/restore s)))))
