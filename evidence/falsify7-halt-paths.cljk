;; falsify-7 harness — does the halt need an out-of-domain value at all?
;;
;; Claim under test (falsify-6 verdict + maturity.md OPEN red): the halt path
;; `validate passes -> fx/check throws inside apply-block` was demonstrated
;; with amount >= 2^53. Two things were left unmeasured:
;;   (a) does :withdraw behave like :deposit (halt), as the OPEN entry's
;;       "…/:withdraw … i53 上限がない" phrasing implies?
;;   (b) is an out-of-domain INPUT required — or can two fully in-domain,
;;       validate-passing orders halt the chain when their fill's notional
;;       (level x qty) crosses i53 inside apply-tx :order's fill reduce?
;;
;; Same rules as falsify-6: NO try/catch in this file (nbb load-string does
;; not resolve reader conditionals); the drivers own catching and both print
;; via `line` / `line-threw` so the stdout dumps stay comparable. Nothing in
;; src/ or test/ is touched.
(ns falsify7-halt-paths
  (:require [torihiki.state :as st]
            [torihiki.api :as api]
            [torihiki.fixed :as fx]))

(def i53-max 9007199254740991)          ; 2^53 - 1, in domain
(def pow53 9007199254740992)            ; 2^53, out of domain
(def flag-reduce-only 4)

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1}
                    :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

(defn seed-collateral!
  "Apply one in-domain deposit (i53-max, measured applied in falsify-6) so
  free collateral is maximal for the withdraw probes."
  [ex]
  (st/apply-block ex {:height 1 :ts 1000
                      :txs [{:tx :deposit :account 1 :amount i53-max}]}))

;; ── probes (each returns a plain map; drivers format) ───────────────────────

(defn probe-withdraw
  "validate + apply ONE :withdraw of 2^53 (out of domain). cl/withdraw has no
  fx/check and gates on free-collateral — prediction under test: no-op, not
  throw, i.e. the halt path does NOT generalize to :withdraw."
  [seeded?]
  (let [ex (cond-> (fresh) seeded? seed-collateral!)
        tx {:tx :withdraw :account 1 :amount pow53}
        v (api/validate ex tx)]
    (if v
      {:probe (str "withdraw-2^53 seeded=" seeded?) :outcome "rejected"
       :validate v :detail (pr-str v)}
      (let [ex' (st/apply-block ex {:height 2 :ts 1001 :txs [tx]})
            coll (get-in ex' [:clearing :accounts 1 :collateral] 0)]
        {:probe (str "withdraw-2^53 seeded=" seeded?)
         :outcome "applied"
         :detail (str "collateral=" coll
                      " changed=" (not= coll (if seeded? i53-max 0))
                      " claims=" (pr-str (:withdrawals ex'))
                      " state-root=" (str (st/state-root ex')))}))))

(defn probe-order-oob
  "validate + apply ONE :order with qty 2^53 (out of domain), flags 0 or
  reduce-only. flags 0 prediction under test: book.cljc:500
  (fx/check :place-qty qty) throws. reduce-only prediction: clamped to 0 by
  cl/reducing-qty (flat account) -> (pos? qty) false -> no-op."
  [flags]
  (let [ex (fresh)
        tx {:tx :order :account 1 :market 1 :side 0 :level 10 :qty pow53
            :flags flags}
        v (api/validate ex tx)]
    (if v
      {:probe (str "order-qty-2^53 flags=" flags) :outcome "rejected"
       :validate v :detail (pr-str v)}
      (let [ex' (st/apply-block ex {:height 2 :ts 1001 :txs [tx]})]
        {:probe (str "order-qty-2^53 flags=" flags)
         :outcome "applied"
         :detail (str "state-root=" (str (st/state-root ex')))}))))

(defn probe-indomain-selfhalt
  "Two FULLY in-domain, validate-passing orders. Block 2: account 1 rests a
  sell of i53-max at level 2. Block 3: account 2's buy of i53-max at level 2
  crosses it. The fill's notional = 2 * i53-max > i53-max, and
  fx/abs* (fx/notional level qty) runs INSIDE apply-tx :order's fill reduce —
  AFTER bk/place! has already consumed the maker's queue in place. Prediction
  under test: apply-block throws (chain halt) with no out-of-domain input."
  []
  (let [ex0 (fresh)
        rest-tx {:tx :order :account 1 :market 1 :side 1 :level 2
                 :qty i53-max :flags 0}
        take-tx {:tx :order :account 2 :market 1 :side 0 :level 2
                 :qty i53-max :flags 0}
        vr (api/validate ex0 rest-tx)
        vt (api/validate ex0 take-tx)
        ex1 (st/apply-block ex0 {:height 2 :ts 1001 :txs [rest-tx]})]
    {:probe "in-domain-cross halt"
     :validate-rest vr :validate-take vt
     :block2 "applied"
     :block2-ex ex1
     :block2-detail (str "state-root=" (str (st/state-root ex1)))
     ;; drivers apply :take-tx against :block2-ex (the book holding the
     ;; resting order), not a fresh exchange — a fresh book would just rest
     ;; the buy and measure nothing (2026-09-04 first-run driver bug).
     :take-tx take-tx}))

(defn line-threw
  "Same shape as falsify-6's: show validate PASSED before the throw."
  [probe v e]
  (str "probe " probe
       " validate " (pr-str v)
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header []
  "falsify-7: does the validate-passing chain halt need an out-of-domain input?")
