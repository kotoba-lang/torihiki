;; falsify-13 harness — is :insurance-fund accumulation (liquidation.cljc:195)
;; sum-unchecked across 2^53, the last unmeasured accum-class site?
;;
;; Hypothesis (registered pre-run in evidence/2026-09-09-1206-falsify13-hypothesis.md):
;; liq/liquidate* stage 1 (liquidation.cljc:187-197) computes fee via
;; fx/mul-rate (i53-checked) but accumulates with
;; (update :insurance-fund (fnil + 0) fee) — NO sum check, the falsify-8/9/10/11/12
;; class. :insurance-fund is folded into state-root via encode-clearing-totals
;; (state.cljc:1601), so a sum crossing onto a double-irreversible (odd) value
;; must silently diverge JVM/nbb roots with zero throws.
;;
;; Measured bring-up (both runtimes identical each round):
;;   R1: fee = fdiv(N·F, 1e9), F = 10 bp = 1e6 -> fee = N/10^4 exactly (floor).
;;   R2: liquidate arity — production signature is
;;       [state acct mkt mark now markets params take-fn] (8 args, liquidation.cljc:247).
;;   R3: stage-1 (:book) fired once; second call at the same `now` was :cooldown
;;       (30 logical seconds). Also MEASURED the real per-event fee:
;;       position 9e9 lots at mark 1000 -> slice = mul-rate(9e9, pct 20) =
;;       1.8e9 lots -> fill notional 1.8e12 -> fee = 1.8e9 (even, in-domain;
;;       N·F = 1.8e18 < 2^63 so mul-rate is bit-identical on both runtimes).
;;       fund1 - fund0 = 1.8e9 confirmed (9007183054740993 - 9007181254740993).
;;   R4: slice shrinks each event (20% of remaining), so event 2's fee would be
;;       1.44e9 — the position is RE-SEEDED to 9e9 lots between events (the
;;       falsify-12 per-block reseed method) so both events pay fee = 1.8e9.
;;       seed = 2^53+1 - 2·(1.8e9); two events land exactly on 2^53+1.
;;   Honest ceiling (measured): N-cap = 9223372036854 (N·F < 2^63), fee-cap
;;   9223372036, honest events-to-cross 976,562 — nbb-infeasible honestly.
;;
;; Same rules as falsify-6..12: NO try/catch in this file; drivers own catching;
;; nothing in src/ or test/ is touched.
(ns falsify13-insurance-accum
  (:require [torihiki.state :as st]
            [torihiki.clearing :as cl]
            [torihiki.liquidation :as liq]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)
(def two53+1 9007199254740993)   ; 2^53+1, odd, double-irreversible

(defn header []
  "falsify-13: does :insurance-fund accumulation (liquidation.cljc:195) cross 2^53 unchecked?")

;; ── shared state construction ──────────────────────────────────────────────
(defn- fresh []
  (st/new-exchange {:market (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                    :book-opts {:n-levels 65536 :cap 65536 :ev-cap 65536}}))

(defn- fund [ex] (get-in ex [:clearing :insurance-fund] 0))

;; the live long each event liquidates: 9e9 lots, entry 1000, mark 1000
(def pos {:size 9000000000 :entry-notional 9000000000000 :isolated nil})
;; measured per-event stage-1 fee for `pos` (R3): slice 1.8e9 lots at 1000
;; -> notional 1.8e12 -> fee = fdiv(1.8e12·1e6, 1e9) = 1.8e9
(def fee-evt 1800000000)

;; ── probe A: payment identity (bit-exactness of the fee) ───────────────────
(defn probe-payment-identity
  "The exact fee code path of liquidation.cljc:190 on the measured slice:
  fx/mul-rate over the slice fill notional, and the two-event parity walk
  (seed = 2^53+1 − 2·fee lands exactly on 2^53+1)."
  []
  (let [slice-notional 1800000000000   ; 1.8e9 lots at level 1000
        F (fx/bps 10)                  ; 1e6 — default-params liquidation-fee
        fee (fx/mul-rate slice-notional F)
        seed (- two53+1 fee fee)]
    {:probe (str "payment-identity slice-notional=" slice-notional " F=" F)
     :fee fee :fee-odd (odd? fee) :fee-in-domain (<= (fx/abs* fee) i53-max)
     :seed seed :seed-in-domain (<= (fx/abs* seed) i53-max)
     :two-fee-sum (+ fee fee) :sum-lands-on-two53+1 (= (+ seed fee fee) two53+1)}))

;; ── probe B: honest ceiling (no seed) ──────────────────────────────────────
(defn probe-honest-ceiling
  "The largest fee one stage-1 liquidation can produce: N*F must stay < 2^63
  for mul-rate's unchecked product. Shows the honest crossing distance."
  []
  (let [N-cap 9223372036854      ; largest N with N*1e6 < 2^63
        fee-cap (fx/mul-rate N-cap (fx/bps 10))
        events-needed (fx/fdiv (inc i53-max) fee-cap)]
    {:probe "honest-ceiling (no seed)"
     :N-cap N-cap :fee-cap fee-cap
     :events-to-cross events-needed}))

;; ── probe C: seeded crossing through the REAL liquidate path ───────────────
(defn probe-seeded-crossing
  "The falsify-9/10/12 crossing method, two events: seed :insurance-fund to
  2^53+1 − 2·fee, then run TWO real liq/liquidate calls (production arity,
  liquidation.cljc:247) on a live long whose slice is absorbed above
  bankruptcy (take-fn). Event 2 runs at now=1000 (past the 30-logical-second
  cooldown) with the position re-seeded to 9e9 lots (falsify-12 reseed
  method) so its fee equals event 1's. Each call runs the production
  accumulator at liquidation.cljc:195. After event 2 the fund = 2^53+1
  (odd) -> JVM keeps the exact long, nbb rounds to 2^53 -> root divergence,
  zero throws expected. The account is well-collateralized (i53-max) so
  settle-deficit (liquidation.cljc:252) is a no-op."
  []
  (let [seed (- two53+1 fee-evt fee-evt)
        take-fn (fn [delta _mark] [(- delta) 1000])
        run-1 (fn [ex acct now]
                (liq/liquidate (:clearing ex) acct 1 1000 now
                               (:markets ex) liq/default-params take-fn))
        reseed (fn [ex] (assoc-in ex [:clearing :accounts 4 :positions 1] pos))]
    (if (not (= (+ seed fee-evt fee-evt) two53+1))
      {:probe "seeded-crossing" :aborted "two-fee sum does not land on 2^53+1"}
      (let [ex0 (-> (fresh)
                    (assoc :ts 100)
                    (assoc-in [:marks 1] 1000)
                    (assoc-in [:oracle 1] 1000)
                    (assoc-in [:clearing :insurance-fund] seed)
                    (assoc-in [:clearing :accounts 4 :collateral] i53-max)
                    (assoc-in [:clearing :accounts 4 :positions 1] pos))
            cl1 (run-1 ex0 4 100)
            ex1 (-> ex0
                    (assoc :clearing (:state cl1))
                    (assoc :ts 1000)
                    reseed)
            cl2 (run-1 ex1 4 1000)
            ex2 (assoc ex1 :clearing (:state cl2))]
        {:probe (str "seeded-crossing seed=" seed " fee=" fee-evt " x2")
         :stage1 (:stage cl1) :stage2 (:stage cl2)
         :fund0 seed
         :fund1 (fund ex1) :root1 (str (st/state-root ex1))
         :fund2 (fund ex2) :root2 (str (st/state-root ex2))}))))

(defn line-threw
  [probe e]
  (str "probe " probe
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))
