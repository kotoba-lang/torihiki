(ns torihiki.state
  "The exchange as a state machine: `(state, ordered-txs) -> (state', events)`.

  This is the seam that makes torihiki a chain's execution layer rather than
  an exchange server. Consensus decides the ORDER of transactions and nothing
  else; this namespace decides what that order MEANS. Every validator that
  applies the same block to the same prior state must reach a byte-identical
  successor, so everything here is deterministic by construction:

  - no wall clock — `:ts` arrives in the block header
  - no randomness
  - no floating point
  - no unordered iteration: every fold over accounts sorts first

  ## What a state root is here, and what it is not

  `state-root` is a 32-bit FNV-1a checksum over a canonical serialisation. It
  is enough to catch divergence between two replays, which is what it is for
  and what the tests use it for. It is NOT a cryptographic commitment: it is
  not collision-resistant, and it does not support proofs about parts of the
  state. A production chain needs SHA-256 over a canonical encoding, and if
  light clients ever need to prove individual balances it needs an
  authenticated tree rather than a flat digest. Saying so here is cheaper
  than letting someone discover it later."
  (:require [torihiki.fixed :as fx]
            [torihiki.slab :as slab]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.funding :as fnd]
            [torihiki.liquidation :as liq]))

;; ── reserved account ids ────────────────────────────────────────────────────
;;
;; Account and market ids are INTEGERS, not keywords. The book stores an
;; order's owner in a long slab, so a keyword owner cannot survive a round
;; trip through it — and `clojure.core/hash` on a keyword is not guaranteed
;; to agree between the JVM and JS, which would put a platform-dependent
;; value straight into the state root. Integers dodge both problems.

(def ^:const vault-account -1)      ; the backstop vault
(def ^:const liquidator-account -2) ; the synthetic taker liquidations trade as

;; ── construction ────────────────────────────────────────────────────────────

(defn new-exchange
  "An exchange over one market. Multi-market is a map of books keyed by market
  id; the single-market case is spelled out here because it is what the tests
  and the benchmark exercise, and pretending otherwise would be scaffolding
  nobody has run."
  [{:keys [market book-opts] :as _cfg}]
  {:markets {(:id market) market}
   :books {(:id market) (bk/new-book (or book-opts {}))}
   :clearing (assoc (cl/new-state)
                    :insurance-fund 0
                    :backstop-vault vault-account
                    :liquidation-clock {})
   :oracle {}
   :marks {}
   :funding {(:id market) fnd/empty-accumulator}
   :funding-params fnd/default-params
   :liq-params liq/default-params
   :height 0
   :ts 0})

;; ── transactions ────────────────────────────────────────────────────────────

(defmulti apply-tx
  "Apply one transaction. Dispatches on `:tx`. Every method must be a pure
  function of (exchange, tx) — the book mutates its own arrays in place, but
  that mutation is fully determined by the arguments, so replay reproduces
  it exactly."
  (fn [_ex tx] (:tx tx)))

(defmethod apply-tx :default [ex _tx] ex)

(defmethod apply-tx :deposit
  [ex {:keys [account amount]}]
  (update ex :clearing cl/deposit account amount))

(defmethod apply-tx :withdraw
  [ex {:keys [account amount]}]
  (update ex :clearing cl/withdraw account amount (:marks ex) (:markets ex)))

(defmethod apply-tx :order
  [ex {:keys [account market side level qty flags] :or {flags 0}}]
  (let [book (get-in ex [:books market])
        before (bk/event-count book)
        _oid (bk/place! book side level qty flags account)
        fills (drop before (bk/fills book))
        mkt (get-in ex [:markets market])
        fee-rate (get mkt :taker-fee-rate 0)
        maker-rate (get mkt :maker-fee-rate 0)]
    ;; Both sides of every fill are credited here, and in fill order. Crediting
    ;; only the taker and reconciling makers later would let an account's
    ;; margin be evaluated against a position it already holds but has not
    ;; been told about.
    (reduce
     (fn [ex' {:keys [taker-owner maker-owner level qty taker-side]}]
       (let [taker-delta (if (= taker-side bk/bid) qty (- qty))
             maker-delta (- taker-delta)]
         (-> ex'
             (update :clearing cl/apply-fill taker-owner market taker-delta level fee-rate)
             (update :clearing cl/apply-fill maker-owner market maker-delta level maker-rate)
             (assoc-in [:marks market] level))))
     ex
     fills)))

(defmethod apply-tx :cancel
  [ex {:keys [market oid]}]
  (bk/cancel! (get-in ex [:books market]) oid)
  ex)

(defmethod apply-tx :oracle
  [ex {:keys [market price]}]
  ;; Validator-submitted prices are consensus INPUT, so the aggregation that
  ;; turns many submissions into one number has to happen before this point,
  ;; in the block producer. What lands here is already the agreed value.
  (assoc-in ex [:oracle market] price))

(defmethod apply-tx :funding-sample
  [ex {:keys [market]}]
  (let [book (get-in ex [:books market])
        oracle (get-in ex [:oracle market] 0)
        impact (get-in ex [:funding-params :impact-notional])]
    (update-in ex [:funding market] fnd/sample (fnd/premium book oracle impact))))

(defmethod apply-tx :funding-settle
  [ex {:keys [market]}]
  (let [acc (get-in ex [:funding market] fnd/empty-accumulator)
        rate (fnd/hourly-rate acc (:funding-params ex))
        oracle (get-in ex [:oracle market] 0)]
    (-> ex
        (update :clearing fnd/apply-funding market oracle rate)
        (assoc-in [:funding market] fnd/empty-accumulator)
        (assoc-in [:last-funding-rate market] rate))))

(defmethod apply-tx :liquidate
  [ex {:keys [market]}]
  (let [book (get-in ex [:books market])
        mark (get-in ex [:marks market] (get-in ex [:oracle market] 0))
        ;; offering the slice to the real book is what makes stage 1 mean
        ;; something; a take-fn that always refuses would send every
        ;; liquidation straight to the vault
        take-fn (fn [delta _mark]
                  (let [before (bk/event-count book)
                        side (if (pos? delta) bk/bid bk/ask)
                        limit (if (pos? delta) (dec (:n-levels book)) 0)
                        _ (bk/place! book side limit (fx/abs* delta) bk/flag-ioc liquidator-account)
                        fills (drop before (bk/fills book))
                        filled (reduce + 0 (map :qty fills))
                        cost (reduce + 0 (map #(fx/notional (:level %) (:qty %)) fills))]
                    (if (zero? filled)
                      [0 0]
                      [(if (pos? delta) filled (- filled)) (fx/fdiv cost filled)])))]
    (reduce
     (fn [ex' acct]
       (let [r (liq/liquidate (:clearing ex') acct market mark (:ts ex')
                              (:markets ex') (:liq-params ex') take-fn)]
         (assoc ex' :clearing (:state r))))
     ex
     (liq/scan (:clearing ex) market (:marks ex) (:markets ex)))))

;; ── blocks ──────────────────────────────────────────────────────────────────

(defn apply-block
  "Apply an ordered block. The header's `:ts` becomes the state machine's
  logical clock for the duration — nothing below it may consult a real one."
  [ex {:keys [height ts txs]}]
  (let [ex (assoc ex :height height :ts ts)]
    (doseq [[_ book] (:books ex)] (bk/reset-events! book))
    (reduce apply-tx ex txs)))

;; ── state root ──────────────────────────────────────────────────────────────
;;
;; FNV-1a, 32-BIT, on both platforms. The obvious implementation uses the
;; 64-bit variant on the JVM and something narrower in JS, and that is exactly
;; wrong: a state root computed by two different algorithms is not a check on
;; agreement, it is a guarantee of disagreement. 32 bits is what JS can
;; multiply exactly (`Math.imul`), so 32 bits is what both sides use.
;;
;; Values are split into non-negative 32-bit chunks plus a sign bit before
;; mixing, because `bit-and` in ClojureScript coerces through ToInt32 and
;; would silently discard everything above bit 31 of a 53-bit value.

(def ^:const fnv-offset-32 2166136261)
(def ^:const fnv-prime-32 16777619)
(def ^:const two32 4294967296)

(defn- mix32
  "One FNV-1a step over a 32-bit chunk."
  [h x]
  #?(:clj  (bit-and (unchecked-multiply (bit-xor (long h) (long x)) fnv-prime-32)
                    0xFFFFFFFF)
     :cljs (unsigned-bit-shift-right
            (js/Math.imul (bit-xor (bit-or h 0) (bit-or x 0)) fnv-prime-32) 0)))

(defn- mix
  "Fold one i53 value into the digest: low chunk, high chunk, then sign."
  [h v]
  (let [v (or v 0)
        neg (if (neg? v) 1 0)
        a (fx/abs* v)]
    (-> h
        (mix32 (fx/fmod a two32))
        (mix32 (fx/fdiv a two32))
        (mix32 neg))))

(defn- hash-slab
  [h a]
  (let [n (slab/size a)]
    (loop [i 0 h h]
      (if (>= i n) h (recur (inc i) (mix h (slab/get a i)))))))

(defn- hash-clearing
  "Fold the clearinghouse into the digest in SORTED key order. Clojure map
  iteration order is unspecified — folding in map order would make the state
  root depend on insertion history rather than on state, and two validators
  holding identical balances would disagree."
  [h clearing]
  (let [h (mix h (or (:insurance-fund clearing) 0))
        h (mix h (or (:fees-collected clearing) 0))
        h (mix h (or (:funding-residue clearing) 0))]
    (reduce
     (fn [h acct]
       (let [{:keys [collateral positions]} (get-in clearing [:accounts acct])
             h (mix h acct)
             h (mix h (or collateral 0))]
         (reduce (fn [h mkt]
                   (let [p (get positions mkt)]
                     (-> h
                         (mix mkt)
                         (mix (:size p))
                         (mix (:entry-notional p))
                         (mix (or (:isolated p) 0)))))
                 h
                 (sort (keys positions)))))
     h
     (sort (keys (:accounts clearing))))))

(defn state-root
  "A digest of the whole exchange. Equal roots mean two replays agreed; see
  this namespace's docstring for what this is not."
  [ex]
  (let [h (-> fnv-offset-32
              (mix (:height ex))
              (mix (:ts ex)))
        h (reduce (fn [h mkt]
                    (let [b (get-in ex [:books mkt])]
                      (-> h
                          (mix mkt)
                          (mix (get-in ex [:oracle mkt] 0))
                          (mix (get-in ex [:marks mkt] 0))
                          (hash-slab (:lvl-qty b))
                          (hash-slab (:o-qty b))
                          (hash-slab (:o-level b))
                          (hash-slab (:o-owner b)))))
                  h
                  (sort (keys (:books ex))))]
    (hash-clearing h (:clearing ex))))
