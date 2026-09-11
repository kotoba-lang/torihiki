;; falsify-11 harness — does the sum-unchecked :fees-collected accumulation
;; (clearing.cljc:410 apply-fill / :560 spot-fill) cross 2^53 silently?
;;
;; Hypothesis (evidence/2026-09-04-falsify-11-fees-collected-accum-overflow-notrun.md,
;; registered pre-run): cl/apply-fill fx/checks each fill's fee through
;; fx/mul-rate (in-domain), but accumulates
;; (update :fees-collected (fnil + 0) (- fee share)) with NO check on the
;; running sum — the falsify-8 (collateral) / falsify-9 (:deficit) /
;; falsify-10 (:funding-residue) shape: delta-checked, sum-unchecked.
;; :fees-collected IS under the state root (state.cljc:1602
;; encode-clearing-totals), so a non-invertible (odd >= 2^53) total is a
;; direct silent cross-runtime root divergence.
;;
;; Boundary-distance trick applied to the FEE path (what NEXT asked to verify
;; first): fee = floor(N x R / 1e9) with
;;   N = level x qty = 720 x 1000000001500 = 720000001080000 (< 2^53;
;;       level 720 < book n-levels 1024 — the ladder is tick-indexed)
;;   R = 12500 (market :taker-fee-rate; same scale as falsify-10's real rate)
;;   product P = N x R = 9000000013500000000 (< 2^63 — falsify-9's cap)
;;   P mod 1e9 = 5e8 boundary distance >> double error (<=1024 at ~9e18)
;;   => fee = 9000000013, ODD, bit-identical on JVM and nbb.
;;
;; probe A (both runtimes) — payment-identity: N, P, boundary-dist, fee.
;; probe B (both runtimes) — fees-seeded-crossing: F0 = 2^53+1-fee =
;;   9007190254740992 (even, reversible) seeded into :fees-collected; then 4
;;   REAL :order crosses (maker account 1 bid, taker account 2 sell at the
;;   same level; maker-fee-rate 0 so one cross adds exactly one odd fee).
;;   Prediction: cross 1 lands :fees-collected on 2^53+1 (odd) -> roots
;;   DIVERGE with zero throws; odd fee preserves the nbb rounding residue,
;;   divergence persists (falsify-10 rule). Collateral stays in-domain
;;   (taker only debited the fee; realized PnL is 0 at a single level).
;; probe C (JVM only) — honest-walk: from :fees-collected 0, real crosses
;;   until the crossing (fee ~9e9 => ~1,000,800 crosses); proves the
;;   crossing is reachable without seeding.
;;
;; Same rules as falsify-6..10: NO try/catch in this file (nbb load-string
;; does not resolve reader conditionals); the drivers own catching and print
;; via `line-threw`. Nothing in src/ or test/ is touched.
(ns falsify11-fees-accum
  (:require [torihiki.state :as st]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)

(def level 720)
(def qty 1000000001500)
(def notional (fx/abs* (fx/notional level qty)))   ; 720000001080000
(def rate 12500)
(def fee-expected 9000000013)                      ; odd

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1
                             :taker-fee-rate rate :maker-fee-rate 0}
                    :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

(defn- deposit!
  [ex acct amt]
  (st/apply-block ex {:height (:height ex 1) :ts 1000
                      :txs [{:tx :deposit :account acct :amount amt}]}))

(defn- seed
  "SYNTHETIC seeding of the fees-collected total ~1e6 honest fee-charged
  crosses would leave (time compression only), plus in-domain collateral so
  no liquidation path fires. The accumulator and apply path are unmodified
  production code."
  [fees0]
  (-> (fresh)
      (deposit! 1 i53-max)
      (deposit! 2 i53-max)
      (assoc-in [:clearing :fees-collected] fees0)))

(defn- cross!
  "One REAL fill: account 1 rests `maker-side` at `level` with `fill` lots,
  `taker-acct`'s `taker-side` order of `fill` lots crosses it. Returns the
  state after the taker's block."
  [ex h maker-side taker-side taker-acct fill]
  (let [ex (st/apply-block ex {:height h :ts (+ 1000 h)
                               :txs [{:tx :order :account 1 :market 1
                                      :side maker-side :level level
                                      :qty fill :flags 0}]})]
    (st/apply-block ex {:height (inc h) :ts (+ 1001 h)
                        :txs [{:tx :order :account taker-acct :market 1
                               :side taker-side :level level
                               :qty fill :flags 0}]})))

(defn- fees [ex] (get (:clearing ex) :fees-collected 0))
(defn- coll [ex a] (get-in ex [:clearing :accounts a :collateral] 0))

(defn- snap [ex k]
  {:k k :fees (fees ex) :coll1 (coll ex 1) :coll2 (coll ex 2)
   :root (str (st/state-root ex))})

(defn probe-payment-identity
  "Show the fee is fx/check'd in-domain and identical by construction: the
  product sits 5e8 away from the floor-division boundary."
  []
  (let [product (* notional rate)]
    {:probe "payment-identity"
     :notional notional
     :rate rate
     :product product
     :prod-lt-2-63 (< product 9223372036854775807)
     :notional-lt-2-53 (< notional i53-max)
     :boundary-dist (mod product 1000000000)
     :fee (fx/mul-rate notional rate)
     :fee-expected fee-expected
     :fee-odd (odd? (fx/mul-rate notional rate))}))

(defn probe-fees-seeded-crossing
  "Four REAL crosses over the seeded state. Prediction: no throw anywhere;
  roots equal after cross 1's seed step but DIVERGE when the total lands on
  2^53+1 (odd), divergence persisting through cross 4."
  []
  (let [f0 9007190254740980                       ; 2^53+1-fee, LITERAL (even,
                                                  ; < 2^53, double-exact —
                                                  ; computing it as
                                                  ; (- (inc i53-max) fee)
                                                  ; rounds differently on nbb)
        s0 (seed f0)
        c1 (cross! s0 2 0 1 2 qty)
        c2 (cross! c1 4 0 1 2 qty)
        c3 (cross! c2 6 0 1 2 qty)
        c4 (cross! c3 8 0 1 2 qty)]
    {:probe "fees-seeded-crossing (F0 = 2^53+1-fee)"
     :seed-fees (fees s0) :seed-root (str (st/state-root s0))
     :c1 (snap c1 1) :c2 (snap c2 2) :c3 (snap c3 3) :c4 (snap c4 4)}))

(defn probe-honest-walk
  "JVM-only: from :fees-collected 0, real crosses until the total passes
  2^53 (fees ~9e9/cross => ~1,000,800 crosses; zero throws predicted).

  Constraint discovered on the way (measured, see evidence md): the REDUCING
  path of apply-fill-to-position (clearing.cljc:157) multiplies
  (* entry-notional closed) UNCHECKED — with entry-notional 7.2e14 and a
  full-size close (1e12 lots) the JVM throws bare \"long overflow\" (nbb
  would round silently). So the walk uses only OPENING and FLIPPING fills:
  fill sequence per taker is sell q (open short q), then buy (q+x) / sell
  (q+x) alternating — every fill after the first crosses zero, never
  reducing. x = 20000000000 keeps each fill's mul-rate product
  9000180013500000000 < 2^63 while staying odd-fee
  (9000180013; cross 1's fee is 9000000013, also odd). The taker rotates
  2 -> 3 halfway so neither account's collateral (i53-max each) is
  exhausted before the crossing; oracle is pinned to `level` so unrealized
  PnL is 0 and the per-block liquidation sweep stays idle. Maker (account
  1) mirrors the same open/flip shape and pays no fee (maker-fee-rate 0)."
  []
  (let [x 20000000000
        q qty                                        ; 1000000001500
        qx (+ q x)                                   ; 1000020001500
        half 500400                                  ; rotation point
        seed (-> (seed 0)
                 (deposit! 3 i53-max)
                 (assoc-in [:oracle 1] level))]
    (loop [ex seed, h 2, k 0, prev2 nil, prev1 nil, marks {}, max-abs-coll 0]
      (let [k' (inc k)
            taker (if (<= k' half) 2 3)
            ;; taker sells on odd k, buys on even k (k=1: open short q)
            t-sell? (odd? k')
            ;; first fill of each taker opens from flat with q (a qx-sized
            ;; open would make the NEXT fill land exactly on 0 — the REDUCING
            ;; branch, whose (* entry-notional closed) overflows the JVM);
            ;; every later fill is qx and crosses zero (FLIP branch).
            fill (if (or (= k' 1) (= k' (inc half))) q qx)
            ex' (cross! ex h
                        (if t-sell? 0 1)         ; maker rests opposite side
                        (if t-sell? 1 0)
                        taker fill)
            c (max (fx/abs* (coll ex' 2)) (fx/abs* (coll ex' 3)))
            f (fees ex')]
        (when (zero? (mod k' 50000))
          (binding [*out* *err*]
            (println "walk-progress k=" k' "fees=" f "coll2=" (coll ex' 2)
                     "coll3=" (coll ex' 3))))
        (if (> f i53-max)
          {:probe "honest-walk (fees 0, JVM only)"
           :crosses k' :fees f
           :marks {(- k' 2) prev2 (- k' 1) prev1 k' (snap ex' k')}
           :max-abs-coll max-abs-coll}
          (recur ex' (+ h 2) k' prev1 (snap ex' k') marks
                 (max max-abs-coll c)))))))

(defn line-threw
  "Same shape as falsify-6..10's: show what threw, where, with what value."
  [probe e]
  (str "probe " probe
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header []
  "falsify-11: does the sum-unchecked :fees-collected accumulation (clearing.cljc:410/560) cross 2^53 unchecked?")
