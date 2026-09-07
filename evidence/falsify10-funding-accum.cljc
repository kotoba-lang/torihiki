;; falsify-10 harness — funding accumulation (:funding-residue) crosses 2^53 silently.
;;
;; Hypothesis (evidence/2026-09-04-falsify-10-funding-residue-accum-overflow-notrun.md,
;; registered pre-run): fnd/apply-funding (funding.cljc:130-140) fx/checks each
;; payment p through fx/mul-rate (in-domain), but accumulates BOTH
;; (update :funding-residue (fnil + 0) p) and
;; (update-in [:accounts acct :collateral] (fnil - 0) p) with NO check on the
;; running sum — the falsify-8 (collateral) / falsify-9 (:deficit) shape:
;; delta-checked, sum-unchecked. :funding-residue IS under the state root
;; (state.cljc:1603 encode-clearing-totals), so a non-invertible residue is a
;; direct silent cross-runtime root divergence.
;;
;; Scale analysis (mul-rate product cap, falsify-9): one settle's payment is
;; bounded by (* amount rate) < 2^63 (JVM long overflow). With the REAL
;; empty-accumulator hourly rate = 12500 (interest 1bp/8h -> fdiv 1e5 8; the
;; 4e7 cap is not reached), payment p = floor(notional x 12500 / 1e9)
;; = floor(notional / 80000). Choosing notional = 80000*p + 40000 puts the
;; product at 1e9*p + 5e8 — half a billion away from the floor-division
;; boundary, so the double rounding error (<=1024 at ~9.2e18) cannot move the
;; quotient: p is bit-identical on JVM and nbb even though the product is
;; between 2^53 and 2^63. p = 9000000001 (odd).
;;
;; probe A (both runtimes) — residue seeded to R0 = 2^53 + 1 - 2p
;; = 8989199254740991 (odd, < 2^53, double-exact: the shape ~979k honest
;; funding hours would leave — synthetic TIME compression, mechanism
;; untouched, same method as falsify-9's seeded collateral):
;;   settle 1: R1 = 8998199254740992 (even, < 2^53)  -> roots EQUAL predicted
;;   settle 2: R2 = 9007199254740993 = 2^53 + 1 (ODD, >= 2^53)
;;             -> JVM exact ...993, nbb rounds to ...992 -> ROOT DIVERGES,
;;                zero throws predicted
;;   settle 3: R3 = R2 + p lands on an even reversible value -> possible
;;             re-convergence (falsify-8 rule ii), settle 4 diverges again.
;; Collateral stays below 2^53 in magnitude throughout (exact on both).
;;
;; probe B (JVM only — the 1M apply-block loop is the scale falsify-9 judged
;; unrealistic for nbb): honest walk from residue 0, 1,000,801 real settles.
;; Predicted: residue(1000800) = 9007200001000800 (even, reversible),
;; residue(1000801) = 9007209001000801 (ODD -> non-invertible on nbb),
;; zero throws — the crossing is reachable from in-domain inputs alone.
;;
;; Same rules as falsify-6/7/8/9: NO try/catch in this file (nbb load-string
;; does not resolve reader conditionals); the drivers own catching and print
;; via `line-threw`. Nothing in src/ or test/ is touched.
(ns falsify10-funding-accum
  (:require [torihiki.state :as st]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)

(def p 9000000001)                      ; one settle's payment, ODD
(def pos-size 720000000120000)          ; oracle 1 x size = notional
(def r0 9007181254740991)               ; 2^53 + 1 - 2p, odd, < 2^53

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1}
                    :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

(defn- seed
  "SYNTHETIC seeding of the residue a ~979k-hour honest run would leave
  (time compression only), plus a long position and oracle price. The
  accumulator and apply path are unmodified production code."
  [residue0]
  (-> (fresh)
      (assoc-in [:clearing :accounts 7 :collateral] i53-max)
      (assoc-in [:clearing :accounts 7 :positions 1 :size] pos-size)
      (assoc-in [:oracle 1] 1)
      (assoc-in [:clearing :funding-residue] residue0)))

(defn- settle [ex]
  (st/apply-tx ex {:tx :funding-settle :market 1}))

(defn- residue [ex]
  (get-in ex [:clearing :funding-residue] 0))
(defn- coll [ex]
  (get-in ex [:clearing :accounts 7 :collateral] 0))
(defn- rate [ex]
  (get-in ex [:last-funding-rate 1]))

(defn- snap [ex k]
  {:k k :residue (residue ex) :coll (coll ex) :rate (rate ex)
   :root (str (st/state-root ex))})

(defn probe-payment-identity
  "Show p is fx/check'd in-domain and identical by construction: the product
  sits 5e8 away from the floor-division boundary."
  []
  (let [notional (fx/notional 1 pos-size)
        product (* notional 12500)]
    {:probe "payment-identity"
     :notional notional
     :product product
     :prod-lt-2-63 (< product 9223372036854775807)
     :boundary-dist (mod product 1000000000)
     :p (fx/mul-rate notional 12500)
     :p-expected p}))

(defn probe-residue-seeded-crossing
  "Four REAL funding-settle applies over the seeded state. Prediction: no
  throw anywhere; roots equal at settle 1, DIVERGE at settle 2 (residue
  2^53+1, odd), possibly re-converge at settle 3, diverge again at settle 4."
  []
  (let [ex1 (settle (seed r0))
        ex2 (settle ex1)
        ex3 (settle ex2)
        ex4 (settle ex3)]
    {:probe "residue-seeded-crossing (R0 = 2^53+1-2p)"
     :s1 (snap ex1 1) :s2 (snap ex2 2) :s3 (snap ex3 3) :s4 (snap ex4 4)}))

(defn probe-honest-walk
  "JVM-only: from residue 0, `n` REAL funding-settle applies at the real
  empty-accumulator rate. Records checkpoints around the predicted 2^53
  crossing (k=1000800 even / k=1000801 odd) and the extreme collateral."
  [n]
  (loop [k 0, ex (seed 0), marks {}, max-abs-coll 0]
    (if (>= k n)
      {:probe "honest-walk (residue 0, JVM only)"
       :settled k :marks marks :max-abs-coll max-abs-coll}
      (let [ex' (settle ex)
            k' (inc k)
            c (fx/abs* (coll ex'))
            marks' (if (contains? #{1000799 1000800 1000801} k')
                     (assoc marks k' (snap ex' k'))
                     marks)]
        (recur k' ex' marks' (max max-abs-coll c))))))

(defn line-threw
  "Same shape as falsify-6..9's: show what threw, where, with what value."
  [probe e]
  (str "probe " probe
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header []
  "falsify-10: does the sum-unchecked funding accumulation (:funding-residue, funding.cljc:138) cross 2^53 unchecked?")
