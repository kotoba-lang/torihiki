;; falsify-9 harness — the delta-checked / sum-unchecked accumulators.
;;
;; Hypothesis (evidence/2026-09-04-falsify-9-fees-collected-accum-overflow-notrun.md,
;; amended during the run — see the report): NEXT names the unmeasured i53
;; accumulation sites (funding, liquidation, fee pooling, :deficit at
;; clearing.cljc:711) as the falsify-9 candidate class. The measured target
;; here is :deficit: cl/settle-deficit (:711) fx/checks each incoming delta
;; ((fx/check :deficit (- c))) but accumulates via (fnil + 0) with NO check on
;; the sum — the exact shape falsify-8 proved for collateral.
;;
;; Predicted walk (3 settles, each delta in-domain i53-max):
;;   deficit 1x = 9007199254740991 (odd, < 2^53 — double-exact)
;;   deficit 2x = 18014398509481982 (even, in [2^53, 2^54) — even-only grid, exact)
;;   deficit 3x = 27021597764222973 (odd, >= 2^54 — DOUBLE-IRREVERSIBLE)
;;   -> JVM keeps 3x exact (BigInt-free i64: 2.7e16 << 2^63), nbb rounds to the
;;   even grid -> state-root diverges at settle 3 with ZERO throws, falsify-8
;;   class (silent consensus break). A 4th settle should stay divergent (the
;;   nbb-rounded deficit3 propagates).
;;
;; Method: settle-deficit is only reachable in production through the
;; liquidation waterfall (liquidation.cljc:252), which needs a live
;; underwater position. The probe seeds collateral = -i53-max directly between
;; settles (what a fresh liquidation loss would leave) and calls the REAL
;; cl/settle-deficit on a real exchange state — the accumulator itself is
;; unmodified production code. Labelled synthetic-seeded in the report.
;;
;; Bonus probe (measured incidentally on the first JVM run of this falsify):
;; fx/mul-rate computes (* amount rate) BEFORE fx/check — for amount=i53-max,
;; rate=rate-scale that product is 9.007e24 and the JVM throws
;; ArithmeticException "long overflow" (unchecked, inside the fee path), while
;; nbb computes a rounded double and throws fx/check :mul-rate instead. Same
;; tx, two runtimes, two DIFFERENT failure modes.
;;
;; Same rules as falsify-6/7/8: NO try/catch in this file (nbb load-string
;; does not resolve reader conditionals); the drivers own catching and both
;; print via `line` / `line-threw` so stdout dumps stay comparable. Nothing in
;; src/ or test/ is touched.
(ns falsify9-accum-paths
  (:require [torihiki.state :as st]
            [torihiki.clearing :as cl]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1}
                    :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

(defn- deficit [ex acct]
  (get-in ex [:clearing :accounts acct :deficit] 0))
(defn- coll [ex acct]
  (get-in ex [:clearing :accounts acct :collateral] 0))

(defn- seed-loss
  "SYNTHETIC seeding: put collateral at -d exactly (what a fresh liquidation
  loss of d would leave), so the next real settle-deficit has delta d."
  [ex acct d]
  (assoc-in ex [:clearing :accounts acct :collateral] (- d)))

(defn- settle-round
  "One deficit event: seed -d, call the REAL cl/settle-deficit (which takes
  the CLEARING map, as liquidation.cljc:252 calls it)."
  [ex acct d]
  (update (seed-loss ex acct d) :clearing cl/settle-deficit acct))

(defn probe-deficit-walk
  "Four settle rounds of in-domain delta i53-max on account 7. Reports
  deficit / collateral / state-root after each. Prediction: no throw anywhere;
  roots equal through settle 2, DIVERGE at settle 3 (deficit 3xi53-max is odd
  >= 2^54), still divergent at settle 4."
  []
  (let [ex0 (fresh)
        ex1 (settle-round ex0 7 i53-max)
        ex2 (settle-round ex1 7 i53-max)
        ex3 (settle-round ex2 7 i53-max)
        ex4 (settle-round ex3 7 i53-max)
        snap (fn [ex k] {:k k :deficit (deficit ex 7) :coll (coll ex 7)
                         :root (str (st/state-root ex))})]
    {:probe "deficit-walk settle-deficit x4 (delta i53-max)"
     :s1 (snap ex1 1) :s2 (snap ex2 2) :s3 (snap ex3 3) :s4 (snap ex4 4)}))

(defn probe-deficit-validate-path
  "The deltas each pass fx/check :deficit individually — show one settle
  round from the REAL negative-collateral shape and that the accumulated
  deficit then flows into deposit pay-down unchanged (cl/deposit credits
  repaid first). Prediction: all applied, no check on the running deficit sum."
  []
  (let [ex1 (settle-round (fresh) 7 i53-max)
        ex2 (settle-round ex1 7 i53-max)
        ex3 (settle-round ex2 7 i53-max)
        ex4 (update ex3 :clearing cl/deposit 7 1)]
    {:probe "deficit-3x then deposit 1"
     :deficit3 (deficit ex3 7) :deficit-after (deficit ex4 7)
     :coll-after (coll ex4 7) :root (str (st/state-root ex4))}))

(defn probe-mul-rate-limit
  "fx/mul-rate (fixed.cljc:99-103) multiplies BEFORE checking: (* i53-max
  rate-scale) = 9.007e24 overflows i64 on the JVM. Reports the exception
  shape each runtime produces for the SAME call."
  []
  (let [v (fx/mul-rate i53-max 1000000000)]
    {:probe "mul-rate i53-max x rate-scale"
     :value v}))

(defn line-threw
  "Same shape as falsify-6/7/8's: show what threw, where, with what value."
  [probe e]
  (str "probe " probe
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header []
  "falsify-9: do the delta-checked / sum-unchecked accumulators (:deficit clearing.cljc:711) cross 2^53 unchecked?")
