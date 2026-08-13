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
            [torihiki.auth :as auth]))

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

(defn validate
  "nil when `tx` may be applied, otherwise a keyword from `reasons`.

  Deliberately checks SHAPE and RANGE only — not whether the account can
  afford it. Affordability is a clearinghouse outcome (a withdrawal that
  exceeds free collateral is already a no-op there), and duplicating that
  judgement here would give two places to keep in agreement."
  [ex {:keys [tx account market] :as t}]
  (case tx
    (:order :cancel :trigger :cancel-trigger :oracle :oracle-submit
     :funding-sample :funding-settle :liquidate)
    (cond
      (not (market-exists? ex market)) :unknown-market

      (= tx :order)
      (or (when-not (integer? account) :bad-account)
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
      ;; order that could not be cancelled by anyone, including its owner.
      (if-not (and (integer? (:oid t)) (nat-int? (:oid t))) :missing-field nil)

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
        (seq (:oracle-publishers ex)) :oracle-is-aggregated
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
      ;; The stated limit is that this decrements the balance and nothing pays
      ;; out anywhere — the other half is a bridge that does not exist yet.
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
     :open-interest (cl/open-interest (:clearing ex) market)
     :open-interest-cap (:open-interest-cap m)
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
