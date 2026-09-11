(ns torihiki.fixed
  "Fixed-point integer arithmetic for the torihiki state machine.

  EVERY quantity in this engine is an integer. There is no floating point
  anywhere on the state-transition path, because two validators that disagree
  on the last bit of a f64 produce different state roots and the chain halts.
  This ns defines the scales, the rounding, and — the load-bearing part — the
  RANGE INVARIANT that makes the JVM and JS paths bit-identical.

  ## The i53 domain

  Values are integers in [-(2^53-1), 2^53-1]. Not i64. The ceiling is set by
  the weaker of the two runtimes: JVM `long[]` holds i64 exactly, but JS has
  no i64 array whose elements survive arithmetic (`BigInt64Array` elements are
  BigInt, and `Number(bigint)` silently truncates above 2^53). Rather than let
  the two platforms diverge in the top 11 bits, we forbid those bits outright
  and check for them.

  This is not a limitation in practice, it is a scaling decision, and it is the
  same one every exchange makes. With the default scales below:

    BTC at $100,000  = 10^7 price ticks
    1 BTC            = 10^4 size lots
    notional of 1 BTC = 10^11        (2^53 is 9.007 x 10^15)
    1000 BTC position = 10^14        (still 90x of headroom)
    $1,000,000,000 of collateral = 10^11

  So the engine runs out of realistic market long before it runs out of
  integer. `check` exists to prove that claim on every write rather than
  assume it.

  ## Scales

  Chosen per-market at genesis, not hardcoded — a market's `tick` and `lot`
  live in its config. What IS fixed globally is the ratio unit for rates:
  `rate-scale` = 10^9, so a funding rate of 0.0001 (1 bp) is 100000.")

;; ── the i53 domain ──────────────────────────────────────────────────────────

(def ^:const i53-max 9007199254740991)   ; 2^53 - 1
(def ^:const i53-min -9007199254740991)

(defn in-domain?
  "True when `v` is an integer this engine is allowed to store. Rejects the
  i64 range JVM would accept but JS cannot mirror."
  [v]
  (and (<= i53-min v) (<= v i53-max)))

(defn check
  "Return `v` if it is inside the i53 domain, else throw. Called on every
  value that crosses into the slab, so an overflow surfaces as a rejected
  transaction at the boundary instead of as a silent state-root divergence
  between two validators three blocks later.

  `where` names the site for the error message; it is never used in the
  happy path, so passing a keyword literal costs nothing."
  [where v]
  (if (in-domain? v)
    v
    (throw (ex-info "torihiki.fixed: value escaped the i53 domain"
                    {:where where :value v :i53-max i53-max}))))

;; ── rates ───────────────────────────────────────────────────────────────────

(def ^:const rate-scale 1000000000)      ; 10^9. 1 bp = 100000, 100% = 10^9

(defn bps
  "Basis points -> rate. 1 bp = 0.0001."
  [n]
  (* n 100000))

(defn pct
  "Percent -> rate."
  [n]
  (* n 10000000))

;; ── rounding ────────────────────────────────────────────────────────────────
;;
;; Division has to round SOMEWHERE, and the direction is a consensus rule: two
;; implementations that round differently disagree on every fee. We use floor
;; division (toward negative infinity) as the single primitive, and express
;; every other rounding mode in terms of it, so there is exactly one place
;; where the question is answered.

(defn fdiv
  "Floor division. Rounds toward negative infinity on both platforms —
  unlike `quot`, which truncates toward zero and therefore rounds a
  loss-making position's PnL in the trader's favour and a winning one's
  against them."
  [a b]
  #?(:clj  (Math/floorDiv (long a) (long b))
     :cljs (Math/floor (/ a b))))

(defn fmod
  "Remainder consistent with `fdiv`: always has the sign of the divisor."
  [a b]
  (- a (* b (fdiv a b))))

(defn mul-rate
  "Apply a rate (scaled by `rate-scale`) to an integer amount, rounding
  toward negative infinity. Used for fees, funding, and margin fractions."
  [amount rate]
  (check :mul-rate (fdiv (* amount rate) rate-scale)))

(defn div-round-half-up
  "a/b rounding halves away from zero. Only used where a specification
  demands it; prefer `fdiv`."
  [a b]
  (let [neg? (not= (neg? a) (neg? b))
        q (fdiv (+ (Math/abs (double a)) (fdiv (Math/abs (double b)) 2))
                (Math/abs (double b)))]
    (long (if neg? (- q) q))))

;; ── notional ────────────────────────────────────────────────────────────────

(defn notional
  "price-ticks * size-lots, in the market's notional unit. The single most
  common place to blow the i53 ceiling, so it is checked."
  [price size]
  (check :notional (* price size)))

(defn abs*
  "Absolute value that stays integral on both platforms."
  [v]
  (if (neg? v) (- v) v))

(defn clamp
  [v lo hi]
  (cond (< v lo) lo
        (> v hi) hi
        :else v))
