(ns torihiki.slab
  "Preallocated flat integer arrays — the only storage the hot path touches.

  ## Why not Clojure's own collections

  A persistent vector or map is the right default for almost everything in
  this workspace, and the wrong one here. Resting an order allocates; matching
  against it allocates again; a hash-map keyed by order id allocates a third
  time and then hands back an ITERATION ORDER THAT IS NOT SPECIFIED — the same
  hazard already recorded in this workspace's CLAUDE.md for
  `css.core/declarations`, where a map silently stops being ordered past eight
  entries. In a renderer that produces a cosmetic diff. In a replicated state
  machine it produces two validators with two different state roots.

  So the book is struct-of-arrays: one flat integer array per field, indexed by
  slot number. Allocation happens once at genesis. The hot path does array
  loads and stores and nothing else.

  ## Platform

  `:clj` backs a slab with `long[]`. `:cljs` backs it with `Float64Array`,
  which stores integers below 2^53 exactly and, unlike `BigInt64Array`, yields
  plain numbers that ordinary arithmetic accepts. `torihiki.fixed/check` keeps
  every stored value inside that shared domain, so the two platforms are not
  merely both-correct, they are bit-identical. The JVM path is the one that
  meets the throughput target; the JS path exists so a browser or a Worker can
  replay and verify a block without trusting a validator.

  ## Accessors are macros, and that is not a style choice

  `get`/`set!`/`add!` are macros rather than functions because a Clojure
  function returns Object: `(defn get [a i] (aget a i))` allocates a boxed
  Long on EVERY ARRAY READ. Measured on this engine, that one detail cost
  8.6 microseconds per operation and held throughput at 115k ops/sec — the
  boxing, not the array access, was essentially the entire cost. As macros
  the same call sites compile to a raw array load and the engine reaches its
  target.

  The consequence to remember: a macro is not a value. `slab/get` cannot be
  passed to `map` or bound as a local, which is why `torihiki.book/best` is
  written as an explicit branch instead of selecting an accessor."
  (:refer-clojure :exclude [get set!])
  #?(:cljs (:require-macros [torihiki.slab :refer [get set! add!]])))

(defn- cljs-env?
  "True when a macro is expanding for ClojureScript — `&env` carries an `:ns`
  key only under the cljs compiler."
  [env]
  (boolean (:ns env)))

(defn alloc
  "A slab of `n` integers, zero-filled."
  [n]
  #?(:clj  (long-array n)
     :cljs (js/Float64Array. n)))

(defn alloc-filled
  "A slab of `n` integers, every element `v`. Used for the -1 sentinels that
  mean \"no order here\" — zero is a valid slot index, so empty cannot be 0."
  [n v]
  #?(:clj  (let [a (long-array n)] (java.util.Arrays/fill a (long v)) a)
     :cljs (let [a (js/Float64Array. n)] (.fill a v) a)))

(defmacro get
  [a i]
  (if (cljs-env? &env)
    `(aget ~a ~i)
    `(aget ~(with-meta a {:tag 'longs}) (int ~i))))

(defmacro set!
  [a i v]
  (if (cljs-env? &env)
    `(aset ~a ~i ~v)
    `(aset ~(with-meta a {:tag 'longs}) (int ~i) (long ~v))))

(defmacro add!
  "a[i] += v, returning the new value."
  [a i v]
  (if (cljs-env? &env)
    `(let [a# ~a i# ~i nv# (+ (aget a# i#) ~v)]
       (aset a# i# nv#)
       nv#)
    `(let [a# ~(with-meta a {:tag 'longs})
           i# (int ~i)
           nv# (+ (aget a# i#) (long ~v))]
       (aset a# i# nv#)
       nv#)))

(defn size
  [a]
  #?(:clj  (alength ^longs a)
     :cljs (.-length a)))

(defn copy
  "An independent duplicate — used for snapshots, never on the hot path."
  [a]
  #?(:clj  (java.util.Arrays/copyOf ^longs a (alength ^longs a))
     :cljs (js/Float64Array.from a)))

;; ── bit ladder ──────────────────────────────────────────────────────────────
;;
;; Finding the best bid and best ask is the single most frequent question the
;; matching engine asks, so it must not be a scan. A three-level bitmap over
;; the price ladder answers it in a bounded number of word operations
;; regardless of how sparse the book is: level 0 has one bit per price level,
;; level 1 one bit per level-0 word, level 2 one bit per level-1 word. With
;; 32-bit words and three levels a single ladder addresses 32^3 = 32768 price
;; levels; four would reach a million.
;;
;; Words are 32 bits, not 64, because JS bitwise operators are defined on
;; int32 and a 64-bit word would need splitting on that platform anyway.

(def ^:const word-bits 32)

(defn bit-count-words
  [n-levels]
  (inc (quot (dec n-levels) word-bits)))

;; These stay functions rather than macros — they are called once per lookup,
;; not once per array element — but they carry primitive `^long` hints so the
;; call sites still avoid boxing.

;; The `^long` goes on the ARGUMENT VECTOR, not on the fn name. On the name it
;; becomes a :tag on the var, which makes Clojure declare `invokePrim` as
;; returning Object and every call site dies with AbstractMethodError.

(defn lowest-set-bit
  "Index of the least significant set bit of `w`, or -1 when `w` is zero.
  `w` is treated as 32 bits."
  ^long [^long w]
  #?(:clj  (let [w (bit-and w 0xFFFFFFFF)]
             (if (zero? w) -1 (long (Long/numberOfTrailingZeros w))))
     :cljs (let [w (bit-or w 0)]
             (if (zero? w) -1 (- 31 (Math/clz32 (bit-and w (- w))))))))

(defn highest-set-bit
  "Index of the most significant set bit of `w`, or -1 when `w` is zero."
  ^long [^long w]
  #?(:clj  (let [w (bit-and w 0xFFFFFFFF)]
             (if (zero? w) -1 (- 63 (long (Long/numberOfLeadingZeros w)))))
     :cljs (let [w (bit-or w 0)]
             (if (zero? w) -1 (- 31 (Math/clz32 w))))))
