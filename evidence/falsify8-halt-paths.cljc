;; falsify-8 harness — does cumulative in-domain deposit state escape i53?
;;
;; Hypothesis (evidence/2026-09-04-falsify-8-cumulative-deposit-overflow-notrun.md):
;; planned fix (a) — per-tx :bad-amount i53 cap on :deposit — is insufficient.
;; cl/deposit (clearing.cljc:714–726) fx/checks only the incoming amount (:721),
;; then credits (fnil + 0) with NO check on the sum (:726). Two in-domain
;; deposits of i53-max each pass fix (a) but leave collateral = 2×i53-max in
;; state. Open question for the measurement: which fx/check site fires FIRST
;; when the escaped collateral is next consumed (candidates per hypothesis:
;; fill-time equity/margin, :notional consumers, :deficit at clearing.cljc:711)?
;;
;; Same rules as falsify-6/7: NO try/catch in this file (nbb load-string does
;; not resolve reader conditionals); the drivers own catching and both print
;; via `line` / `line-threw` so the stdout dumps stay comparable. Nothing in
;; src/ or test/ is touched.
(ns falsify8-halt-paths
  (:require [torihiki.state :as st]
            [torihiki.api :as api]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)          ; 2^53 - 1, in domain (passes fix (a))

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1}
                    :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

(defn- deposit-block
  "One per-tx in-domain deposit of i53-max (passes fix (a)'s cap)."
  [ex i]
  (st/apply-block ex {:height i :ts (+ 1000 i)
                      :txs [{:tx :deposit :account 1 :amount i53-max}]}))

(defn- collateral [ex]
  (get-in ex [:clearing :accounts 1 :collateral] 0))

(defn- state2 []
  "State carrying collateral = 2×i53-max (each deposit individually in domain)."
  (deposit-block (deposit-block (fresh) 1) 2))

;; ── probes (each returns a plain map; drivers format) ───────────────────────

(defn probe-deposit-steps
  "Deposit i53-max in 3 consecutive blocks. Prediction under test: no throw at
  any step (cl/deposit never checks the sum), collateral walks 1×/2×/3× i53-max."
  []
  (let [ex1 (deposit-block (fresh) 1)
        ex2 (deposit-block ex1 2)
        ex3 (deposit-block ex2 3)]
    {:probe "deposit-i53max x3 per-block"
     :coll1 (collateral ex1) :coll2 (collateral ex2) :coll3 (collateral ex3)
     :root3 (str (st/state-root ex3))}))

(defn probe-validate-2nd
  "api/validate of the SECOND i53-max deposit against the 1-deposit state.
  Prediction: passes (integer?/pos? only) — and would pass fix (a) too."
  []
  (let [ex1 (deposit-block (fresh) 1)]
    {:probe "validate-2nd-deposit-i53max"
     :validate (api/validate ex1 {:tx :deposit :account 1 :amount i53-max})}))

(defn probe-post-order
  "From the 2×i53-max state, account 1 rests a small in-domain sell.
  Candidate first-firing site #1: place/fill-path fx/check consuming equity
  or collateral. Reports state before, outcome after."
  []
  (let [ex2 (state2)
        tx {:tx :order :account 1 :market 1 :side 1 :level 2 :qty 1 :flags 0}
        v (api/validate ex2 tx)]
    (if v
      {:probe "post-2dep order rest qty=1" :outcome "rejected"
       :validate v :detail (pr-str v)}
      (let [ex3 (st/apply-block ex2 {:height 3 :ts 1003 :txs [tx]})]
        {:probe "post-2dep order rest qty=1" :outcome "applied"
         :detail (str "state-root=" (str (st/state-root ex3)))}))))

(defn probe-post-cross
  "From the 2×i53-max state (account 1 collateral-overflowed), account 1's
  order crosses: rest sell qty=1 (block 3), account 2 buy qty=1 level 2
  (block 4) — the fill reduce runs fx/notional and account-1-side PnL/margin
  arithmetic over the overflowed collateral. Candidate first-firing site #2."
  []
  (let [ex2 (state2)
        rest-tx {:tx :order :account 1 :market 1 :side 1 :level 2 :qty 1 :flags 0}
        take-tx {:tx :order :account 2 :market 1 :side 0 :level 2 :qty 1 :flags 0}
        ex3 (st/apply-block ex2 {:height 3 :ts 1003 :txs [rest-tx]})
        vt (api/validate ex3 take-tx)]
    (if vt
      {:probe "post-2dep cross fill" :outcome "rejected"
       :validate vt :detail (pr-str vt)}
      (let [ex4 (st/apply-block ex3 {:height 4 :ts 1004 :txs [take-tx]})]
        {:probe "post-2dep cross fill" :outcome "applied"
         :detail (str "state-root=" (str (st/state-root ex4)))}))))

(defn probe-post-withdraw
  "From the 2×i53-max state, withdraw 1. free-collateral (clearing.cljc)
  computes over the overflowed collateral — candidate first-firing site #3."
  []
  (let [ex2 (state2)
        tx {:tx :withdraw :account 1 :amount 1}
        v (api/validate ex2 tx)]
    (if v
      {:probe "post-2dep withdraw 1" :outcome "rejected"
       :validate v :detail (pr-str v)}
      (let [ex3 (st/apply-block ex2 {:height 3 :ts 1003 :txs [tx]})]
        {:probe "post-2dep withdraw 1" :outcome "applied"
         :detail (str "collateral=" (collateral ex3)
                      " state-root=" (str (st/state-root ex3)))}))))

(defn probe-post-deficit-deposit
  "Candidate first-firing site #4 — :deficit at clearing.cljc:711. Build an
  account with a deficit via settle-debt semantics is not directly reachable;
  instead probe the deposit path's OWN fx/check under overflow: a 4th deposit
  onto the 3×i53-max state (:deposit checks only the incoming amount →
  prediction: no throw, sum keeps escaping)."
  []
  (let [ex3 (deposit-block (state2) 3)
        ex4 (deposit-block ex3 4)]
    {:probe "4th deposit onto 3xi53max"
     :coll3 (collateral ex3) :coll4 (collateral ex4)
     :root4 (str (st/state-root ex4))}))

(defn line-threw
  "Same shape as falsify-6/7's: show what threw, where, with what value."
  [probe e]
  (str "probe " probe
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header []
  "falsify-8: does cumulative IN-DOMAIN deposit state escape i53 (fix (a) insufficient)?")
