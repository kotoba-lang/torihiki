(ns torihiki.api
  "The request surface: validation, a stated rejection taxonomy, and the read
  models a client actually needs.

  Pure. No transport, no sockets, no serialization format — a Worker, an nbb
  process or a test calls `validate` and `query` directly. What lives here is
  the CONTRACT; how bytes reach it is somebody else's problem.

  ## Why validation is not the state machine's job to throw about

  `apply-tx` used to throw on a price level outside the ladder. Inside a block
  that is not a rejected order, it is a HALTED CHAIN: one malformed
  transaction, submitted by anyone, and every validator stops at the same
  place. Liveness dies to a typo.

  So validation is separated from application, and it is total: every
  transaction either passes `validate` and is applied, or fails it and is
  recorded as a rejection. Nothing in between, and nothing that throws.

  This is the difference between a program that refuses bad input and a
  protocol that survives it.

  ## The taxonomy is closed on purpose

  Rejection reasons are keywords from a fixed set. A free-text reason would be
  a string a client parses, which makes every future rewording a breaking
  change — and, worse, tempts an implementation into putting internal state in
  it. `reasons` is the whole list."
  (:require [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.auth :as auth]
            [torihiki.oracle :as orc]))

(def reasons
  "Every rejection this API can produce. Closed set — see the ns docstring."
  #{:unknown-tx
    :unknown-market
    :missing-field
    :bad-account
    :bad-quantity
    :bad-price-level
    :bad-side
    :bad-flags
    :bad-amount
    :bad-trigger-direction
    :bad-trigger-price
    :bad-trigger-order
    :builder-fee-too-high
    :open-interest-cap
    :not-a-publisher
    :oracle-is-aggregated
    :not-the-bridge})

(defn- int-in? [v lo hi] (and (integer? v) (<= lo v) (<= v hi)))

(defn- market-exists? [ex m] (contains? (:markets ex) m))

(defn- n-levels [ex m] (:n-levels (get-in ex [:books m])))

(defn- validate-order-shape
  "Shared by `:order` and by a trigger's embedded order — the same fields mean
  the same things in both, and checking them in one place is what keeps them
  from drifting apart."
  [ex market {:keys [side level qty flags]}]
  (cond
    (not (int-in? side 0 1)) :bad-side
    (not (and (integer? qty) (pos? qty))) :bad-quantity
    (not (int-in? level 0 (dec (n-levels ex market)))) :bad-price-level
    (not (int-in? (or flags 0) 0 7)) :bad-flags
    :else nil))

(def ^:const max-builder-fee-rate
  "The most a builder may charge, as a rate against `fx/rate-scale`. Ten basis
  points.

  A builder fee is set by whoever wrote the client, and the trader signing the
  order is agreeing to a number they may not have read — so the cap is what
  stands between an interface earning from routing and an interface taking the
  account. Hyperliquid caps it for the same reason.

  An order over the cap is REFUSED rather than trimmed: trimming would fill the
  trader at a cost they did not agree to, which is the same harm arriving
  quietly.

  It lives here and not in `torihiki.state` because `state` requires this
  namespace and not the other way round — the cap is a validation policy, and
  putting it where the engine is would make the engine the thing that decides
  what a client may charge."
  1000000)

(def ^:const max-batch-prices
  "How many markets one `:oracle-submit-batch` may carry. **256.**

  A bound, not a target. The transaction is folded entry by entry into the
  same rule a single submission uses, so an unbounded batch is an unbounded
  amount of work bought with one signature verification — the cheapest way to
  make a block expensive. 256 is well past the market count this venue can
  hold in a Durable Object's memory, so the bound is not the thing that
  limits listing."
  256)

(defn validate
  "nil when `tx` may be applied, otherwise a keyword from `reasons`.

  Deliberately checks SHAPE and RANGE only — not whether the account can
  afford it. Affordability is a clearinghouse outcome (a withdrawal that
  exceeds free collateral is already a no-op there), and duplicating that
  judgement here would give two places to keep in agreement."
  [ex {:keys [tx account market] :as t}]
  (case tx
    (:order :cancel :cancel-all :amend :trigger :cancel-trigger :oracle
     :oracle-submit :funding-sample :funding-settle :liquidate)
    (cond
      (not (market-exists? ex market)) :unknown-market

      (= tx :order)
      (or (when-not (integer? account) :bad-account)
          ;; A builder fee is a number the trader may not have read. The cap is
          ;; what stands between an interface earning from routing and an
          ;; interface taking the account.
          (when (some? (:builder t))
            (cond
              (not (integer? (:builder t))) :bad-account
              (not (and (integer? (:builder-fee t)) (pos? (:builder-fee t))))
              :missing-field
              (> (:builder-fee t) max-builder-fee-rate) :builder-fee-too-high))
          (validate-order-shape ex market t)
          ;; Conservative on purpose: the check assumes the whole order could
          ;; open new interest, because whether it does depends on which side
          ;; the counterparty is on and that is not known until it matches.
          ;; Over-rejecting near the cap is the safe direction — the other one
          ;; discovers the breach only after it cannot be undone.
          (when-let [cap (get-in ex [:markets market :open-interest-cap])]
            (when (> (+ (cl/open-interest (:clearing ex) market) (:qty t)) cap)
              :open-interest-cap)))

      (= tx :cancel)
      ;; `nat-int?`, not `pos?`. An order id is `slot * gen-mod + generation`
      ;; (`bk/oid-of`), so the first order to occupy slot 0 has id 0 — a live,
      ;; cancellable order that this refused as a missing field. Observed on
      ;; the deployed chain: `/orders` returned `{"oid":0,...}` for a resting
      ;; order that nobody, including its owner, could cancel.
      (if-not (nat-int? (:oid t)) :missing-field nil)

      (= tx :cancel-all)
      ;; Nothing but the market and the signer, both already checked. There is
      ;; no id to get wrong, which is the point: a maker pulling out does not
      ;; want a transaction that can be refused for a shape reason.
      (when-not (integer? account) :bad-account)

      (= tx :amend)
      (cond
        (not (integer? account)) :bad-account
        (not (nat-int? (:oid t))) :missing-field
        ;; The new resting shape has to be a legal order in its own right —
        ;; the same check `:order` gets, because that is what an amend
        ;; becomes whenever it cannot keep its place in the queue.
        :else (validate-order-shape ex market (assoc t :side 0 :flags 0)))

      (= tx :cancel-trigger)
      (if-not (integer? (:id t)) :missing-field nil)

      (= tx :trigger)
      (cond
        (not (integer? account)) :bad-account
        (not (and (integer? (:trigger-price t)) (pos? (:trigger-price t))))
        :bad-trigger-price
        (not (contains? #{:above :below} (:direction t))) :bad-trigger-direction
        (not (map? (:order t))) :bad-trigger-order
        :else (when-let [r (validate-order-shape ex market (:order t))]
                ;; a bad embedded order is a bad trigger, reported as such —
                ;; the client asked for a trigger, not for an order
                (if (= r :bad-price-level) :bad-price-level :bad-trigger-order)))

      (= tx :oracle)
      (cond
        (not (and (integer? (:price t)) (pos? (:price t)))) :bad-price-level
        ;; With publishers configured, the direct setter is closed. Leaving
        ;; both doors open would make the aggregate decorative — an attacker
        ;; would simply use the one that does not aggregate.
        ;;
        ;; ONE exception: the authority may open a market that has never had a
        ;; price. A market listed on a running chain arrives unpriced, and
        ;; nothing can price it — publishers move a price, they do not create
        ;; one, and the direct setter is shut. So a listed market could be
        ;; traded and never margined, with a mark of zero, which is worse than
        ;; either door being open.
        ;;
        ;; It is not a second door into pricing: it closes behind itself the
        ;; moment it is used, because the condition is that there is no price
        ;; yet. The authority opens a market once; after that only publishers
        ;; move it.
        (and (seq (:oracle-publishers ex))
             (not (and (zero? (get-in ex [:oracle market] 0))
                       (some? (:bridge-authority ex))
                       (= account (:bridge-authority ex)))))
        :oracle-is-aggregated
        :else nil)

      (= tx :oracle-submit)
      (cond
        (not (integer? account)) :bad-account
        (not (and (integer? (:price t)) (pos? (:price t)))) :bad-price-level
        (not (contains? (:oracle-publishers ex) account)) :not-a-publisher
        :else nil)

      :else nil)

    :deposit
    (cond
      (not (integer? account)) :bad-account
      (not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount
      ;; Collateral has to come from somewhere. With a bridge authority
      ;; configured, only it may credit an account — a deposit is the chain
      ;; recording that value arrived somewhere else, and an account that can
      ;; credit itself is not depositing, it is minting.
      ;;
      ;; Everything the clearinghouse does — margin, liquidation, the
      ;; insurance fund, auto-deleveraging — is arithmetic over collateral. If
      ;; any account can conjure it, all of that arithmetic is exact and means
      ;; nothing, and the failure is invisible because every individual number
      ;; is correct.
      ;;
      ;; Configured closes the door, exactly as `:oracle-publishers` closes the
      ;; direct price setter above: leaving both open would make the authority
      ;; decorative, since an attacker would use the door that does not check.
      (let [bridge (:bridge-authority ex)]
        (and bridge (not= account bridge))) :not-the-bridge
      ;; Who the deposit pays. The signer is still the bridge — the
      ;; certificate backing this collateral is the bridge's own Ed25519
      ;; signature over the transaction, which the chain already verifies and
      ;; which its strictly sequential nonce already protects from replay.
      ;;
      ;; A second signing scheme (an authority-signed voucher carried inside a
      ;; user-submitted deposit) would need its own payload format, its own
      ;; spent-voucher set in the state root, and its own agreement between
      ;; client and node forever. This repo has said twice already why it does
      ;; not keep two implementations of a signed payload; the transaction
      ;; signature IS the certificate.
      ;;
      ;; Omitted, the deposit pays the signer, which is what it did before a
      ;; bridge could exist.
      (and (some? (:credit t)) (not (integer? (:credit t)))) :bad-account
      ;; Only a bridge may pay somebody else. Without this, any account could
      ;; credit any other on a chain with no authority configured — harmless
      ;; there in isolation, and a way to obscure where collateral came from
      ;; on a chain where the mint is supposed to be the only source.
      (and (some? (:credit t)) (nil? (:bridge-authority ex))
           (not= (:credit t) account)) :not-the-bridge
      :else nil)

    :withdraw
    (cond
      (not (integer? account)) :bad-account
      (not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount
      ;; Not gated: a holder withdrawing their own collateral is the one
      ;; movement that needs no authority beyond the signature, and the
      ;; clearinghouse already refuses to take more than free collateral.
      ;;
      ;; This no longer ends at the decrement. It raises a claim — a leaf, so
      ;; whoever holds the escrow can be handed an inclusion proof and pay
      ;; against it. See `torihiki.state/apply-tx :withdraw-settle`.
      :else nil)

    (:authorize-agent :revoke-agent)
    (cond
      (not (integer? account)) :bad-account
      (not (string? (:agent t))) :missing-field
      ;; A base64 Ed25519 public key is 44 characters. Checking the shape here
      ;; keeps an unusable authorisation out of the state root rather than
      ;; discovering it when a key that cannot verify tries to trade.
      (not= 44 (count (:agent t))) :missing-field
      (and (= tx :authorize-agent)
           (some? (:expires t))
           (not (and (integer? (:expires t)) (pos? (:expires t)))))
      :missing-field
      :else nil)

    (:bond :unbond)
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:validator t))) :bad-account
      (not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount
      :else nil)

    :collect-unbonded
    (if (integer? account) nil :bad-account)

    (:vault-deposit :vault-withdraw)
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:vault t))) :bad-account
      (and (= tx :vault-deposit)
           (not (and (integer? (:amount t)) (pos? (:amount t))))) :bad-amount
      (and (= tx :vault-withdraw)
           (not (and (integer? (:shares t)) (pos? (:shares t))))) :bad-quantity
      :else nil)

    :create-sub-account
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:sub t))) :bad-account
      :else nil)

    :transfer
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:to t))) :bad-account
      (not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount
      :else nil)

    :set-leverage
    (cond
      (not (integer? account)) :bad-account
      (not (contains? (:markets ex) market)) :unknown-market
      (not (and (integer? (:leverage t)) (pos? (:leverage t)))) :missing-field
      :else nil)

    :attest-reserves
    (cond
      (not (integer? account)) :bad-account
      (not (and (integer? (:amount t)) (not (neg? (:amount t))))) :bad-amount
      :else nil)

    ;; An escrow deposit observed on another chain.
    ;;
    ;; Refused HERE for shape, so a malformed attestation is answered rather
    ;; than silently ignored by `apply-tx`. Whether the attestor has weight and
    ;; whether a quorum agrees are consensus questions and stay there — this
    ;; only says the transaction is the shape it claims to be.
    :deposit-attest
    (cond
      (not (integer? account)) :bad-account
      (not (and (string? (:txid t)) (seq (:txid t)))) :missing-field
      (not (integer? (:credit t))) :bad-account
      (not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount
      (not (and (string? (:asset t)) (seq (:asset t)))) :missing-field
      :else nil)

    ;; Contract code, published into the chain.
    :evm-deploy
    (cond
      (not (integer? account)) :bad-account
      (not (and (string? (:code t)) (seq (:code t)))) :missing-field
      (odd? (count (:code t))) :missing-field
      :else nil)

    :set-referrer
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:referrer t))) :bad-account
      :else nil)

    :scale
    (cond
      (not (integer? account)) :bad-account
      (not (contains? (:markets ex) market)) :unknown-market
      (not (contains? #{0 1} (:side t))) :bad-side
      (not (and (integer? (:qty t)) (pos? (:qty t)))) :bad-quantity
      (not (and (integer? (:level t)) (not (neg? (:level t))))) :bad-price-level
      ;; A ladder of one is an order, and this is not the way to send one.
      (not (and (integer? (:count* t)) (> (:count* t) 1) (<= (:count* t) 50)))
      :missing-field
      ;; A step of zero stacks every rung on one level, which is an order for
      ;; count x qty wearing a ladder's clothes.
      (not (and (integer? (:step t)) (pos? (:step t)))) :missing-field
      :else nil)

    :twap
    (cond
      (not (integer? account)) :bad-account
      (not (contains? (:markets ex) market)) :unknown-market
      (not (contains? #{0 1} (:side t))) :bad-side
      (not (and (integer? (:qty t)) (pos? (:qty t)))) :bad-quantity
      ;; A schedule of one slice is an order, and a schedule of none is
      ;; nothing. Both are better refused than quietly turned into something
      ;; the trader did not ask for.
      (not (and (integer? (:slices t)) (> (:slices t) 1))) :missing-field
      (not (and (integer? (:every t)) (pos? (:every t)))) :missing-field
      ;; Every slice must be at least one lot, or the tail of the schedule
      ;; fires empty orders forever.
      (< (:qty t) (:slices t)) :bad-quantity
      :else nil)

    :cancel-twap
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:id t))) :missing-field
      :else nil)

    :amend-market
    (cond
      (not (integer? account)) :bad-account
      (not (contains? (:markets ex) market)) :unknown-market
      (not (map? (:spec t))) :missing-field
      :else nil)

    :set-oracle-params
    (cond
      (not (integer? account)) :bad-account
      (not (and (integer? (:quorum t)) (>= (:quorum t) 2))) :missing-field
      (not (and (integer? (:max-age t)) (pos? (:max-age t))
                (<= (:max-age t) orc/max-age-ceiling))) :missing-field
      :else nil)

    :oracle-submit-batch
    ;; The batch's own shape. Each entry is then checked by the same rule a
    ;; single `:oracle-submit` is checked by, because the batch is a way to
    ;; spend one nonce and not a second set of rules.
    (let [prices (:prices t)]
      (cond
        (not (integer? account)) :bad-account
        (not (and (map? prices) (seq prices))) :missing-field
        (> (count prices) max-batch-prices) :missing-field
        :else
        (some (fn [[market price]]
                (validate ex {:tx :oracle-submit :account account
                              :market market :price price}))
              (sort-by key prices))))

    :list-market
    (cond
      (not (integer? account)) :bad-account
      (not (and (integer? market) (pos? market))) :unknown-market
      ;; Already listed is refused here rather than in the engine so a client
      ;; sees why, and so the engine's no-op stays a safety net rather than
      ;; the only check.
      (contains? (:markets ex) market) :unknown-market
      (not (map? (:spec t))) :missing-field
      (not (and (integer? (:tick (:spec t))) (pos? (:tick (:spec t))))) :missing-field
      (not (and (integer? (:lot (:spec t))) (pos? (:lot (:spec t))))) :missing-field
      ;; The risk parameters, required.
      ;;
      ;; They used to be optional, and every one of them defaults to zero or
      ;; one at its read site — so a spec of `{:tick 1 :lot 1}` listed a market
      ;; with an initial margin rate of 0, a maintenance rate of 0, no fees and
      ;; no open-interest cap. Measured: an account holding 1000 collateral
      ;; opened a position of 10,000,000 notional on such a market — ten
      ;; thousand times its collateral — with `liquidatable?` false. That is
      ;; not a market, it is a mint.
      ;;
      ;; The specs this venue actually lists are safe only because they are
      ;; built by `clearing/market`, which derives these. **The transaction
      ;; path never calls it**, and `POST /markets` is not behind the admin
      ;; guard, so the constructor is not a check — it is a convention that
      ;; nothing enforces. This is the check.
      (not (and (integer? (:initial-margin-rate (:spec t)))
                (pos? (:initial-margin-rate (:spec t)))))
      :missing-field
      (not (and (integer? (:maintenance-margin-rate (:spec t)))
                (pos? (:maintenance-margin-rate (:spec t)))))
      :missing-field
      ;; Maintenance below initial, or a position is liquidatable the moment
      ;; it is opened.
      (not (< (:maintenance-margin-rate (:spec t))
              (:initial-margin-rate (:spec t))))
      :missing-field
      :else nil)

    (:withdraw-settle :withdraw-cancel)
    (cond
      (not (integer? account)) :bad-account
      (not (integer? (:claim t))) :missing-field
      ;; Who may settle and who may cancel is decided in `torihiki.state`,
      ;; where the claim itself is in hand: settling needs the bridge, and
      ;; cancelling needs the claim's owner. Neither question is answerable
      ;; from the transaction's shape, which is all this function judges.
      :else nil)

    :unknown-tx))

;; ── read models ─────────────────────────────────────────────────────────────
;;
;; What a client needs, shaped for a client. These deliberately do not hand
;; back the internals: a caller that reached into `:books` would be coupled to
;; the slab layout, and the terminal did exactly that before this existed.

(defn depth
  "Top `n` levels of one side, nearest the touch first, with a running
  cumulative size — the shape an order-book panel renders."
  [ex market side n]
  (let [book (get-in ex [:books market])]
    (loop [level (bk/best book side) i 0 cum 0 out []]
      (if (or (neg? level) (>= i n))
        out
        (let [q (bk/level-qty book side level)
              cum (+ cum q)]
          (recur (bk/next-occupied book side level) (inc i) cum
                 (conj out {:level level :qty q :cum cum})))))))

(defn book-snapshot
  [ex market n]
  {:market market
   :bids (depth ex market bk/bid n)
   :asks (depth ex market bk/ask n)
   :resting (bk/resting-count (get-in ex [:books market]))})

(defn fills-since
  "Fills produced in the current block from index `from` onward. The event
  buffer is reset per block, so this is a block-scoped view, not history — a
  client wanting history keeps its own, or replays the log."
  [ex market from]
  (vec (drop from (bk/fills (get-in ex [:books market])))))

(defn account-state
  "Everything a client needs to render a portfolio and decide what it may do
  next. `:free-collateral` is the number that answers 'can I open this', and
  it is computed here rather than by the client because getting it wrong is
  how a client offers a trade the chain will refuse."
  [ex account]
  (let [c (:clearing ex)
        marks (:marks ex)
        markets (:markets ex)]
    {:account account
     ;; The only nonce this account may sign next. A client that guesses gets
     ;; :bad-nonce, and a client that tracks its own drifts the moment anything
     ;; else submits for the account or a request is lost in flight — so the
     ;; chain says it, since the chain is the thing that decides.
     :next-nonce (auth/expected-nonce ex account)
     ;; Which key owns the id, or nil when it is unclaimed. Without this a
     ;; client cannot tell "nobody has taken this id" from "somebody else has",
     ;; and would discover the difference as a :wrong-key rejection after
     ;; signing.
     :bound-key (get-in ex [:account-keys account])
     :collateral (get-in c [:accounts account :collateral] 0)
     :equity (cl/equity c account marks)
     :initial-margin (cl/initial-margin c account marks markets)
     :maintenance-margin (cl/maintenance-margin c account marks markets)
     :free-collateral (cl/free-collateral c account marks markets)
     :liquidatable (boolean (cl/liquidatable? c account marks markets))
     :positions (into {}
                      (for [[m p] (get-in c [:accounts account :positions] {})
                            :when (not (zero? (:size p)))]
                        [m {:size (:size p)
                            :entry (cl/entry-price p)
                            :unrealized (cl/unrealized p (get marks m 0))
                            :isolated (:isolated p)}]))
     :triggers (vec (for [[m ts] (:triggers ex)
                          t ts
                          :when (= account (:account t))]
                      (assoc (select-keys t [:id :trigger-price :direction :order])
                             :market m)))}))

(defn market-info
  [ex market]
  (let [m (get-in ex [:markets market])]
    {:id market
     ;; What to call it. A read model that returns every rate and no name
     ;; makes the client invent one, and two clients invent two.
     :symbol (:symbol m)
     :open-interest (cl/open-interest (:clearing ex) market)
     :open-interest-cap (:open-interest-cap m)
     ;; The fee schedule, so a client can show what a trade will cost and what
     ;; volume would make it cost less. A venue that charges by tier and does
     ;; not publish the tiers is asking to be trusted about the price.
     :fee-tiers (mapv #(select-keys % [:min-volume :taker-fee-rate :maker-fee-rate])
                      (:fee-tiers m []))
     :margin-tiers (mapv #(select-keys % [:max-notional :max-leverage
                                          :initial-margin-rate
                                          :maintenance-margin-rate])
                         (:margin-tiers m []))
     :tick (:tick m)
     :lot (:lot m)
     :max-leverage (:max-leverage m)
     :initial-margin-rate (:initial-margin-rate m)
     :maintenance-margin-rate (:maintenance-margin-rate m)
     :taker-fee-rate (get m :taker-fee-rate 0)
     :maker-fee-rate (get m :maker-fee-rate 0)
     :oracle (get-in ex [:oracle market] 0)
     :oracle-stale (boolean (get-in ex [:oracle-stale market] false))
     :oracle-publishers (count (:oracle-publishers ex))
     :mark (get-in ex [:marks market] 0)
     :last (get-in ex [:last market] 0)
     :funding-rate (get-in ex [:last-funding-rate market] 0)}))
