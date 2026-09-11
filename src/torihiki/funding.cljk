(ns torihiki.funding
  "Funding rates: the mechanism that keeps a perpetual's price tethered to
  spot without ever settling.

  The formula is Hyperliquid's, reimplemented rather than copied — their
  engine is closed source, so this is derived from the published
  specification:

    premium              = impact_price_difference / oracle_price
    impact_price_diff    = max(impact_bid - oracle, 0) - max(oracle - impact_ask, 0)
    F (per 8h)           = avg(premium) + clamp(interest - avg(premium), -0.0005, 0.0005)
    paid hourly at F/8, capped at 4%/hour
    payment              = position_size * ORACLE_price * rate

  Four details in that specification are easy to get wrong and each one is a
  real consequence:

  1. THE PREMIUM USES IMPACT PRICES, NOT THE TOP OF BOOK. `impact_bid` is what
     a reference-sized order would actually pay. Using the best bid instead
     would let one lot quoted at an absurd price drag funding for everyone —
     a cheap attack, since the quote need never be filled.

  2. THE INTEREST TERM IS CLAMPED, NOT THE RATE. The clamp is applied to
     `interest - premium`, so when the premium is large the interest component
     contributes at most +/-0.0005 and the premium dominates. Clamping the
     final rate instead would break the tether exactly when it is needed.

  3. PAYMENT IS COMPUTED ON THE ORACLE PRICE, NOT THE MARK. Using the mark
     would make funding manipulable by the same book pressure funding exists
     to counteract.

  4. THE PREMIUM IS AVERAGED OVER SAMPLES, NOT READ ONCE. Sampling every five
     seconds across the hour means a momentary dislocation cannot set the
     rate; it has to persist to matter.

  Pure and integral throughout: sampling accumulates a sum and a count, so the
  average involves exactly one division, at the moment the hour closes."
  (:require [torihiki.fixed :as fx]
            [torihiki.book :as bk]))

;; ── parameters ──────────────────────────────────────────────────────────────

(def default-params
  {;; 0.01% per 8h, expressed per-8h in rate-scale units
   :interest-rate (fx/bps 1)
   ;; the clamp on (interest - premium), +/- 0.0005
   :clamp (fx/bps 5)
   ;; 4% per hour, the ceiling on the hourly rate actually charged
   :hourly-cap (fx/pct 4)
   ;; the order size the premium is measured against, in quote notional
   :impact-notional 20000
   ;; how many samples make an hour at a five-second cadence
   :samples-per-hour 720})

;; ── sampling ────────────────────────────────────────────────────────────────

(defn premium
  "One premium observation, in rate-scale units. Returns nil when the book is
  too thin on either side to absorb the reference size — a missing sample is
  honest, whereas substituting zero would quietly pull funding toward neutral
  every time liquidity dried up."
  [book oracle-price impact-notional]
  (let [ib (bk/impact-price book bk/bid impact-notional)
        ia (bk/impact-price book bk/ask impact-notional)]
    (when (and (pos? ib) (pos? ia) (pos? oracle-price))
      (let [diff (- (max (- ib oracle-price) 0)
                    (max (- oracle-price ia) 0))]
        (fx/fdiv (* diff fx/rate-scale) oracle-price)))))

(defn sample
  "Fold one observation into an accumulator. The accumulator carries a sum and
  a count rather than a running mean, so the hour's average costs one division
  and no rounding accumulates across samples."
  [acc p]
  (if (nil? p)
    acc
    (-> acc
        (update :sum (fnil + 0) p)
        (update :n (fnil inc 0)))))

(def empty-accumulator {:sum 0 :n 0})

(defn average-premium
  [{:keys [sum n]}]
  (if (or (nil? n) (zero? n)) 0 (fx/fdiv sum n)))

;; ── the rate ────────────────────────────────────────────────────────────────

(defn eight-hour-rate
  "F = P + clamp(interest - P, -clamp, +clamp), all in rate-scale units."
  [avg-premium {:keys [interest-rate clamp] :as _params}]
  (+ avg-premium
     (fx/clamp (- interest-rate avg-premium) (- clamp) clamp)))

(defn hourly-rate
  "One eighth of the eight-hour rate, then capped. The cap is applied AFTER
  the division so it means what it says — four percent of the hour actually
  charged, not four percent of an eight-hour figure."
  [acc params]
  (let [f8 (eight-hour-rate (average-premium acc) params)
        cap (:hourly-cap params)]
    (fx/clamp (fx/fdiv f8 8) (- cap) cap)))

(defn payment
  "What a position pays (positive) or receives (negative) this hour.

  Longs pay shorts when the rate is positive, so the sign follows
  `size * rate` directly. The oracle price is used deliberately — see this
  namespace's docstring."
  [position-size oracle-price rate]
  (fx/mul-rate (fx/notional oracle-price position-size) rate))

;; ── application ─────────────────────────────────────────────────────────────

(defn apply-funding
  "Charge one hour of funding on `mkt` to every account holding a position.

  Funding is a transfer between traders, not a fee: what longs pay, shorts
  receive. Because both sides are computed from the same rate and the same
  oracle price, the payments sum to zero up to rounding, and the rounding
  residue is accumulated into `:funding-residue` rather than silently created
  or destroyed. A residue that is never accounted for is how a clearinghouse
  slowly becomes insolvent without anyone seeing it happen."
  [state mkt oracle-price rate]
  (let [accts (keys (:accounts state))
        ;; sorted for determinism: map iteration order is not specified, and
        ;; two validators that fold funding in different orders would produce
        ;; different rounding residues and therefore different state roots
        accts (sort accts)]
    (reduce
     (fn [st acct]
       (let [size (get-in st [:accounts acct :positions mkt :size] 0)]
         (if (zero? size)
           st
           (let [p (payment size oracle-price rate)]
             (-> st
                 (update-in [:accounts acct :collateral] (fnil - 0) p)
                 (update :funding-residue (fnil + 0) p))))))
     state
     accts)))
