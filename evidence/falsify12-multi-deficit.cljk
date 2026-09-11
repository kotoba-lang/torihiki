;; falsify-12 harness — does the sum-unchecked :deficit accumulation reach a
;; 2^53+1 crossing through REAL per-block liquidation sweeps of MULTIPLE
;; accounts (the unmeasured last accum-class site)?
;;
;; Hypothesis (evidence/notrun-2026-09-04-1245.md, registered pre-run):
;; cl/settle-deficit (clearing.cljc:711) fx/checks each incoming delta
;; ((fx/check :deficit (- c))) but accumulates via (fnil + 0) with NO check
;; on the sum — the falsify-8/9 class. falsify-9 measured ONE account with
;; synthetic-seeded deltas (direct settle-deficit calls). The unmeasured
;; parts this harness measures:
;;   (1) the FULL real path — end-of-block sweep (state.cljc:1189) through
;;       scan/liquidate/settle-deficit — on multiple underwater accounts at
;;       once (do real multi-account liquidations leave per-account deficits
;;       that already diverge the roots?), and
;;   (2) repeated real sweeps of the SAME account across blocks (cooldown
;;       30 logical seconds) accumulating its :deficit toward 2^53.
;;
;; Harness bring-up notes (measured en route, recorded for the verdict):
;;  - the market must come from cl/market: a bare {:id ..} map has no
;;    :maintenance-margin-rate and NOBODY is ever liquidatable;
;;  - the book needs real two-sided depth (mark impact-mid + liquidation
;;    take-fn read it) and the crash goes through the REAL `:oracle` tx so
;;    reprice! (state.cljc:367) recomputes `:marks` — the sweep (state.cljc
;;    :1072) reads `:marks`, NOT the oracle, and skips a market with no mark;
;;  - the walk revealed an i53-halt DISCOVERED on the way: an account
;;    collateralized at −i53-max (deltas summing to 2×/3× i53-max) throws
;;    fx/check :deficit at clearing.cljc:711 INSIDE the sweep — i.e. the
;;    accumulator's delta check itself is a halt gate for a state the
;;    falsify-8-class overflow already produced. That is why probe C seeds
;;    d = 9007190000000000 (sum = 3×d < i53-max × 3 is still over i53…
;;    measured: 3×d = 27021570000999000 > i53-max, and each DELTA d is
;;    in-domain) — the ACCUMULATED deficit crosses 2^53 at k2 with zero
;;    throws, and would halt at the NEXT delta if the crossing left a
;;    non-reversible value (the falsify-6/7 shape now reached through the
;;    sweep). Seeded per falsify-9's labelled method.
;;
;;   probe A (both runtimes) — three long accounts (4/6/8), collateral 1e6
;;     each, entry ≈ level 1000, oracle tx 1000→1; 60 empty blocks. The
;;     sweep liquidates slice by slice through vault/ADL; deficits per
;;     account per sampled block, with roots. NO seeding.
;;   probe B (both runtimes) — same path, collateral 1e9 each (real losses
;;     ~1e9, in-domain i53-scale). Roots compared per block.
;;   probe C (both runtimes) — the falsify-9 crossing completed through the
;;     REAL sweep: per block, the state a fresh liquidation leaves
;;     (collateral −d, position open — the labelled falsify-9 method) is
;;     re-seeded on accounts 4/6/8, then the REAL end-of-block sweep runs
;;     settle-deficit with an in-domain delta of d each. Per-account
;;     deficit: k1 d (odd, reversible), k2 2×d (even > 2^53, reversible),
;;     k3 3×d (odd > 2^54, DOUBLE-IRREVERSIBLE) → roots match k1..k2,
;;     diverge at k3 — zero throws predicted.
;;
;; Same rules as falsify-6..11: NO try/catch in this file (nbb load-string
;; does not resolve reader conditionals); the drivers own catching and both
;; print via `line` / `line-threw` so stdout dumps stay comparable. Nothing
;; in src/ or test/ is touched.
(ns falsify12-multi-deficit
  (:require [torihiki.state :as st]
            [torihiki.clearing :as cl]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)
;; seeded liquidation-loss delta: in-domain, double-exact, ODD (the seed
;; −9007190000000001 + measured stage-1 loss 999000 = 9007190000999001).
;; The accumulator then walks: k1 (odd < 2^53, reversible), k2 2× (even in
;; [2^53, 2^54) — the reversible even grid), k3 3× = 27021570002997003
;; (ODD ≥ 2^53 → double-IRREVERSIBLE → nbb rounds → root divergence).
(def d 9007190000000001)

(defn fresh []
  (st/new-exchange {:market (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                    :book-opts {:n-levels 65536 :cap 65536 :ev-cap 65536}}))

(defn- deficit [ex acct]
  (get-in ex [:clearing :accounts acct :deficit] 0))
(defn- coll [ex acct]
  (get-in ex [:clearing :accounts acct :collateral] 0))

(defn- snap [ex k]
  {:k k
   :d4 (deficit ex 4) :d6 (deficit ex 6) :d8 (deficit ex 8)
   :c4 (coll ex 4) :c6 (coll ex 6) :c8 (coll ex 8)
   :root (str (st/state-root ex))})

(defn- seed-insolvent
  "The state a fresh liquidation leaves, per account: collateral at −d
  (fresh liquidation loss the waterfall could not cover) and a live
  position (so the sweep's scan finds the account). Positions are closed
  by the sweep's stage-2 transfer; the account then settles its deficit."
  [ex acct]
  (-> ex
      (assoc-in [:clearing :accounts acct :collateral] (- d))
      (assoc-in [:clearing :accounts acct :positions 1]
                {:size 1000 :entry-notional 1000000 :isolated nil})))

(defn- open-longs
  "Build: oracle 1000; account 2 rests a 3S ask; accounts 4/6/8 each cross
  S long at level 1000 through REAL apply-tx :order blocks. The crash then
  goes through the REAL `:oracle` tx so reprice! recomputes the mark."
  [S C]
  (let [ex0 (-> (fresh)
                (assoc-in [:clearing :accounts 2 :collateral] (* 4 C))
                (assoc-in [:clearing :accounts 4 :collateral] C)
                (assoc-in [:clearing :accounts 6 :collateral] C)
                (assoc-in [:clearing :accounts 8 :collateral] C))
        ex1 (st/apply-block ex0 {:height 1 :ts 100
                                 :txs [{:tx :order :account 2 :market 1
                                        :side 1 :level 1000 :qty (* 3 S) :flags 0}]})
        ex2 (st/apply-block ex1 {:height 2 :ts 200
                                 :txs [{:tx :order :account 4 :market 1
                                        :side 0 :level 1000 :qty S :flags 0}]})
        ex3 (st/apply-block ex2 {:height 3 :ts 300
                                 :txs [{:tx :order :account 6 :market 1
                                        :side 0 :level 1000 :qty S :flags 0}]})
        ex4 (st/apply-block ex3 {:height 4 :ts 400
                                 :txs [{:tx :order :account 8 :market 1
                                        :side 0 :level 1000 :qty S :flags 0}]})
        ex5 (st/apply-block ex4 {:height 5 :ts 500
                                 :txs [{:tx :oracle :market 1 :price 1}]})]
    ex5))

(defn- walk
  "`n` more empty blocks (each +100 ts, well past the 30-logical-second
  cooldown) so the end-of-block sweep slices every block. Snap at heights
  in `ks`."
  [ex n ks]
  (loop [ex ex, h 6, acc []]
    (if (> h (+ 5 n))
      acc
      (let [ex' (st/apply-block ex {:height h :ts (* 100 h) :txs []})
            acc' (if (some (fn [k] (= k h)) ks)
                   (conj acc [h (snap ex' h)])
                   acc)]
        (recur ex' (inc h) acc')))))

(defn probe-real-sweep-walk
  "probe A: S=100000 lots, C=1e6. Real entry ≈ level 1000; after the crash
  equity ≈ 1e6 − 999×~1e5×(entry/1000) ≪ mm → insolvent on the first sweep.
  ADL counterparty is account 2 (short 3S, huge profit at mark 1)."
  []
  (let [ex0 (open-longs 100000 1000000)
        states (walk ex0 60 [6 7 8 9 13 21 36 51 65])
        last-snap (peek (vec states))]
    {:probe "real-sweep-walk S=100000 C=1e6 oracle-tx 1000->1"
     :snaps states :last last-snap}))

(defn probe-deficit-crossing
  "probe B: S=1,000,000 lots (real entry notional ~1e9 per account),
  C=1e9 — total real loss per account ≈ 999e6, so the accounts go bankrupt
  for real and the sweep must leave deficits ~1e9 (in-domain) per account.
  Root comparison per sampled block across runtimes. NO collateral seeding."
  []
  (let [ex0 (open-longs 1000000 1000000000)
        states (walk ex0 60 [6 7 8 9 13 21 36 51 65])
        last-snap (peek (vec states))]
    {:probe "deficit-crossing S=1e6 C=1e9 real losses ~1e9"
     :snaps states :last last-snap}))

(defn probe-deficit-accum
  "probe C (falsify-9 crossing through the REAL sweep): re-seed the state a
  fresh liquidation leaves (collateral −d, live position) on accounts
  4/6/8, then run the REAL end-of-block sweep each block — settle-deficit
  receives the REAL fx/check'd delta d per block. 3×d = 27021570000999000
  is odd and ≥ 2^54 → double-irreversible on nbb at k3."
  []
  (let [fresh-marked (fn [] (-> (fresh)
                                (assoc-in [:marks 1] 1)
                                (assoc-in [:oracle 1] 1)))
        reseed (fn [ex] (-> ex
                            (assoc-in [:clearing :accounts 4 :collateral] (- d))
                            (assoc-in [:clearing :accounts 4 :positions 1]
                                      {:size 1000 :entry-notional 1000000 :isolated nil})))
        states (loop [ex (reseed (fresh-marked)), h 1, acc []]
                 (let [ex' (st/apply-block ex {:height h :ts (* 100 h) :txs []})
                       acc' (conj acc [h (snap ex' h)])]
                   (if (= h 3)
                     acc'
                     (recur (reseed ex') (inc h) acc'))))]
    {:probe "deficit-accum 3x d=9007190000000000 through real sweep"
     :snaps states}))

(defn line-threw
  "Same shape as falsify-6..11's: show what threw, where, with what value."
  [probe e]
  (str "probe " probe
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header []
  "falsify-12: does :deficit accumulation cross 2^53 through REAL multi-account liquidation sweeps?")
