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
            [torihiki.oracle :as orc]
            [torihiki.api :as api]
            [torihiki.auth :as auth]
            [kotoba.bytes :as b]
            [kotoba.bytes.sha256 :as sha]
            [torihiki.commit :as cm]))

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
  "An exchange over one or more markets.

  `:market` is one market and `:markets` is a collection of them; give either.
  The single-market spelling stays because it is what the tests, the benchmark
  and the parity scenario use, and rewriting three hundred call sites to pass a
  vector of one would be churn.

  Multi-market was always the shape here — `:books` is a map keyed by market id
  and every transaction names its market — but nothing built more than one, so
  the claim was untested. `book-opts` applies to every book: each market gets
  its own slab, so the memory is per market and a small ladder is the way to
  run many of them."
  [{:keys [market markets book-opts] :as _cfg}]
  (let [ms (vec (or (seq markets) [market]))]
    (when (empty? ms)
      (throw (ex-info "torihiki.state: an exchange needs at least one market" {})))
    (when (not= (count ms) (count (distinct (map :id ms))))
      ;; Two markets sharing an id is one market that behaves like two: the
      ;; second would replace the first in every map here, and the transactions
      ;; naming it would land somewhere nobody meant.
      (throw (ex-info "torihiki.state: duplicate market id"
                      {:ids (mapv :id ms)})))
    {:markets (into {} (map (juxt :id identity)) ms)
     :books (into {} (map (fn [m] [(:id m) (bk/new-book (or book-opts {}))])) ms)
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
     :funding (into {} (map (fn [m] [(:id m) fnd/empty-accumulator])) ms)
     :funding-params fnd/default-params
     ;; Conditional orders, per market. They are state: they change what future
     ;; blocks do, so they are committed to by the state root.
     :triggers {}
     :trigger-seq 0
     ;; Authentication state. Both are consensus state, not server state: every
     ;; validator must agree on which nonces are spent and which key owns an
     ;; account, or they disagree about what is a replay and who may trade.
     :nonces {}
     :account-keys {}
     ;; Oracle publishers are configured at genesis and this engine does not
     ;; change the set — rotating them is a governance question, and answering
     ;; it badly is worse than not answering it here.
       :oracle-publishers (set (:oracle-publishers _cfg))
     ;; How much each publisher's word weighs. See `apply-tx :oracle-submit`.
     :publisher-stake (:publisher-stake _cfg)
     :oracle-submissions {}
     :oracle-params orc/default-params
     ;; A market with no publishers configured accepts a direct `:oracle`
     ;; transaction, which is what the tests and a single-operator devnet use.
     ;; With publishers, direct setting is refused: leaving both doors open
     ;; would make the aggregate decorative.
     :oracle-stale {}
     ;; Where collateral is allowed to come from. Configured at genesis for the
     ;; same reason publishers are: it is a governance question, and an engine
     ;; that let a transaction change it would let whoever holds the current key
     ;; hand the mint to somebody else.
     ;;
     ;; nil means any authenticated account may credit itself, which is what the
     ;; tests and a single-operator devnet use — and which means the collateral
     ;; backing every position is whatever people asked for. `torihiki.api`
     ;; explains why that makes the clearinghouse exact and meaningless.
     :bridge-authority (:bridge-authority _cfg)
     ;; A fired trigger places an order, which moves the book, which reprices
     ;; the mark, which can arm more triggers. That cascade has to terminate.
     :max-trigger-rounds 8
     :liq-params liq/default-params
     ;; Withdrawals that have left an account and not yet left the exchange.
     ;;
     ;; A withdrawal used to decrement the balance and end there, which `api`
     ;; said plainly: nothing pays out anywhere. That is not a missing feature
     ;; at the edge, it is a hole in the middle — the collateral stops being
     ;; anybody's and no obligation replaces it, so the chain's own total says
     ;; the exchange owes less while nobody has been paid.
     ;;
     ;; A claim is that obligation, and it is a leaf, so whoever holds the
     ;; escrow can be handed an inclusion proof and pay against it instead of
     ;; against a promise. `:withdraw-seq` names claims; it is committed too,
     ;; because a replica that numbered the next one differently would fork.
     :withdrawals {}
     :withdraw-seq 0
     :height 0
     :ts 0}))

;; ── transactions ────────────────────────────────────────────────────────────

(defmulti apply-tx
  "Apply one transaction. Dispatches on `:tx`. Every method must be a pure
  function of (exchange, tx) — the book mutates its own arrays in place, but
  that mutation is fully determined by the arguments, so replay reproduces
  it exactly."
  (fn [_ex tx] (:tx tx)))

(defmethod apply-tx :default [ex _tx] ex)

(defmethod apply-tx :deposit
  [ex {:keys [account amount credit]}]
  ;; `credit` when the bridge is paying somebody, `account` when an account is
  ;; paying itself. `api/validate` has already refused the combinations that
  ;; would make this a mint.
  (update ex :clearing cl/deposit (or credit account) amount))

(defmethod apply-tx :withdraw
  [ex {:keys [account amount]}]
  (let [clearing (:clearing ex)
        clearing' (cl/withdraw clearing account amount (:marks ex) (:markets ex))]
    ;; `cl/withdraw` returns the state it was GIVEN when free collateral does
    ;; not cover the amount — that is its documented way of making a refused
    ;; withdrawal a no-op block entry rather than a halted chain. `identical?`
    ;; reads that answer without asking the same affordability question twice
    ;; in two places that would then have to agree forever.
    (if (identical? clearing clearing')
      ex
      (let [id (inc (:withdraw-seq ex 0))]
        (-> ex
            (assoc :clearing clearing')
            (assoc :withdraw-seq id)
            (assoc-in [:withdrawals id] {:account account :amount amount
                                         :height (:height ex 0)}))))))

(defmethod apply-tx :withdraw-settle
  [ex {:keys [account claim]}]
  ;; The bridge saying the money left. Only the bridge can say it, because it
  ;; is the only party that can know — the payment happened off this chain,
  ;; and this transaction is the receipt coming back.
  ;;
  ;; With no `:bridge-authority` configured, nobody can settle anything. That
  ;; is not a gap to paper over: an unbacked chain HAS no exit, and claims
  ;; piling up unsettled is what that looks like when it is stated instead of
  ;; hidden. Money quietly disappearing was the old way of saying the same
  ;; thing.
  (let [bridge (:bridge-authority ex)]
    (if (and bridge (= account bridge) (get-in ex [:withdrawals claim]))
      (update ex :withdrawals dissoc claim)
      ex)))

(defmethod apply-tx :withdraw-cancel
  [ex {:keys [account claim]}]
  ;; A payout that could not be made, handed back. Gated on OWNERSHIP rather
  ;; than on the bridge, for the reason `api` already gives about withdrawal
  ;; itself: taking back your own collateral needs no authority beyond the
  ;; signature. Requiring the bridge here would strand every claim on a chain
  ;; that has no bridge — which is precisely the chain most likely to need
  ;; the money back.
  (let [c (get-in ex [:withdrawals claim])]
    (if (and c (= account (:account c)))
      (-> ex
          (update :withdrawals dissoc claim)
          (update :clearing cl/deposit (:account c) (:amount c)))
      ex)))

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
  [ex {:keys [account market side level qty flags builder builder-fee]
      :or {flags 0}}]
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
      (apply-tx-order ex account market side level qty flags builder builder-fee))))

(defn- apply-tx-order
  [ex account market side level qty flags builder builder-fee]
  (let [book (get-in ex [:books market])
        before (bk/event-count book)
        _oid (bk/place! book side level qty flags account)
        fills (drop before (bk/fills book))
        mkt (get-in ex [:markets market])
        h (:height ex 0)]
    ;; Both sides of every fill are credited here, and in fill order. Crediting
    ;; only the taker and reconciling makers later would let an account's
    ;; margin be evaluated against a position it already holds but has not
    ;; been told about.
    (-> (reduce
         (fn [ex' {:keys [taker-owner maker-owner level qty taker-side]}]
           (let [taker-delta (if (= taker-side bk/bid) qty (- qty))
                 maker-delta (- taker-delta)
                 notional (fx/abs* (fx/notional level qty))
                 ;; Each side pays its OWN tier. Charging both from the taker's
                 ;; schedule would give a maker somebody else's discount, and
                 ;; the two are not even the same sign of activity.
                 ;;
                 ;; Read BEFORE this fill is added to either account's volume:
                 ;; a fill that pushed you over a threshold would otherwise be
                 ;; charged at the rate it earned you, which is a discount on
                 ;; the trade that has not happened yet.
                 [t-rate _] (cl/fee-rates-for (:clearing ex') taker-owner mkt h)
                 [_ m-rate] (cl/fee-rates-for (:clearing ex') maker-owner mkt h)]
             (-> ex'
                 (update :clearing cl/apply-fill taker-owner market taker-delta level t-rate)
                 (update :clearing cl/apply-fill maker-owner market maker-delta level m-rate)
                 ;; The builder's cut: paid by the taker who agreed to it and
                 ;; credited to the builder, not to the venue. A fee that went
                 ;; to `:fees-collected` would be the exchange charging on the
                 ;; client's behalf — a different arrangement from the one the
                 ;; trader signed.
                 ;;
                 ;; Only on the fills of the order that CARRIED the builder:
                 ;; the maker never saw it and cannot have agreed to it.
                 (cond->
                  (and builder (pos? (long (or builder-fee 0)))
                       (= taker-owner account))
                   (update :clearing cl/pay-builder taker-owner builder
                           (fx/mul-rate (fx/abs* (fx/notional level qty))
                                        (long builder-fee))))
                 (update :clearing cl/add-volume taker-owner h notional)
                 (update :clearing cl/add-volume maker-owner h notional)
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

(defn- twap-slice-qty
  "How much this firing should place: the even share, plus whatever rounding
  left behind on the LAST slice.

  Dividing and letting the remainder vanish would fill less than the trader
  asked for, and silently — the order would simply end short. Putting the
  remainder on the last slice keeps the total exact without making any single
  slice bigger than the schedule the market can see coming."
  [{:keys [remaining slices-left]}]
  (if (<= slices-left 1)
    remaining
    (max 1 (quot remaining slices-left))))

(defn- fire-twaps
  "Fire every TWAP on `market` whose next slice is due at this height.

  Sorted by id, because a Clojure map has no specified iteration order and two
  validators firing slices in different orders would take different fills from
  the same book — the same rule `liq/scan` and the trigger firing order
  already follow.

  A slice is an aggressive IOC limit, not a market order: this engine has no
  market order (`torihiki.api` says why), and IOC is what makes an unfilled
  slice disappear rather than rest. A TWAP that left resting orders behind
  would be a different instrument — the trader asked to be filled over time,
  not to quote."
  [ex market]
  (let [due (->> (:twaps ex {})
                 (filter (fn [[_ t]] (and (= market (:market t))
                                          (<= (:next-at t) (:height ex 0))
                                          (pos? (:remaining t)))))
                 (sort-by key))]
    (reduce
     (fn [ex' [id t]]
       (let [qty (twap-slice-qty t)
             book (get-in ex' [:books market])
             ;; Aggressive to the end of the ladder, IOC: take what is there
             ;; at this instant and drop the rest.
             level (if (= (:side t) bk/bid) (dec (:n-levels book)) 0)
             ex' (apply-tx ex' {:tx :order :account (:account t) :market market
                                :side (:side t) :level level :qty qty
                                :flags bk/flag-ioc})
             remaining (max 0 (- (:remaining t) qty))
             slices-left (max 0 (dec (:slices-left t)))]
         (if (or (zero? remaining) (zero? slices-left))
           (update ex' :twaps dissoc id)
           (update-in ex' [:twaps id] merge
                      {:remaining remaining
                       :slices-left slices-left
                       :next-at (+ (:height ex' 0) (:every t))}))))
     ex
     due)))

(defmethod apply-tx :bond
  [ex {:keys [account validator amount]}]
  ;; Delegated stake. `validator` is any account — a publisher, a witness, or
  ;; somebody else's name for one — because this chain does not have a
  ;; registry of validators to check against, and inventing one here would put
  ;; the validator set in two places.
  (update ex :clearing cl/bond account validator amount
          (cl/free-collateral (:clearing ex) account (:marks ex) (:markets ex))))

(defmethod apply-tx :unbond
  [ex {:keys [account validator amount]}]
  (update ex :clearing cl/unbond account validator amount (:height ex 0)))

(defmethod apply-tx :collect-unbonded
  [ex {:keys [account]}]
  ;; Asked for rather than swept, because a sweep would have to walk every
  ;; account every block to find the few that have something matured. The
  ;; money never moved, so waiting costs the holder nothing but the ask.
  (update ex :clearing cl/collect-unbonded account (:height ex 0)))

(defmethod apply-tx :vault-deposit
  [ex {:keys [account vault amount]}]
  ;; Outside money into a vault, for shares.
  ;;
  ;; The backstop vault was already an ordinary account — a position it absorbs
  ;; is margined like anyone else's rather than through a privileged path — and
  ;; that is what makes this possible without inventing a second kind of
  ;; account. What it could NOT do was take money from anybody but its
  ;; operator, so the capital standing behind the liquidation waterfall was
  ;; whatever one account happened to hold. The gap ledger called that out:
  ;; Hyperliquid's backstop takes outside deposits and this one could not.
  (update ex :clearing cl/vault-deposit account vault amount))

(defmethod apply-tx :vault-withdraw
  [ex {:keys [account vault shares]}]
  ;; Paid out of what the vault holds FREE — collateral that is not backing its
  ;; positions.
  (update ex :clearing cl/vault-withdraw account vault shares
          (cl/free-collateral (:clearing ex) vault (:marks ex) (:markets ex))))

(defmethod apply-tx :set-referrer
  [ex {:keys [account referrer]}]
  ;; Bound once, by the account itself. `cl/set-referrer` refuses a rebind and
  ;; a self-referral; both refusals are about the same thing — a referral that
  ;; can be moved is a stream that can be taken, and one that can point at
  ;; yourself is a rebate.
  (update ex :clearing cl/set-referrer account referrer))

(defmethod apply-tx :scale
  [ex {:keys [account market side level qty count* step flags]}]
  ;; A ladder of resting orders in one transaction.
  ;;
  ;; A maker quoting ten levels otherwise sends ten transactions, each with its
  ;; own nonce, and the nonces are strictly sequential — so one refusal in the
  ;; middle strands every order after it. Placing the ladder as one transaction
  ;; makes it arrive whole or not at all, which is what a quote IS.
  ;;
  ;; `step` walks AWAY from the top of book: down for bids, up for asks. The
  ;; sign is not the caller's to choose, because a ladder that walked the wrong
  ;; way would cross the book and take instead of quote — the opposite of what
  ;; a scale order is for, arriving through the same door.
  (let [dir (if (= side bk/bid) -1 1)
        book (get-in ex [:books market])
        n-levels (:n-levels book)]
    (reduce
     (fn [ex' i]
       (let [lvl (+ level (* dir i step))]
         (if (or (neg? lvl) (>= lvl n-levels))
           ;; Off the ladder. Skipped rather than refused: a scale that ran
           ;; past the end of the book would otherwise take the orders that
           ;; did fit with it.
           ex'
           (apply-tx ex' {:tx :order :account account :market market
                          :side side :level lvl :qty qty :flags (or flags 0)}))))
     ex
     (range count*))))

(defmethod apply-tx :twap
  [ex {:keys [account market side qty slices every]}]
  ;; An order worked over time instead of all at once — the thing a size that
  ;; would move the book against you needs.
  ;;
  ;; The schedule is in BLOCKS, like every other duration here: the engine has
  ;; no clock, and a schedule in seconds would fire at a different point in
  ;; the chain on every replica.
  (let [id (inc (:twap-seq ex 0))]
    (-> ex
        (assoc :twap-seq id)
        (assoc-in [:twaps id]
                  {:id id :account account :market market :side side
                   :remaining qty :slices-left slices :every every
                   ;; The first slice fires on the NEXT block rather than
                   ;; inside this one. A TWAP that put its first slice through
                   ;; the book in the same block it was submitted would be a
                   ;; market order wearing a schedule.
                   :next-at (inc (:height ex 0))
                   :started-at (:height ex 0)}))))

(defmethod apply-tx :cancel-twap
  [ex {:keys [account id]}]
  ;; Only the owner. Same rule as cancelling an order, and for the same reason.
  (if (= account (:account (get-in ex [:twaps id])))
    (update ex :twaps dissoc id)
    ex))

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
                                                 (bit-or (:flags o 0) bk/flag-ioc)
                                                 ;; A trigger's order carries no
                                                 ;; builder: the routing client
                                                 ;; is not on the other end when
                                                 ;; a stop fires blocks later.
                                                 nil nil)
                                 e)))
                           ex armed)]
            (recur (reprice! ex market) (inc round))))))))

(defmethod apply-tx :amend-market
  [ex {:keys [account market spec]}]
  ;; Changing a listed market's parameters — fees, leverage, tiers, the name.
  ;;
  ;; `:list-market` refuses an id that already exists, and it should: replacing
  ;; a live market would strand the orders resting in it. But a market whose
  ;; parameters can never change is not how any exchange works — fees move,
  ;; margin schedules tighten — and the alternative to a transaction for it is
  ;; an operator editing config, which is exactly the thing that put the
  ;; symbol in the source and not on the chain.
  ;;
  ;; **`:tick` and `:lot` are immutable.** They decide what a price LEVEL
  ;; means, so changing them silently reprices every resting order and every
  ;; open position — the same class of harm as replacing the market outright,
  ;; arriving through a door labelled amend.
  (let [bridge (:bridge-authority ex)
        cur (get-in ex [:markets market])]
    (if (or (nil? bridge) (not= account bridge) (nil? cur) (not (map? spec)))
      ex
      (assoc-in ex [:markets market]
                (merge cur (dissoc spec :id :tick :lot))))))

(defmethod apply-tx :authorize-agent
  [ex {:keys [account agent expires]}]
  ;; A delegated key. `torihiki.auth/agent-forbidden` is what makes it worth
  ;; having: this key can trade and cannot move money out, so a trading
  ;; program can hold it and a thief cannot use it to empty the account.
  ;;
  ;; Only an owner signature reaches here — `auth/check` refuses
  ;; `:authorize-agent` from an agent, so agents cannot mint agents.
  ;;
  ;; `expires` is a BLOCK HEIGHT. The engine has no clock, and an expiry in
  ;; wall time would make the moment a key dies depend on which validator you
  ;; asked. nil never expires, which is the honest spelling of what a key with
  ;; no expiry is — rather than a large number that looks like a policy.
  (if (or (not (string? agent)) (= agent (get-in ex [:account-keys account])))
    ;; Authorising the owner key as an agent would DEMOTE it: `check` looks for
    ;; an agent record before it decides what a signer may do.
    ex
    (assoc-in ex [:agents account agent] {:expires expires})))

(defmethod apply-tx :revoke-agent
  [ex {:keys [account agent]}]
  ;; Revocation is immediate and needs no reason. A key you are unsure about
  ;; is a key you revoke; making that expensive is how people leave them live.
  (if (get-in ex [:agents account agent])
    (update-in ex [:agents account] dissoc agent)
    ex))

(defmethod apply-tx :list-market
  [ex {:keys [account market spec book-opts]}]
  ;; Listing a market on a RUNNING chain.
  ;;
  ;; Markets were genesis-only, and a chain restores from a checkpoint — so
  ;; adding one to the source added it to a chain nobody was running. A market
  ;; has to arrive as a state transition or it does not arrive at all.
  ;;
  ;; Gated on `:bridge-authority` because that is the authority this chain
  ;; already has, and listing is the same kind of question as who may mint:
  ;; whoever can list can price, and pricing is what margin reads. With no
  ;; authority configured nobody can list — the same answer `:withdraw-settle`
  ;; gives, and for the same reason: an unconfigured chain has no governance,
  ;; and pretending otherwise would hand it to whoever asks first.
  (let [bridge (:bridge-authority ex)]
    (if (or (nil? bridge) (not= account bridge)
            (contains? (:markets ex) market)
            (not (map? spec)))
      ex
      (-> ex
          (assoc-in [:markets market] (assoc spec :id market))
          (assoc-in [:books market] (bk/new-book (or book-opts {})))
          (assoc-in [:funding market] fnd/empty-accumulator)))))

(defmethod apply-tx :cancel-all
  [ex {:keys [market account]}]
  ;; The risk control every venue has and this did not: one transaction that
  ;; takes a maker out of the market. Without it, pulling N quotes costs N
  ;; transactions, each with its own nonce, and the last one lands after the
  ;; move the maker was trying to get out of the way of.
  (bk/cancel-all! (get-in ex [:books market]) account)
  ex)

(defmethod apply-tx :amend
  [ex {:keys [market oid account level qty]}]
  (let [book (get-in ex [:books market])
        cur (bk/order-of book oid)]
    (cond
      ;; Not a live order, or not theirs. Same answer as a cancel naming
      ;; somebody else's order: a no-op block entry.
      (or (nil? cur) (not= account (:owner cur))) ex

      ;; Same price, smaller size — the one modification that keeps its place
      ;; in the queue. See `bk/reduce-to!` for why that matters enough to be a
      ;; separate path.
      (and (= level (:level cur)) (< qty (:qty cur)))
      (do (bk/reduce-to! book oid account qty) ex)

      ;; Anything else is a new order at the back of the line, so it IS one.
      ;; Delegating rather than re-implementing: placing has to run fills
      ;; through the clearinghouse, reprice the mark and arm triggers, and a
      ;; second copy of that would be a second definition of what an order
      ;; does.
      :else
      (if (zero? (bk/cancel! book oid account))
        ex
        (apply-tx ex {:tx :order :account account :market market
                      :side (:side cur) :level level :qty qty :flags 0})))))

(defmethod apply-tx :cancel
  [ex {:keys [market oid account]}]
  ;; `account` is who SIGNED this, and until it was passed here nothing
  ;; compared it to who placed the order — see `bk/cancel!`. A cancel naming
  ;; somebody else's order is now a no-op block entry rather than a free hand
  ;; on their book.
  (bk/cancel! (get-in ex [:books market]) oid account)
  ex)

;; Set the oracle directly. Only permitted when no publisher set is configured
;; (see torihiki.api/validate). A devnet with one operator uses this; a market
;; with publishers must go through :oracle-submit, or the aggregate would be
;; decorative.
(defmethod apply-tx :oracle
  [ex {:keys [market price]}]
  (-> ex
      (assoc-in [:oracle market] price)
      (assoc-in [:oracle-stale market] false)
      (reprice! market)
      (fire-triggers market)))

;; One publisher's observation. The aggregate is recomputed from every fresh
;; authorised submission; a single publisher never sets the price.
(defmethod apply-tx :oracle-submit
  [ex {:keys [account market price]}]
  (let [ex (assoc-in ex [:oracle-submissions market account]
                     {:price price :ts (:ts ex)})
        {:keys [price stale?]} (orc/aggregate
                                (get-in ex [:oracle-submissions market])
                                (:oracle-publishers ex)
                                (:ts ex)
                                (:oracle-params ex)
                                ;; Stake, when the chain has any. `:publisher-stake`
                                ;; is genesis data like the publisher set itself:
                                ;; who may speak and how much their word weighs are
                                ;; the same governance question, and a transaction
                                ;; that could change either would hand the price to
                                ;; whoever holds the current key.
                                ;;
                                ;; Absent, the aggregate is the plain median it has
                                ;; always been — a chain with no bonds has nothing
                                ;; to weigh, and inventing weights would be a
                                ;; stronger claim than the deployment supports.
                                ;; Bonded stake first, genesis map second.
                                ;;
                                ;; `:publisher-stake` was a number in the
                                ;; config — an operator's assertion about how
                                ;; much each voice weighs. Bonds are the same
                                ;; claim made by people putting collateral
                                ;; behind it, which is what makes the weight
                                ;; cost something. The map stays as the answer
                                ;; for a chain whose publishers have not
                                ;; bonded, so nothing that worked stops.
                                (let [c (:clearing ex)
                                      any-bonds? (seq (:bonds c))]
                                  (cond
                                    any-bonds? (fn [pub] (cl/stake-of c pub))
                                    (:publisher-stake ex) (fn [pub] (get (:publisher-stake ex) pub 0))
                                    :else nil)))]
    (-> (if stale?
          ;; Keep the last price but say it is stale. Discarding it would
          ;; leave the mark at zero and stop everything; pretending it is
          ;; current would liquidate people on a number nobody vouches for.
          (assoc-in ex [:oracle-stale market] true)
          (-> ex
              (assoc-in [:oracle market] price)
              (assoc-in [:oracle-stale market] false)))
        (reprice! market)
        (fire-triggers market))))

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

(defn sweep-liquidations
  "Liquidate everyone liquidatable on `market`, or do nothing.

  ## Why this never throws

  It refused a market with no mark by throwing. `apply-block` catches nothing,
  so that threw out of the block — and its own docstring says why that cannot
  be allowed: a transaction that throws stops every validator at the same
  place, so anybody able to submit one can stop the chain. `:liquidate` names
  its market and `api/validate` only checks that the market EXISTS, so a
  market that exists without a price yet was a one-transaction halt.

  A market with no mark and a market with a stale one are the same situation —
  there is no price anybody vouches for — and the answer was already written
  for the second: waiting is reversible and liquidating at a price nobody
  vouches for is not. Bad debt growing while the feed is down is the smaller
  cost. Doing nothing is that answer; throwing was never a different policy,
  only a louder way to fail at it."
  [ex market]
  (let [book (get-in ex [:books market])
        mark (get-in ex [:marks market] (get-in ex [:oracle market] 0))
        ;; A stale oracle stops liquidation. Closing somebody's position at a
        ;; price nobody currently vouches for is irreversible; waiting is not.
        ;; Bad debt can grow while the feed is down, and that is the smaller
        ;; cost.
        stale? (get-in ex [:oracle-stale market] false)
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
     (if (or stale? (not (pos? mark)))
       []
       (liq/scan (:clearing ex) market (:marks ex) (:markets ex))))))

(defmethod apply-tx :liquidate
  [ex {:keys [market]}]
  ;; Kept, and now redundant on its own chain: `apply-block` sweeps every
  ;; market at the end of every block, so a `:liquidate` transaction can only
  ;; do what would have happened anyway a moment later. It stays because it is
  ;; the seam the tests drive one market through, and because a keeper that
  ;; wants a position closed WITHIN a block rather than after it still has a
  ;; way to ask.
  (sweep-liquidations ex market))

;; ── blocks ──────────────────────────────────────────────────────────────────

(defn apply-block
  "Apply an ordered block. The header's `:ts` becomes the state machine's
  logical clock for the duration — nothing below it may consult a real one.

  **Application is total.** Every transaction is validated first; one that
  fails is skipped and recorded in `:rejected`, never applied and never thrown
  from. This is not politeness toward clients, it is LIVENESS: a block that
  throws on a malformed transaction stops every validator at the same place,
  so anyone able to submit a transaction can stop the chain with a typo.

  Rejections are part of the block's outcome and therefore part of the state
  root — two validators must agree on what was refused as much as on what was
  executed, and without that a sequencer could quietly drop a transaction and
  still produce a matching root.

  ## Liquidation runs because the block ended, not because somebody asked

  Every market is swept after the transactions, in sorted market order. It
  used to happen only when a `:liquidate` transaction appeared, which made
  solvency depend on a keeper choosing to send one: with nobody sending, an
  underwater account simply stayed open and its bad debt grew. That is a
  strange thing for a chain to leave to goodwill in general, and it is a
  contradiction now in particular — `torihiki.commit` publishes what the
  exchange owes, and a number that is only trustworthy while a keeper is
  awake is not an attestation.

  The sweep costs a scan of the accounts per market per block, and that is a
  real cost rather than a free one: it is proportional to accounts, not to
  positions at risk, so it is the thing to index first when it stops being
  cheap. Paying it every block is what makes it unconditional."
  ([ex block] (apply-block ex block nil))
  ([ex {:keys [height ts txs]} {:keys [verify-fn chain-id derive-account]
                                :as _opts}]
   (let [ex (-> ex (assoc :height height :ts ts) (assoc :rejected []))
         applied
         (reduce
          (fn [e [i entry]]
            (if verify-fn
              ;; Authenticated: the entry is a signed envelope around a tx.
              (if-let [reason (auth/check e entry chain-id verify-fn derive-account)]
                ;; Authentication failed, so this was never from the account —
                ;; nothing is consumed and nothing is applied.
                (update e :rejected conj {:index i :tx (:tx (:tx entry)) :reason reason})
                ;; Authenticated. The nonce is consumed even if the transaction
                ;; then fails validation: the holder authorised THAT nonce for
                ;; THAT transaction, and leaving it unspent would make the
                ;; signature reusable.
                (let [e (auth/accept e entry)
                      t (assoc (:tx entry) :account (:account entry))]
                  (if-let [reason (api/validate e t)]
                    (update e :rejected conj {:index i :tx (:tx t) :reason reason})
                    (apply-tx e t))))
              ;; Unauthenticated mode: for tests, for replay of an already-agreed
              ;; log, and for a sequencer that authenticates at its own edge.
              (if-let [reason (api/validate e entry)]
                (update e :rejected conj {:index i :tx (:tx entry) :reason reason})
                (apply-tx e entry))))
          (do (doseq [[_ book] (:books ex)] (bk/reset-events! book)) ex)
          (map-indexed vector txs))]
     ;; Sorted, because `keys` on a Clojure map has no specified order and two
     ;; validators sweeping markets in different orders would take different
     ;; fills from books that liquidation itself moves.
     ;; TWAP slices fire before the sweep: a slice can move the mark, and an
     ;; account made liquidatable by its own slice should be found by the
     ;; sweep in the block that made it so, not the next one.
     (-> (reduce fire-twaps applied (sort (keys (:books applied))))
         (as-> ex' (reduce sweep-liquidations ex' (sort (keys (:books ex')))))))))

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

(def ^:const enc-tag-rejected 8)
(def ^:const enc-tag-nonce 9)
(def ^:const enc-tag-oracle-sub 11)
(def ^:const enc-tag-key 10)

(defn- enc-string
  "A string as its UTF-8 bytes, length-prefixed. Public keys are the only
  strings in the state, and they still have to be committed to — an account
  whose bound key is outside the root could be rebound without changing it."
  [x]
  (let [bs (b/utf8-encode (str x))]
    (into (enc-int (count bs)) bs)))

(defn- encode-oracle-market
  "One market's publisher submissions and staleness flag, publishers sorted.
  They are state: the next aggregate depends on them, and a sequencer that
  could add or drop a submission without changing the root could move the mark
  invisibly."
  [ex m]
  (let [subs (get-in ex [:oracle-submissions m] {})
        pubs (sort (keys subs))]
    (reduce (fn [acc p]
              (let [{:keys [price ts]} (get subs p)]
                (into acc (enc-ints [enc-tag-oracle-sub m p price ts]))))
            (enc-ints [enc-tag-oracle-sub m (count pubs)
                       (if (get-in ex [:oracle-stale m] false) 1 0)])
            pubs)))

(def ^:const enc-tag-twap 16)

(defn- encode-twap
  "One working TWAP: who, where, which way, how much is left, and when the
  next slice fires.

  In the root because it changes what FUTURE blocks do — the same argument
  `:triggers` carries. A replica that disagreed about a working schedule would
  put a different order through the book two blocks later and diverge then,
  with nothing to point at."
  [id t]
  (enc-ints [enc-tag-twap id (:account t) (:market t) (:side t)
             (:remaining t) (:slices-left t) (:every t) (:next-at t)]))

(def ^:const enc-tag-withdrawal 13)

(defn- encode-withdrawal
  "One pending claim: who is owed, how much, and the height it was raised at.

  The height is in the preimage because it is the only thing that
  distinguishes two identical withdrawals by the same account, and because a
  claim's age is what an operator uses to decide whether a payout is late."
  [id w]
  (enc-ints [enc-tag-withdrawal id (:account w) (:amount w) (:height w 0)]))

(def ^:const enc-tag-governance 12)

(defn- encode-governance
  "The two configured authorities: who may create collateral, and who may
  publish a price.

  `encode-oracle-market` above already gives the argument for half of this —
  a sequencer that could add or drop a SUBMISSION without changing the root
  could move the mark invisibly. The same sentence is true of the set of
  accounts allowed to submit, and of the one account allowed to mint, and
  neither was committed to anything. They were read straight off the config
  map every replica was started with.

  What that costs is not a wrong balance; every balance stays exactly right.
  It is that two replicas configured differently produce the SAME root while
  disagreeing about who may mint — so the number whose entire job is to catch
  divergence reports none, and the disagreement surfaces later as collateral
  that one replica accepted and another refused. It is the failure mode
  `torihiki.api` describes for unbacked collateral, moved up a level: the
  arithmetic is exact and means nothing.

  It also fixes what a proof proves. `commit/balance-proof` lets a client
  check one balance against the root without replaying the chain; until now
  that client could learn the balance and could not learn whether the mint
  behind it was authorised, because the authority was not under the root it
  was checking against.

  A `nil` authority is encoded as its own flag rather than as account 0. Zero
  is a real account id, and `nil` means the opposite of a restriction — it is
  the devnet faucet, where anybody may credit themselves."
  [ex]
  (let [bridge (:bridge-authority ex)
        pubs (sort (:oracle-publishers ex))
        stake (:publisher-stake ex)]
    (-> (enc-ints [enc-tag-governance
                   (if (some? bridge) 1 0)
                   (or bridge 0)
                   (count pubs)])
        (into (enc-ints pubs))
        ;; The weights, beside the voices. Stake decides whose price wins, so
        ;; a replica that disagreed about it would compute a different mark
        ;; from the same submissions — and margin reads the mark.
        (into (enc-ints [enc-tag-governance (if stake 1 0) (count (or stake {}))]))
        (into (enc-ints (mapcat (fn [p] [p (get stake p 0)]) (sort (keys (or stake {})))))))))

(defn- auth-accounts [ex]
  (sort (distinct (concat (keys (:nonces ex)) (keys (:account-keys ex))
                          ;; An account can hold agents; leaving it out of this
                          ;; list would leave its authorisations out of the
                          ;; root, which is the whole reason they are encoded.
                          (keys (:agents ex))))))

(def ^:const enc-tag-market-spec 15)

(defn- encode-market-spec
  "One market's parameters: what it is called, how it ticks, and every rate the
  clearinghouse charges or margins against.

  These were not in the root. They are consensus-critical — the margin rates
  decide who is liquidatable and the fee rates decide what every fill costs —
  so two replicas configured differently would liquidate different accounts
  and charge different fees. The balances they produced would diverge and be
  caught, eventually; the CONFIGURATION that produced them would not be, so
  the root said nothing about the difference until somebody was near
  liquidation. That is the same argument `encode-governance` makes about who
  may mint, one level down.

  Tiers are folded in order. `margin-tiers` is sorted at construction, so the
  order here is the market's own and not a map iteration."
  [ex m]
  (let [spec (get-in ex [:markets m])
        tiers (:margin-tiers spec [])]
    (into
     (reduce (fn [acc t]
              (into acc (enc-ints [enc-tag-market-spec m
                                   (:max-notional t) (:max-leverage t)
                                   (:initial-margin-rate t)
                                   (:maintenance-margin-rate t)])))
            (-> (enc-ints [enc-tag-market-spec m
                           (:tick spec 0) (:lot spec 0)
                           (:max-leverage spec 0)
                           (:initial-margin-rate spec 0)
                           (:maintenance-margin-rate spec 0)
                           (:taker-fee-rate spec 0)
                           (:maker-fee-rate spec 0)
                           (count (:fee-tiers spec []))
                           (or (:open-interest-cap spec) 0)
                           (if (:open-interest-cap spec) 1 0)
                           (count tiers)])
                (into (enc-string (:symbol spec ""))))
            tiers)
        ;; Fee tiers, after the margin tiers. Both are schedules the
        ;; clearinghouse reads, and a replica that disagreed about either
        ;; would charge a different price for the same fill.
        (into (reduce (fn [acc t]
                        (into acc (enc-ints [enc-tag-market-spec m
                                             (:min-volume t 0)
                                             (:taker-fee-rate t 0)
                                             (:maker-fee-rate t 0)])))
                      []
                      (:fee-tiers spec []))))))

(def ^:const enc-tag-agent 14)

(defn- encode-auth-account
  "One account's nonce, key binding, and delegated keys.

  The agents are here because they are authority: a replica that disagreed
  about which key may trade for an account would accept transactions another
  replica refuses, and the two would diverge on the next order — the same
  argument `encode-governance` makes about who may mint.

  Sorted by key, because map iteration order is unspecified and a root that
  depended on insertion history would differ between a replica that restored
  from a snapshot and one that never restarted."
  [ex a]
  (let [agents (sort (keys (get-in ex [:agents a] {})))]
    (reduce (fn [acc k]
              (-> acc
                  (into (enc-ints [enc-tag-agent a (or (:expires (get-in ex [:agents a k])) 0)]))
                  (into (enc-string k))))
            (-> (enc-ints [enc-tag-nonce a (get-in ex [:nonces a] 0)])
                (into (enc-ints [enc-tag-key a]))
                (into (enc-string (get-in ex [:account-keys a] "")))
                (into (enc-ints [enc-tag-agent a (count agents)])))
            agents)))

(def reason-codes
  "A stable integer per rejection reason. `clojure.core/hash` on a keyword is
  not guaranteed to agree between the JVM and JS, so a root that used it would
  differ by platform — the same trap the integer account ids already avoid.
  Append-only: renumbering one would silently change every historical root."
  {:unknown-tx 1
   :unknown-market 2
   :missing-field 3
   :bad-account 4
   :bad-quantity 5
   :bad-price-level 6
   :bad-side 7
   :bad-flags 8
   :bad-amount 9
   :bad-trigger-direction 10
   :bad-trigger-price 11
   :bad-trigger-order 12})

(defn- encode-rejections
  [rejected]
  (reduce (fn [acc {:keys [index reason]}]
            (into acc (enc-ints [enc-tag-rejected index
                                 (get reason-codes reason 0)])))
          (enc-ints [enc-tag-rejected (count rejected)])
          rejected))

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

(defn- encode-clearing-totals
  [clearing]
  (enc-ints [enc-tag-account (count (:accounts clearing))
             (or (:insurance-fund clearing) 0)
             (or (:fees-collected clearing) 0)
             (or (:funding-residue clearing) 0)]))

(defn- encode-clearing-account
  "One account: its collateral and its positions in sorted market order.
  Sorted because Clojure map iteration order is unspecified — folding in map
  order would make the root depend on insertion history rather than on state.

  **This is the leaf a light client proves.** Everything needed to answer
  \"what does account A hold\" is here and nowhere else, which is what lets an
  inclusion proof stand in for a replay."
  [clearing a]
  (let [{:keys [collateral deficit positions]} (get-in clearing [:accounts a])
        mkts (sort (keys positions))]
    (reduce (fn [acc m]
              (let [p (get positions m)]
                (into acc (enc-ints [enc-tag-position m (:size p)
                                     (:entry-notional p)
                                     (or (:isolated p) 0)]))))
            ;; `:deficit` is bad debt this account owes after a liquidation
            ;; the insurance fund could not fully cover — see
            ;; `torihiki.clearing/settle-deficit`. It is committed for the same
            ;; reason the collateral beside it is: a deposit pays it down
            ;; before it credits anything, so two replicas that disagreed about
            ;; it would disagree about the balance one block later.
            ;; The referrer, and the volume. Both decide where money goes on
            ;; the next fill — the referrer takes a share of the venue's fee,
            ;; and the volume picks the tier — so a replica that disagreed
            ;; about either would pay a different account or charge a
            ;; different price.
            ;;
            ;; Volume decides which fee tier this account pays, so it is
            ;; authority over money the same way the rates themselves are: two
            ;; replicas that disagreed about it would charge different fees on
            ;; the next fill.
            (let [{:keys [epoch cur prev] :or {epoch 0 cur 0 prev 0}}
                  (get-in clearing [:volume a])]
              (enc-ints [enc-tag-account a (or collateral 0) (or deficit 0)
                         epoch cur prev
                         (or (get-in clearing [:referrers a]) 0)
                         (if (get-in clearing [:referrers a]) 1 0)
                         ;; Bonds and unbondings: a claim the chain can slash
                         ;; and a claim the holder is waiting on. Both decide
                         ;; what this account may spend, and bonds decide whose
                         ;; price wins — a replica that disagreed would compute
                         ;; a different mark.
                         (reduce + 0 (vals (get-in clearing [:bonds a] {})))
                         (count (get-in clearing [:bonds a] {}))
                         (reduce + 0 (map :amount (get-in clearing [:unbonding a] [])))
                         (count mkts)]))
            mkts)))

(def ^:private id-pad
  "Sixteen zeros: the width of the largest i53, 9007199254740992.

  Twelve was the first guess and it was wrong on live data within the hour —
  `torihiki.address/derive` produced account 34633027260816, fourteen digits,
  so the id was not padded at all. Ids of different lengths sort
  lexicographically against each other, and lexicographic order stops
  agreeing with numeric order the moment the lengths differ: \"99999999999999\"
  sorts AFTER \"100000000000000\" while the number sorts before it.

  merkle-sum orders leaves by id, so a disagreement there means the tree
  commits to a different sequence than the flat encoding does — quietly, with
  both still producing a stable root. Sixteen makes every id the same width,
  and no account id can exceed it because no state value can."
  "0000000000000000")

(defn- pad-id [n]
  (let [s (str n)]
    (str (subs id-pad 0 (max 0 (- (count id-pad) (count s)))) s)))

(defn canonical-leaves
  "The state, cut into the pieces the authenticated tree commits to
  separately, IN THE ORDER THE FLAT ENCODING ALREADY USED.

  Each entry is `{:id <sortable string> :bytes <ints>}`.

  ## The cut was a refinement; the governance leaf is not

  When the tree landed, `canonical-bytes` still produced the SAME bytes it
  produced before, so the tree was a structure placed OVER the existing
  encoding rather than a new encoding.

  `00:01` breaks that. It adds bytes, so every digest moves — `flat-root` and
  `state-root` both, and the parity scenario's published digest with them.
  That is the point rather than a side effect: the authorities were not under
  the root, and there is no way to put them under it that leaves the root
  alone.

  **This is a state-encoding fork.** Replicas on either side of it compute
  different roots for identical state, which is exactly the shape of the
  divergence bug the root exists to catch, so a rolling upgrade of a live
  chain does not work — every replica has to cross together, or the chain
  stops at the first vote that disagrees. See `enc-tag-governance` for what is
  being bought.

  Ids sort into that order (`clojure.core/sort` on strings), with numbers
  zero-padded — unpadded, account 10 would sort before account 9 and the
  concatenation would stop matching.

  ## Why the account is its own leaf

  \"Verify one balance without replaying the chain\" is only possible if that
  balance is a leaf. Everything else is cut along the boundaries the flat
  encoding already had, because those boundaries were already the sections a
  reader reasons about."
  [ex]
  (let [mkts (sort (keys (:books ex)))
        auth-accts (auth-accounts ex)
        clearing (:clearing ex)
        ;; Accounts with VOLUME but no balance are accounts too. An account
        ;; can trade its position flat and hold nothing, and its volume still
        ;; decides the fee tier it pays on the next fill — so leaving it out
        ;; of the leaves puts a discount outside the root. Found by a test
        ;; that asserted the root moves when volume does, and it did not.
        accts (sort (distinct (concat (keys (:accounts clearing))
                                      (keys (:volume clearing))
                                      ;; An account can have bound a referrer
                                      ;; and never traded; the binding is
                                      ;; still authority over money.
                                      (keys (:referrers clearing)))))]
    (vec
     (concat
      [{:id "00" :bytes (enc-ints [enc-tag-exchange (:height ex) (:ts ex) (count mkts)])}
       {:id "00:01" :bytes (encode-governance ex)}
       {:id "01" :bytes (encode-rejections (:rejected ex []))}
       {:id "02:00" :bytes (enc-ints [enc-tag-nonce (count auth-accts)])}]
      (for [a auth-accts]
        {:id (str "02:01:" (pad-id a)) :bytes (encode-auth-account ex a)})
      (for [m mkts]
        {:id (str "03:" (pad-id m)) :bytes (encode-oracle-market ex m)})
      (for [m mkts]
        {:id (str "03:9:" (pad-id m)) :bytes (encode-market-spec ex m)})
      [{:id "04:00" :bytes (encode-clearing-totals clearing)}]
      (for [a accts]
        {:id (str "04:01:" (pad-id a))
         :bytes (encode-clearing-account clearing a)
         ;; The merkle-sum leaf value, so the root carries the total of every
         ;; account's collateral. Non-negative because `settle-deficit` moves
         ;; bad debt out of this field — which is the whole reason the sums
         ;; could not be real before, since merkle-sum refuses a negative leaf
         ;; and a sum that holds only while nobody is underwater is a claim
         ;; that breaks when it is needed.
         :sum (or (get-in clearing [:accounts a :collateral]) 0)})
      ;; Vaults: who holds how many shares of which vault.
      ;;
      ;; A share is a claim on the vault's collateral, so a replica that
      ;; disagreed about the amounts would pay a different number on the next
      ;; withdrawal — the same argument the account balances themselves carry.
      ;; Counts alone would not do it: the AMOUNTS are the claim.
      (let [vs (:vaults clearing {})]
        (cons
         {:id "04:02" :bytes (enc-ints [enc-tag-account (count vs)])}
         (for [v (sort (keys vs))]
           {:id (str "04:02:" (pad-id v))
            :bytes (let [holders (sort (keys (get-in vs [v :shares] {})))]
                     (reduce (fn [acc h]
                               (into acc (enc-ints [enc-tag-account v h
                                                    (get-in vs [v :shares h] 0)])))
                             (enc-ints [enc-tag-account v
                                        (get-in vs [v :total-shares] 0)
                                        (count holders)])
                             holders))})))
      (mapcat
       (fn [m]
         [{:id (str "05:" (pad-id m) ":0")
           :bytes (enc-ints [enc-tag-market m
                             (get-in ex [:oracle m] 0)
                             (get-in ex [:marks m] 0)
                             (get-in ex [:last m] 0)])}
          {:id (str "05:" (pad-id m) ":1") :bytes (encode-book (get-in ex [:books m]))}
          {:id (str "05:" (pad-id m) ":2")
           :bytes (encode-triggers (get-in ex [:triggers m] []))}])
       mkts)
      ;; Working TWAPs, before the withdrawals and after the markets.
      (let [ws (:twaps ex {})]
        (cons
         {:id "05:9" :bytes (enc-ints [enc-tag-twap (count ws) (:twap-seq ex 0)])}
         (for [id (sort (keys ws))]
           {:id (str "05:9:" (pad-id id)) :bytes (encode-twap id (get ws id))})))
      ;; Pending withdrawals. The count and the next claim number first, so
      ;; the section is self-delimiting the way the nonce and clearing
      ;; sections are.
      (let [ws (:withdrawals ex {})]
        (cons
         {:id "06:00" :bytes (enc-ints [enc-tag-withdrawal (count ws)
                                        (:withdraw-seq ex 0)])}
         (for [id (sort (keys ws))]
           {:id (str "06:01:" (pad-id id))
            :bytes (encode-withdrawal id (get ws id))
            ;; A claim carries its amount as its merkle-sum value, so the
            ;; root's total stays the exchange's whole obligation across a
            ;; withdrawal: the money moves from an account leaf to a claim
            ;; leaf and the sum does not change. Settlement is then the only
            ;; thing that lowers it, which is correct, because settlement is
            ;; the only thing that actually pays anybody.
            :sum (:amount (get ws id))})))))))

(defn canonical-bytes
  "The exact byte sequence `flat-root` digests. Exposed because a state root
  is only auditable if the thing under the hash can be inspected.

  Now derived from `canonical-leaves` so the two cannot drift: if a section
  were added to one and not the other, the flat digest and the tree would
  commit to different states while both looking healthy."
  [ex]
  (into [] (mapcat :bytes) (canonical-leaves ex)))

(defn flat-root
  "The pre-tree digest: SHA-256 over the whole canonical encoding.

  Kept, rather than deleted, because it is the compatibility anchor. The tree
  changed what `state-root` returns; this did not change at all, so a stored
  digest from before the tree can still be checked against the state it came
  from."
  [ex]
  (sha/sha256-hex (canonical-bytes ex)))

(defn state-root
  "A SHA-256 commitment to the whole exchange, as lowercase hex — the root of
  an authenticated tree over `canonical-leaves`.

  Safe to sign: finding a second state with the same root is as hard as
  finding a SHA-256 collision. Unlike the flat digest it replaces, it also
  PROVES PARTS: `torihiki.commit/balance-proof` produces an inclusion proof
  for one account that a client can check against this root without holding
  the state or replaying the chain.

  **This value changed when the tree landed.** The bytes underneath did not
  (`flat-root` still returns what `state-root` used to), so a chain's history
  is not invalidated — but a stored `state-root` from before the change is a
  `flat-root`, and comparing the two will differ for a reason that is not a
  determinism bug. During a rolling upgrade, replicas on either side of it
  report different roots for the same state; that window looks exactly like
  the failure this number exists to catch, and it is not one."
  [ex]
  (:hash (:root (cm/tree (canonical-leaves ex)))))
