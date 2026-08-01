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

  `state-root` is SHA-256 over a tagged, length-prefixed canonical encoding,
  returned as lowercase hex. It is safe to sign, which matters because
  `torihiki.log` does exactly that.

  It is still a FLAT digest: it commits to the whole state and proves nothing
  about any part of it. A light client that wants to verify one balance
  without replaying the chain needs an authenticated tree, and this is not
  one. Saying so here is cheaper than letting someone discover it later."
  (:require [torihiki.fixed :as fx]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.funding :as fnd]
            [torihiki.liquidation :as liq]
            [torihiki.mark :as mk]
            [torihiki.trigger :as trg]
            [kotoba.bytes.sha256 :as sha]))

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
   ;; `:marks` is DERIVED (torihiki.mark) and is what margin and liquidation
   ;; read. `:last` is the last traded price and exists only to be displayed —
   ;; showing a trader the last print is right, margining them against it is
   ;; how one thin fill liquidates somebody else.
   :marks {}
   :last {}
   :mark-params mk/default-params
   :funding {(:id market) fnd/empty-accumulator}
   :funding-params fnd/default-params
   ;; Conditional orders, per market. They are state: they change what future
   ;; blocks do, so they are committed to by the state root.
   :triggers {}
   :trigger-seq 0
   ;; A fired trigger places an order, which moves the book, which reprices
   ;; the mark, which can arm more triggers. That cascade has to terminate.
   :max-trigger-rounds 8
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

(defn- reprice!
  "Recompute the derived mark for `market` from the book and the oracle.
  Called after anything that can move either — a fill or an oracle update."
  [ex market]
  (let [book (get-in ex [:books market])
        oracle (get-in ex [:oracle market] 0)
        oracle (if (pos? oracle) oracle (get-in ex [:last market] 0))]
    (assoc-in ex [:marks market] (mk/mark-price book oracle (:mark-params ex)))))

(declare fire-triggers apply-tx-order)

(defmethod apply-tx :order
  [ex {:keys [account market side level qty flags] :or {flags 0}}]
  (let [reduce-only? (pos? (bit-and flags bk/flag-reduce-only))
        ;; Reduce-only is clamped, not rejected: a trader closing a position
        ;; often sends slightly more than they hold, and the intent is
        ;; unambiguous. What must not happen is the excess opening a position
        ;; on the other side.
        qty (if reduce-only?
              (cl/reducing-qty (:clearing ex) account market side qty)
              qty)
        ;; A reduce-only order does NOT rest. Enforcing it once at placement is
        ;; only sound if it cannot outlive the position it was checked against
        ;; — a resting one would need re-validating on every position change,
        ;; and a half-enforced flag is worse than a restricted one. Hyperliquid
        ;; does let them rest; this is a stated difference, not an oversight.
        flags (if reduce-only? (bit-or flags bk/flag-ioc) flags)]
    (if (not (pos? qty))
      ex
      (apply-tx-order ex account market side level qty flags))))

(defn- apply-tx-order
  [ex account market side level qty flags]
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
    (-> (reduce
         (fn [ex' {:keys [taker-owner maker-owner level qty taker-side]}]
           (let [taker-delta (if (= taker-side bk/bid) qty (- qty))
                 maker-delta (- taker-delta)]
             (-> ex'
                 (update :clearing cl/apply-fill taker-owner market taker-delta level fee-rate)
                 (update :clearing cl/apply-fill maker-owner market maker-delta level maker-rate)
                 ;; the fill sets the LAST price, never the mark
                 (assoc-in [:last market] level))))
         ex
         fills)
        ;; repriced once per order rather than once per fill: the mark is a
        ;; function of the resulting book, not of each step through it
        (reprice! market)
        (fire-triggers market))))

;; ── conditional orders ──────────────────────────────────────────────────────

(defmethod apply-tx :trigger
  [ex {:keys [account market trigger-price direction order]}]
  (let [id (inc (:trigger-seq ex 0))
        t (trg/trigger {:id id :account account :market market
                        :trigger-price trigger-price :direction direction
                        :order order})]
    (if-not (trg/valid? t)
      ex
      (-> ex
          (assoc :trigger-seq id)
          (update-in [:triggers market] (fnil conj []) t)
          ;; A trigger submitted already past its price fires immediately
          ;; rather than waiting for the next tick. Anything else would leave
          ;; a stop sitting armed-but-unfired through a gap, which is when a
          ;; trader needs it most.
          (fire-triggers market)))))

(defmethod apply-tx :cancel-trigger
  [ex {:keys [market id]}]
  (update-in ex [:triggers market] trg/cancel id))

(defn- fire-triggers
  "Fire every trigger the current mark has armed, then reprice and look again.

  The loop exists because firing changes the book, which changes the mark,
  which can arm more triggers — a stop cascade, which is a real market event
  and not a bug. What would be a bug is not terminating, so the rounds are
  capped; anything still armed at the cap is left for the next transaction.
  Bounded progress beats an unbounded cascade inside one transaction."
  [ex market]
  (let [cap (:max-trigger-rounds ex 8)]
    (loop [ex ex round 0]
      (let [mark (get-in ex [:marks market] 0)
            armed (when (pos? mark) (trg/due (get-in ex [:triggers market] []) mark))]
        (if (or (>= round cap) (empty? armed))
          ex
          ;; remove them BEFORE submitting, so a trigger cannot re-fire itself
          ;; through the cascade it causes
          (let [ex (assoc-in ex [:triggers market]
                             (trg/remaining (get-in ex [:triggers market] []) mark))
                ;; A fired trigger is reduce-only, and the clamping for that
                ;; lives in the `:order` METHOD, not in `apply-tx-order`.
                ;; Passing the flag down to the inner function only sets a bit
                ;; the book ignores — the position check is skipped and a
                ;; stale stop opens a position on the other side. So the clamp
                ;; is applied here explicitly.
                ex (reduce (fn [e t]
                             (let [o (:order t)
                                   q (cl/reducing-qty (:clearing e) (:account t)
                                                      market (:side o) (:qty o))]
                               (if (pos? q)
                                 (apply-tx-order e (:account t) market
                                                 (:side o) (:level o) q
                                                 (bit-or (:flags o 0) bk/flag-ioc))
                                 e)))
                           ex armed)]
            (recur (reprice! ex market) (inc round))))))))

(defmethod apply-tx :cancel
  [ex {:keys [market oid]}]
  (bk/cancel! (get-in ex [:books market]) oid)
  ex)

(defmethod apply-tx :oracle
  [ex {:keys [market price]}]
  ;; Validator-submitted prices are consensus INPUT, so the aggregation that
  ;; turns many submissions into one number has to happen before this point,
  ;; in the block producer. What lands here is already the agreed value.
  (-> ex
      (assoc-in [:oracle market] price)
      (reprice! market)
      (fire-triggers market)))

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
        _ (when-not (pos? mark)
            (throw (ex-info "torihiki.state: refusing to liquidate without a mark"
                            {:market market})))
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
;; SHA-256 over a canonical byte encoding, via `kotoba.bytes.sha256` — pure,
;; synchronous, and identical on the JVM and in JS. It replaced a 32-bit
;; FNV-1a checksum, which was adequate for catching an honest divergence
;; between two replays and NOT adequate for what the log then did with it:
;; `torihiki.log` signs the state root, and signing a digest with no collision
;; resistance authenticates every other preimage that collides with it.
;;
;; Two things this encoding does deliberately:
;;
;; 1. IT HASHES THE LIVE STATE, NOT THE SLABS. The book preallocates arrays
;;    sized for its capacity, so digesting them directly would hash megabytes
;;    of zeros — cost proportional to how big the book COULD get rather than
;;    to what it holds. Walking the occupied levels and their queues costs
;;    what the state actually is.
;;
;; 2. IT IS TAGGED AND LENGTH-PREFIXED. Every section announces what it is and
;;    how many items follow. Concatenating fields without delimiters lets two
;;    different states encode to identical bytes (the classic length-extension
;;    ambiguity: one account with id 12 and balance 3 versus id 1 and balance
;;    23), which would hand out state-root collisions for free.
;;
;; Cost: this is Clojure arithmetic, not a native digest — right for the
;; kilobytes a normal block touches, and NOT right for a book holding hundreds
;; of thousands of resting orders. A production chain wants an incremental
;; authenticated tree so a block's root costs the DELTA rather than the whole
;; state. That is recorded as a follow-up, not solved here.

(def ^:const enc-tag-exchange 1)
(def ^:const enc-tag-market 2)
(def ^:const enc-tag-level 3)
(def ^:const enc-tag-order 4)
(def ^:const enc-tag-account 5)
(def ^:const enc-tag-position 6)

(defn- enc-int
  "One i53 as 8 bytes: a sign byte then seven big-endian magnitude bytes.
  Seven bytes carry 56 bits, comfortably above the 53 the domain allows.
  Written with division rather than bit shifts because ClojureScript's bitwise
  operators coerce through ToInt32 and would silently drop everything above
  bit 31."
  [v]
  (let [v (or v 0)
        neg (if (neg? v) 1 0)
        a (fx/abs* v)]
    (loop [i 6 a a acc (transient [neg])]
      (if (neg? i)
        (persistent! acc)
        (let [d (long (Math/pow 256 i))]
          (recur (dec i) (fx/fmod a d) (conj! acc (fx/fdiv a d))))))))

(defn- enc-ints [xs] (into [] (mapcat enc-int) xs))

(defn- encode-book
  "Occupied levels, each with its resting queue in time order."
  [b]
  (loop [side bk/bid out []]
    (if (> side bk/ask)
      out
      (let [levels (loop [l (bk/best b side) acc []]
                     (if (neg? l)
                       acc
                       (recur (bk/next-occupied b side l) (conj acc l))))
            side-bytes
            (reduce
             (fn [acc l]
               (let [orders (bk/level-orders b side l)]
                 (-> acc
                     (into (enc-ints [enc-tag-level side l (bk/level-qty b side l)
                                      (count orders)]))
                     (into (mapcat (fn [{:keys [owner qty]}]
                                     (enc-ints [enc-tag-order owner qty]))
                                   orders)))))
             (enc-ints [enc-tag-market side (count levels)])
             levels)]
        (recur (inc side) (into out side-bytes))))))

(def ^:const enc-tag-trigger 7)

(defn- encode-triggers
  "Triggers in `:id` order — the same total order they fire in. They are not
  in the book and cost nothing to hold, but they change what future blocks do,
  so a state root that ignored them would let a sequencer add or drop a stop
  without changing the root."
  [triggers]
  (reduce (fn [acc t]
            (let [o (:order t)]
              (into acc (enc-ints [enc-tag-trigger
                                   (:id t) (:account t) (:trigger-price t)
                                   (if (= :above (:direction t)) 1 0)
                                   (:side o) (:level o) (:qty o) (:flags o 0)]))))
          (enc-ints [enc-tag-trigger (count triggers)])
          (sort-by :id triggers)))

(defn- encode-clearing
  "Accounts in sorted id order, each with its positions in sorted market order.
  Sorted because Clojure map iteration order is unspecified — folding in map
  order would make the root depend on insertion history rather than on state."
  [clearing]
  (let [accts (sort (keys (:accounts clearing)))]
    (reduce
     (fn [acc a]
       (let [{:keys [collateral positions]} (get-in clearing [:accounts a])
             mkts (sort (keys positions))]
         (reduce (fn [acc m]
                   (let [p (get positions m)]
                     (into acc (enc-ints [enc-tag-position m (:size p)
                                          (:entry-notional p)
                                          (or (:isolated p) 0)]))))
                 (into acc (enc-ints [enc-tag-account a (or collateral 0)
                                      (count mkts)]))
                 mkts)))
     (enc-ints [enc-tag-account (count accts)
                (or (:insurance-fund clearing) 0)
                (or (:fees-collected clearing) 0)
                (or (:funding-residue clearing) 0)])
     accts)))

(defn canonical-bytes
  "The exact byte sequence `state-root` digests. Exposed because a state root
  is only auditable if the thing under the hash can be inspected."
  [ex]
  (let [mkts (sort (keys (:books ex)))]
    (reduce
     (fn [acc m]
       (-> acc
           (into (enc-ints [enc-tag-market m
                            (get-in ex [:oracle m] 0)
                            (get-in ex [:marks m] 0)
                            (get-in ex [:last m] 0)]))
           (into (encode-book (get-in ex [:books m])))
           (into (encode-triggers (get-in ex [:triggers m] [])))))
     (into (enc-ints [enc-tag-exchange (:height ex) (:ts ex) (count mkts)])
           (encode-clearing (:clearing ex)))
     mkts)))

(defn state-root
  "A SHA-256 commitment to the whole exchange, as lowercase hex.

  Unlike the checksum this replaced, it is safe to sign: finding a second
  state with the same root is as hard as finding a SHA-256 collision. It is
  still a FLAT digest — it commits to everything and proves nothing about any
  part — so a light client that needs to verify one balance without replaying
  the chain needs an authenticated tree, which this is not."
  [ex]
  (sha/sha256-hex (canonical-bytes ex)))
