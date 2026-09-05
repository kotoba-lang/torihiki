;; falsify-6 harness — does a >i53 amount halt the chain through a
;; validate-passing transaction?
;;
;; Claim under test (api.cljc ns docstring + state.cljc apply-block
;; docstring): "validation is total: every transaction either passes
;; `validate` and is applied, or fails it and is recorded as a
;; rejection. Nothing in between, and nothing that throws."
;;
;; api/validate for :deposit checks only (integer? amount) (pos? amount)
;; — no i53 bound. cl/deposit calls (fx/check :deposit amount), which
;; THROWS outside the i53 domain. If so, one signed tx with amount
;; 2^53 stops every validator: the exact "stop the chain with a typo"
;; liveness failure the docs say cannot happen.
;;
;; No try/catch lives in THIS file: nbb's load-string does not process
;; reader conditionals (2026-09-04, new nbb trap beyond the three in
;; falsify-4), and catch syntax is reader-conditional territory. The
;; drivers own the catching — JVM `catch Exception`, nbb `catch :default`
;; — and format via the same `line` / `line-threw` here, so the two
;; stdout dumps stay comparable.
;;
;; No code in src/ or test/ is touched. Invoked:
;;   JVM:  clojure -M -e '(load-file "evidence/falsify6-jvm-driver.clj")'
;;   nbb:  nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/falsify6-driver.cljs
(ns falsify6-i53
  (:require [torihiki.state :as st]
            [torihiki.api :as api]
            [torihiki.fixed :as fx]))

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1}
                    :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

;; 2^53+1 is built arithmetically, not as a literal, so the SAME source
;; expression runs on both runtimes. JS rounds (+ 9007199254740992 1)
;; back to 9007199254740992 at evaluation — printed below via :value so
;; the read-time divergence is itself part of the evidence.
(def amounts
  [["i53-max"  9007199254740991]        ; last representable-exact value
   ["2^53"     9007199254740992]        ; first out-of-domain integer
   ["2^53+1"   (+ 9007199254740992 1)]])

(defn probe
  "Validate, then apply, ONE deposit tx of `amount`. Returns a result map
  when application succeeds; THROWS whatever apply-block throws when the
  tx passed validation but the engine cannot store the value."
  [label amount]
  (let [ex (fresh)
        tx {:tx :deposit :account 1 :amount amount}
        v (api/validate ex tx)]
    (if v
      {:label label :value amount :in-domain (fx/in-domain? amount)
       :validate v :outcome "rejected" :detail (pr-str v)}
      (let [ex' (st/apply-block ex {:height 1 :ts 1000 :txs [tx]})]
        {:label label :value amount :in-domain (fx/in-domain? amount)
         :validate nil :outcome "applied"
         :detail (str "collateral=" (get-in ex' [:clearing :accounts 1 :collateral] 0)
                      " state-root=" (str (st/state-root ex'))
                      " rejected=" (pr-str (:rejected ex')))}))))

(defn line
  "One stdout line for a probe that completed."
  [{:keys [label value in-domain validate outcome detail]}]
  (str "amount " label
       " value " (pr-str value)
       " in-domain " (pr-str in-domain)
       " validate " (pr-str validate)
       " -> " outcome
       " " detail))

(defn line-threw
  "One stdout line for a probe that threw out of apply-block. validate is
  re-run against a fresh exchange so the line still shows that the tx
  PASSED validation before the engine threw on it."
  [[label amount] e]
  (str "amount " label
       " value " (pr-str amount)
       " in-domain " (pr-str (fx/in-domain? amount))
       " validate " (pr-str (api/validate (fresh) {:tx :deposit :account 1 :amount amount}))
       " -> threw"
       " msg=" (ex-message e)
       " where=" (pr-str (:where (ex-data e)))
       " value=" (pr-str (:value (ex-data e)))))

(defn header [] "falsify6-i53: deposit amounts at the i53 boundary through validate+apply-block")
