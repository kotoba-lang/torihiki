(ns torihiki.book
  "The limit order book: price-time priority, O(1) place / cancel / match.

  This is the part of torihiki that replaces the closed HyperCore matching
  engine, and it is where the throughput target is either met or missed.

  ## Shape

  Three structures, all preallocated (see `torihiki.slab` for why):

  1. A PRICE LADDER. `lvl-head`/`lvl-tail`/`lvl-qty`, indexed by
     `side * n-levels + level`. A price is not a key to look up, it IS the
     index — the engine never searches for a price level.

  2. An INTRUSIVE FIFO per level. Orders live in a slab of slots; each slot
     carries `o-next`/`o-prev` into its level's queue. Resting appends to the
     tail, matching consumes from the head, and cancelling unlinks in place.
     Time priority is the list order, so it is maintained rather than sorted.

  3. A FOUR-LEVEL BIT LADDER. Answers \"what is the best bid / best ask\" in a
     bounded number of word operations, no matter how sparse the book is. A
     scan for the top of book would make the engine's cost depend on how far
     apart the quotes are, which is exactly the wrong sensitivity for a market
     that gaps.

  Free slots are a singly linked list threaded through `o-next`, so allocating
  and releasing an order slot are both a pointer swap. Nothing on the hot path
  allocates.

  ## Order ids

  An order id is `slot * gen-mod + generation`, not a sequence number, so
  cancel resolves to a slot with arithmetic instead of a hash lookup. The
  generation counter increments every time a slot is reused, which is what
  stops a stale cancel from hitting whoever inherited the slot (the ABA
  problem — without it, a client cancelling an order that already filled would
  silently cancel a stranger's).

  ## Determinism

  Every operation here is a pure function of (state, arguments): no clock, no
  randomness, no map iteration, no floating point. Replaying the same order
  tape against a fresh book produces the same book and the same event stream,
  which is what lets a validator verify a block instead of trusting it."
  (:require [torihiki.fixed :as fx]
            [torihiki.slab :as slab]))

;; ── constants ───────────────────────────────────────────────────────────────

(def ^:const bid 0)
(def ^:const ask 1)

(def ^:const flag-ioc 1)         ; cancel whatever does not fill immediately
(def ^:const flag-post-only 2)   ; reject if it would take
;; The book ignores this one — whether an order reduces a position is a
;; clearinghouse question and the book holds no positions. It lives here so
;; every flag has one home. `torihiki.state` enforces it.
(def ^:const flag-reduce-only 4)
(def ^:const gen-mod 1048576)    ; 2^20 generations before a slot id repeats

;; mutable scalars, kept in a slab so the book record itself stays immutable
(def ^:const ctl-free-head 0)
(def ^:const ctl-resting 1)
(def ^:const ctl-ev-count 2)
(def ^:const ctl-last-price 3)
(def ^:const ctl-size 4)

;; event field slabs
(def ^:const ev-fields 6)

(defrecord Book [n-levels cap ev-cap
                 w0 w1 w2
                 lvl-head lvl-tail lvl-qty
                 bm0 bm1 bm2 bm3
                 o-owner o-qty o-level o-side o-next o-prev o-gen
                 ev-maker-owner ev-taker-owner ev-maker-oid
                 ev-level ev-qty ev-taker-side
                 ctl])

(defn new-book
  "Allocate a book. `n-levels` is the size of the price ladder in ticks and
  must be a multiple of 32 no greater than 32^4; `cap` is the maximum number
  of simultaneously resting orders."
  [{:keys [n-levels cap ev-cap]
    :or {n-levels 65536 cap 1048576 ev-cap 65536}}]
  (when (pos? (rem n-levels 32))
    (throw (ex-info "n-levels must be a multiple of 32" {:n-levels n-levels})))
  (when (> n-levels 1048576)
    (throw (ex-info "n-levels exceeds what a four-level bit ladder addresses"
                    {:n-levels n-levels :max 1048576})))
  (let [w0 (quot n-levels 32)
        w1 (inc (quot (dec w0) 32))
        w2 (inc (quot (dec w1) 32))
        b (map->Book
           {:n-levels n-levels :cap cap :ev-cap ev-cap
            :w0 w0 :w1 w1 :w2 w2
            :lvl-head (slab/alloc-filled (* 2 n-levels) -1)
            :lvl-tail (slab/alloc-filled (* 2 n-levels) -1)
            :lvl-qty  (slab/alloc (* 2 n-levels))
            :bm0 (slab/alloc (* 2 w0))
            :bm1 (slab/alloc (* 2 w1))
            :bm2 (slab/alloc (* 2 w2))
            :bm3 (slab/alloc 2)
            :o-owner (slab/alloc cap)
            :o-qty   (slab/alloc cap)
            :o-level (slab/alloc cap)
            :o-side  (slab/alloc cap)
            :o-next  (slab/alloc cap)
            :o-prev  (slab/alloc cap)
            :o-gen   (slab/alloc cap)
            :ev-maker-owner (slab/alloc ev-cap)
            :ev-taker-owner (slab/alloc ev-cap)
            :ev-maker-oid   (slab/alloc ev-cap)
            :ev-level       (slab/alloc ev-cap)
            :ev-qty         (slab/alloc ev-cap)
            :ev-taker-side  (slab/alloc ev-cap)
            :ctl (slab/alloc ctl-size)})]
    ;; thread the free list: slot i hands off to slot i+1, last one terminates
    (let [on (:o-next b)]
      (dotimes [i cap] (slab/set! on i (if (= i (dec cap)) -1 (inc i)))))
    (slab/set! (:ctl b) ctl-free-head 0)
    (slab/set! (:ctl b) ctl-last-price -1)
    b))

;; ── bit ladder ──────────────────────────────────────────────────────────────

;; Hot functions read the Book's fields with direct interop (`.-bm0`,
;; `.-lvl_head`) rather than with keyword lookup. `(:bm0 b)` and `{:keys [bm0]}`
;; both compile to `clojure.lang.RT.get`, which walks a chain of `instanceof`
;; checks before it reaches the record's own `valAt` — measured at roughly
;; 20ns per field, which is why `best` still cost 180ns after the boxing was
;; gone: seven field reads dominated four array reads. Direct field access is
;; a single `getfield`.
;;
;; Clojure munges `-` to `_` in the generated Java field, so `lvl-head` is
;; read as `.-lvl_head`. That is ugly and it is confined to this file's hot
;; path on purpose; every public accessor below still takes and returns
;; ordinary Clojure values.

;; ── a note on `(long ...)` ──────────────────────────────────────────────────
;;
;; Every numeric value pulled out of the Book record or off a function
;; parameter is coerced with `(long ...)` before it is used in arithmetic.
;; This is not defensive noise, it is the difference between an engine that
;; hits its target and one that does not.
;;
;; A record field is an Object. `(* side w0)` on two Objects compiles to
;; `clojure.lang.Numbers.multiply(Object, Object)` — a virtual dispatch on
;; the runtime types plus a fresh boxed Long for the result. `best` does a
;; dozen such operations, and measured on this engine that made a function
;; which should cost single-digit nanoseconds cost 570: two orders of
;; magnitude, from arithmetic alone, with the array accesses innocent.
;;
;; Coercing once at the top of each function makes every operation after it
;; a primitive `ladd`/`lmul`. Overflow checking is deliberately left ON —
;; `*unchecked-math*` would buy a further sliver and give up the guarantee
;; that a wrapped value can never reach `torihiki.fixed/check` looking
;; legitimate.

(defn- ladder-set!
  [^Book b side level]
  (let [bm0 (slab/field b bm0) bm1 (slab/field b bm1) bm2 (slab/field b bm2) bm3 (slab/field b bm3)
        w0 (long (slab/field b w0)) w1 (long (slab/field b w1)) w2 (long (slab/field b w2))
        side (long side)
        level (long level)
        i1 (bit-shift-right level 5)
        i2 (bit-shift-right i1 5)
        i3 (bit-shift-right i2 5)]
    (slab/set! bm0 (+ (* side w0) i1)
               (bit-or (long (slab/get bm0 (+ (* side w0) i1)))
                       (bit-shift-left 1 (bit-and level 31))))
    (slab/set! bm1 (+ (* side w1) i2)
               (bit-or (long (slab/get bm1 (+ (* side w1) i2)))
                       (bit-shift-left 1 (bit-and i1 31))))
    (slab/set! bm2 (+ (* side w2) i3)
               (bit-or (long (slab/get bm2 (+ (* side w2) i3)))
                       (bit-shift-left 1 (bit-and i2 31))))
    (slab/set! bm3 side
               (bit-or (long (slab/get bm3 side))
                       (bit-shift-left 1 (bit-and i3 31))))))

(defn- ladder-clear!
  "Clear `level`, then clear each parent bit only once its whole child word
  has emptied — the cascade is what keeps `best` from returning a level that
  no longer has orders."
  [^Book b side level]
  (let [bm0 (slab/field b bm0) bm1 (slab/field b bm1) bm2 (slab/field b bm2) bm3 (slab/field b bm3)
        w0 (long (slab/field b w0)) w1 (long (slab/field b w1)) w2 (long (slab/field b w2))
        side (long side)
        level (long level)
        i1 (bit-shift-right level 5)
        i2 (bit-shift-right i1 5)
        i3 (bit-shift-right i2 5)
        k0 (+ (* side w0) i1)
        v0 (bit-and (long (slab/get bm0 k0))
                    (bit-not (bit-shift-left 1 (bit-and level 31))))]
    (slab/set! bm0 k0 v0)
    (when (zero? v0)
      (let [k1 (+ (* side w1) i2)
            v1 (bit-and (long (slab/get bm1 k1))
                        (bit-not (bit-shift-left 1 (bit-and i1 31))))]
        (slab/set! bm1 k1 v1)
        (when (zero? v1)
          (let [k2 (+ (* side w2) i3)
                v2 (bit-and (long (slab/get bm2 k2))
                            (bit-not (bit-shift-left 1 (bit-and i2 31))))]
            (slab/set! bm2 k2 v2)
            (when (zero? v2)
              (slab/set! bm3 side
                         (bit-and (long (slab/get bm3 side))
                                  (bit-not (bit-shift-left 1 (bit-and i3 31))))))))))))

(defn best
  "The best price level on `side` — highest for bids, lowest for asks — or -1
  when that side is empty. Four word reads regardless of book depth.

  Written as two mirrored branches rather than one branch that picks an
  accessor: `slab/get` is a macro (see that ns for why), so it cannot be
  bound as a local, and selecting `highest-set-bit`/`lowest-set-bit` as a
  function value would reintroduce the boxing the macros exist to remove."
  ^long [^Book b side]
  (let [bm0 (slab/field b bm0) bm1 (slab/field b bm1) bm2 (slab/field b bm2) bm3 (slab/field b bm3)
        w0 (long (slab/field b w0)) w1 (long (slab/field b w1)) w2 (long (slab/field b w2))
        side (long side)]
    (if (= side bid)
      (let [r3 (slab/highest-set-bit (slab/get bm3 side))]
        (if (neg? r3)
          -1
          (let [i2 (+ (* r3 32) (slab/highest-set-bit (slab/get bm2 (+ (* side w2) r3))))
                i1 (+ (* i2 32) (slab/highest-set-bit (slab/get bm1 (+ (* side w1) i2))))]
            (+ (* i1 32) (slab/highest-set-bit (slab/get bm0 (+ (* side w0) i1)))))))
      (let [r3 (slab/lowest-set-bit (slab/get bm3 side))]
        (if (neg? r3)
          -1
          (let [i2 (+ (* r3 32) (slab/lowest-set-bit (slab/get bm2 (+ (* side w2) r3))))
                i1 (+ (* i2 32) (slab/lowest-set-bit (slab/get bm1 (+ (* side w1) i2))))]
            (+ (* i1 32) (slab/lowest-set-bit (slab/get bm0 (+ (* side w0) i1))))))))))

;; ── slot lifecycle ──────────────────────────────────────────────────────────

(defn- take-slot!
  ^long [^Book b]
  (let [ctl (slab/field b ctl)
        s (slab/get ctl ctl-free-head)]
    (when (neg? s)
      (throw (ex-info "torihiki.book: order slab exhausted" {:cap (slab/field b cap)})))
    (slab/set! ctl ctl-free-head (slab/get (slab/field b o-next) s))
    s))

(defn- release-slot!
  [^Book b slot]
  (let [ctl (slab/field b ctl)
        o-gen (slab/field b o-gen)
        slot (long slot)]
    (slab/set! o-gen slot (mod (inc (slab/get o-gen slot)) gen-mod))
    (slab/set! (slab/field b o-qty) slot 0)
    (slab/set! (slab/field b o-next) slot (slab/get ctl ctl-free-head))
    (slab/set! ctl ctl-free-head slot)))

(defn oid-of
  ^long [^Book b slot]
  (let [slot (long slot)]
    (+ (* slot gen-mod) (slab/get (slab/field b o-gen) slot))))

(defn- slot-of ^long [^long oid] (quot oid gen-mod))
(defn- gen-of  ^long [^long oid] (rem oid gen-mod))

;; ── queue linking ───────────────────────────────────────────────────────────

(defn- link-tail!
  [^Book b side level slot]
  (let [lvl-head (slab/field b lvl-head) lvl-tail (slab/field b lvl-tail)
        o-next (slab/field b o-next) o-prev (slab/field b o-prev)
        n-levels (long (slab/field b n-levels))
        side (long side) level (long level) slot (long slot)
        k (+ (* side n-levels) level)
        t (slab/get lvl-tail k)]
    (slab/set! o-next slot -1)
    (slab/set! o-prev slot t)
    (if (neg? t)
      (do (slab/set! lvl-head k slot)
          (ladder-set! b side level))
      (slab/set! o-next t slot))
    (slab/set! lvl-tail k slot)))

(defn- unlink!
  [^Book b side level slot]
  (let [lvl-head (slab/field b lvl-head) lvl-tail (slab/field b lvl-tail)
        o-next (slab/field b o-next) o-prev (slab/field b o-prev)
        n-levels (long (slab/field b n-levels))
        side (long side) level (long level) slot (long slot)
        k (+ (* side n-levels) level)
        p (slab/get o-prev slot)
        n (slab/get o-next slot)]
    (if (neg? p) (slab/set! lvl-head k n) (slab/set! o-next p n))
    (if (neg? n) (slab/set! lvl-tail k p) (slab/set! o-prev n p))
    (when (and (neg? p) (neg? n))
      (ladder-clear! b side level))))

;; ── events ──────────────────────────────────────────────────────────────────

(defn- emit-fill!
  [^Book b taker-owner maker-owner maker-oid level qty taker-side]
  (let [ctl (slab/field b ctl)
        i (slab/get ctl ctl-ev-count)]
    (when (< i (long (slab/field b ev-cap)))
      (slab/set! (slab/field b ev-taker-owner) i taker-owner)
      (slab/set! (slab/field b ev-maker-owner) i maker-owner)
      (slab/set! (slab/field b ev-maker-oid) i maker-oid)
      (slab/set! (slab/field b ev-level) i level)
      (slab/set! (slab/field b ev-qty) i qty)
      (slab/set! (slab/field b ev-taker-side) i taker-side))
    (slab/set! ctl ctl-ev-count (inc i))))

(defn reset-events!
  "Drop the fill buffer. Called once per block, never per order."
  [^Book b]
  (slab/set! (:ctl b) ctl-ev-count 0)
  b)

(defn event-count [^Book b] (slab/get (:ctl b) ctl-ev-count))

(defn fills
  "Materialise the fill buffer as maps. Off the hot path — for tests, for
  settlement, and for anything that reads a block after it is sealed."
  [^Book b]
  (let [n (min (event-count b) (:ev-cap b))]
    (mapv (fn [i]
            {:taker-owner (slab/get (:ev-taker-owner b) i)
             :maker-owner (slab/get (:ev-maker-owner b) i)
             :maker-oid   (slab/get (:ev-maker-oid b) i)
             :level       (slab/get (:ev-level b) i)
             :qty         (slab/get (:ev-qty b) i)
             :taker-side  (slab/get (:ev-taker-side b) i)})
          (range n))))

;; ── the hot path ────────────────────────────────────────────────────────────

(defn- crosses?
  [^long taker-side ^long limit ^long best-level]
  (and (not (neg? best-level))
       (if (= taker-side bid)
         (<= best-level limit)
         (>= best-level limit))))

(defn place!
  "Submit an order. Returns the resting order id, or -1 when nothing rested
  (fully filled, IOC remainder cancelled, or post-only rejected).

  Mutates `b` in place. That is deliberate and it does not cost determinism:
  the transition is still a pure function of the book's contents and the
  arguments, so replay reconstructs it exactly. Immutability at this layer
  would buy nothing a snapshot does not already buy, and would cost the
  throughput target."
  [^Book b side level qty flags owner]
  (let [lvl-head (slab/field b lvl-head) lvl-qty (slab/field b lvl-qty)
        o-owner (slab/field b o-owner) o-qty (slab/field b o-qty)
        o-side (slab/field b o-side) o-level (slab/field b o-level) ctl (slab/field b ctl)
        n-levels (long (slab/field b n-levels))
        side (long side) level (long level)
        qty (long qty) flags (long flags) owner (long owner)
        opp (if (= side bid) ask bid)]
    (fx/check :place-qty qty)
    (when (or (neg? level) (>= level n-levels))
      (throw (ex-info "price level out of range" {:level level :n-levels n-levels})))
    (if (and (pos? (bit-and flags flag-post-only))
             (crosses? side level (best b opp)))
      -1
      (let [ioc? (pos? (bit-and flags flag-ioc))]
        (loop [remaining qty]
          (let [bl (best b opp)]
            (if (and (pos? remaining) (crosses? side level bl))
              ;; consume the FIFO at the best opposing level
              (let [k (+ (* opp n-levels) bl)
                    head (slab/get lvl-head k)]
                (if (neg? head)
                  ;; level bit set but queue empty — cannot happen, but clearing
                  ;; the bit and continuing beats looping forever if it ever does
                  (do (ladder-clear! b opp bl) (recur remaining))
                  (let [avail (slab/get o-qty head)
                        traded (min remaining avail)]
                    (emit-fill! b owner (slab/get o-owner head) (oid-of b head)
                                bl traded side)
                    (slab/add! lvl-qty k (- traded))
                    (slab/set! ctl ctl-last-price bl)
                    (if (= traded avail)
                      (do (unlink! b opp bl head)
                          (release-slot! b head)
                          (slab/add! ctl ctl-resting -1))
                      (slab/set! o-qty head (- avail traded)))
                    (recur (- remaining traded)))))
              ;; nothing more to take: rest the remainder unless told not to
              (if (or (zero? remaining) ioc?)
                -1
                (let [slot (take-slot! b)]
                  (slab/set! o-owner slot owner)
                  (slab/set! o-qty slot remaining)
                  (slab/set! o-side slot side)
                  (slab/set! o-level slot level)
                  (link-tail! b side level slot)
                  (slab/add! lvl-qty (+ (* side n-levels) level) remaining)
                  (slab/add! ctl ctl-resting 1)
                  (oid-of b slot))))))))))

(defn cancel!
  "Remove a resting order. Returns the cancelled quantity, or 0 when the id
  does not name a live order — which covers both a stale id whose slot has
  been reused and an order that filled while the cancel was in flight."
  [^Book b oid]
  (let [oid (long oid)
        slot (slot-of oid)]
    (if (or (neg? slot) (>= slot (long (slab/field b cap)))
            (not= (gen-of oid) (slab/get (slab/field b o-gen) slot))
            (not (pos? (slab/get (slab/field b o-qty) slot))))
      0
      (let [side (slab/get (slab/field b o-side) slot)
            level (slab/get (slab/field b o-level) slot)
            q (slab/get (slab/field b o-qty) slot)]
        (unlink! b side level slot)
        (slab/add! (slab/field b lvl-qty) (+ (* side (long (slab/field b n-levels))) level) (- q))
        (release-slot! b slot)
        (slab/add! (slab/field b ctl) ctl-resting -1)
        q))))

;; ── read-only views ─────────────────────────────────────────────────────────

(defn level-qty
  ^long [^Book b side level]
  (slab/get (slab/field b lvl-qty) (+ (* (long side) (long (slab/field b n-levels))) (long level))))

(defn level-orders
  "The resting queue at one price level, head first — i.e. in time priority.
  Off the hot path: this is for the state root, for a depth view, and for
  tests. Returns `[{:oid :owner :qty} ...]`."
  [^Book b side level]
  (let [o-next (slab/field b o-next) o-owner (slab/field b o-owner) o-qty (slab/field b o-qty)
        k (+ (* (long side) (long (slab/field b n-levels))) (long level))]
    (loop [slot (slab/get (slab/field b lvl-head) k) acc []]
      (if (neg? slot)
        acc
        (recur (slab/get o-next slot)
               (conj acc {:oid (oid-of b slot)
                          :owner (slab/get o-owner slot)
                          :qty (slab/get o-qty slot)}))))))

(defn resting-count ^long [^Book b] (slab/get (slab/field b ctl) ctl-resting))
(defn last-price    ^long [^Book b] (slab/get (slab/field b ctl) ctl-last-price))

(defn mid
  "Midpoint of the spread in ticks, doubled to stay integral: callers that
  need a true mid divide by two and decide their own rounding. -1 when either
  side is empty."
  [^Book b]
  (let [bb (best b bid) ba (best b ask)]
    (if (or (neg? bb) (neg? ba)) -1 (+ bb ba))))

(defn next-occupied
  "The next occupied level strictly beyond `level`, walking outward from the
  top of book — descending for bids, ascending for asks. -1 when there is
  none. Scans the level-0 bitmap a word at a time, so an empty stretch of the
  ladder costs one read per 32 ticks instead of one per tick."
  [^Book b side level]
  (let [bm0 (slab/field b bm0)
        w0 (long (slab/field b w0))
        n-levels (long (slab/field b n-levels))
        side (long side) level (long level)
        base (* side w0)]
    (if (= side bid)
      (loop [l (dec level)]
        (if (neg? l)
          -1
          (let [wi (bit-shift-right l 5)
                masked (bit-and (long (slab/get bm0 (+ base wi)))
                                (dec (bit-shift-left 2 (bit-and l 31))))]
            (if (zero? masked)
              (recur (dec (* wi 32)))
              (+ (* wi 32) (slab/highest-set-bit masked))))))
      (loop [l (inc level)]
        (if (>= l n-levels)
          -1
          (let [wi (bit-shift-right l 5)
                masked (bit-and (long (slab/get bm0 (+ base wi)))
                                (bit-not (dec (bit-shift-left 1 (bit-and l 31)))))]
            (if (zero? masked)
              (recur (* (inc wi) 32))
              (+ (* wi 32) (slab/lowest-set-bit masked)))))))))

(defn impact-price
  "The average price a market order of `notional` would pay on `side`, in
  ticks, or **-1 when the book cannot absorb the WHOLE reference size**.

  All-or-nothing is the load-bearing part, and the first version got it wrong.
  This feeds the funding premium and the mark price, and both exist to be hard
  to push around. Returning the average of a PARTIAL fill means a book holding
  one lot can answer the question — and that one lot then sets the reference
  price, which is exactly the manipulation the reference size was introduced
  to prevent. A partial fill is not a cheaper answer, it is the wrong answer,
  so it is refused.

  Following the occupied levels through the bit ladder keeps the cost
  proportional to the levels actually consumed rather than to the width of the
  ladder."
  [^Book b side notional]
  (loop [level (best b side) left notional cost 0 filled 0 last-level 0]
    (cond
      ;; the reference size was absorbed exactly
      (not (pos? left)) (if (pos? filled) (fx/fdiv cost filled) -1)

      ;; The book ran out. Succeed only if what is left over is smaller than a
      ;; single lot at the deepest price we touched — that is a rounding
      ;; remainder, not a shortfall. Anything larger means the book genuinely
      ;; could not carry the reference size, which is the case this function
      ;; refuses to answer.
      (neg? level) (if (and (pos? filled) (< left last-level))
                     (fx/fdiv cost filled)
                     -1)

      :else
      (let [q (level-qty b side level)]
        (if (zero? q)
          (recur (next-occupied b side level) left cost filled last-level)
          (let [take-q (min q (fx/fdiv left level))]
            (if (not (pos? take-q))
              ;; cannot afford another lot even at this price, and prices only
              ;; get worse from here — the remainder is sub-lot, so this is a
              ;; complete answer
              (if (pos? filled) (fx/fdiv cost filled) -1)
              (let [spent (fx/notional level take-q)]
                (recur (next-occupied b side level)
                       (- left spent)
                       (+ cost spent)
                       (+ filled take-q)
                       level)))))))))
