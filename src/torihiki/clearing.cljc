(ns torihiki.clearing
  "The perpetuals clearinghouse: positions, margin, and the equity test that
  decides whether an account is still solvent.

  Everything here is a pure function of an immutable state map. Unlike
  `torihiki.book`, which mutates preallocated arrays because it is called
  hundreds of thousands of times a second, the clearinghouse runs once per
  fill and once per mark-price update, so ordinary Clojure data is both fast
  enough and much easier to reason about — and reasoning about it is the
  point, because THIS is the layer where money is actually lost.

  ## Model

  An account holds `:collateral` (integer, in the quote asset's smallest unit)
  and a map of `:positions` keyed by market id. A position is

    {:size <signed lots>          ; positive = long, negative = short
     :entry-notional <signed>     ; running cost basis, same sign as size
     :isolated <collateral or nil>}

  Cost basis is carried as a NOTIONAL rather than as an average entry price,
  because an average price forces a division on every fill and a division
  forces a rounding decision on every fill. Accumulating the notional exactly
  and dividing only when someone asks for the entry price moves all the
  rounding to one place.

  ## Margin

  Following Hyperliquid's published parameters: maintenance margin is half the
  initial margin at maximum leverage, so a market that allows 40x has 2.5%
  initial and 1.25% maintenance, and one that allows 3x has 33.3% initial and
  16.7% maintenance. An account is liquidatable when its equity falls below
  the sum of its positions' maintenance requirements.

  Cross margin pools collateral across every position. Isolated margin fences
  a stated amount to one position, so that position's liquidation cannot reach
  the rest of the account — which also means its equity test must exclude the
  cross pool entirely."
  (:require [torihiki.fixed :as fx]))

;; ── markets ─────────────────────────────────────────────────────────────────

(defn market
  "A market's risk parameters. `max-leverage` is the only number an operator
  sets; initial and maintenance margin are derived from it so the pair can
  never be configured inconsistently.

  `margin-tiers` is optional: a vector of `{:max-notional n :max-leverage l}`
  in ascending order of size. A position pays the rate of the first tier whose
  `:max-notional` it fits inside; anything past the last tier pays the last
  tier's rate. Without tiers every position pays the same rate regardless of
  size, which is how a large position ends up under-collateralised — the book
  cannot absorb it at the price the margin assumed, and the difference is paid
  by the insurance fund or by ADL.

  `open-interest-cap` is optional and is checked by `torihiki.api`.

  `symbol` is what a human calls this market. Not used by any arithmetic here
  and committed to the state root all the same: it is what a terminal puts on
  a trade ticket, so two replicas that disagreed about it would label the same
  position two different things."
  [{:keys [id symbol max-leverage tick lot margin-tiers open-interest-cap]}]
  (let [initial (fx/fdiv fx/rate-scale max-leverage)]
    (cond-> {:id id
             :symbol (or symbol (str "MKT-" id))
             :tick tick
             :lot lot
             :max-leverage max-leverage
             :initial-margin-rate initial
             ;; half the initial margin at max leverage — Hyperliquid's rule
             :maintenance-margin-rate (fx/fdiv initial 2)}
      margin-tiers
      (assoc :margin-tiers
             (mapv (fn [{:keys [max-notional max-leverage]}]
                     (let [im (fx/fdiv fx/rate-scale max-leverage)]
                       {:max-notional max-notional
                        :max-leverage max-leverage
                        :initial-margin-rate im
                        :maintenance-margin-rate (fx/fdiv im 2)}))
                   (sort-by :max-notional margin-tiers)))
      open-interest-cap (assoc :open-interest-cap open-interest-cap))))

(defn tier-for
  "The tier a position of `notional` falls into, or nil when the market has no
  tiers. Past the last tier the last tier applies — a position does not escape
  the margin schedule by being bigger than anyone anticipated."
  [market notional]
  (when-let [tiers (seq (:margin-tiers market))]
    (or (first (filter #(<= notional (:max-notional %)) tiers))
        (last tiers))))

(defn rates-for
  "[initial maintenance] for a position of `notional` in `market`."
  [market notional]
  (if-let [t (tier-for market notional)]
    [(:initial-margin-rate t) (:maintenance-margin-rate t)]
    [(:initial-margin-rate market 0) (:maintenance-margin-rate market 0)]))

;; ── positions ───────────────────────────────────────────────────────────────

(def flat {:size 0 :entry-notional 0 :isolated nil})

(defn position [state acct mkt]
  (get-in state [:accounts acct :positions mkt] flat))

(defn entry-price
  "Average entry price in ticks, or 0 for a flat position. The only division
  in the cost-basis path, deliberately."
  [{:keys [size entry-notional]}]
  (if (zero? size) 0 (fx/fdiv entry-notional size)))

(defn unrealized
  "Mark-to-market profit or loss, signed, in the quote unit.

  `(mark - entry) * size` expands to `mark*size - entry-notional`, which
  avoids recovering the entry price — and therefore avoids reintroducing the
  rounding that `entry-price` concentrates in one place."
  [{:keys [size entry-notional]} mark]
  (- (fx/notional mark size) entry-notional))

(defn- apply-fill-to-position
  "Fold one fill into a position. `delta` is signed: positive when the account
  bought. Returns [position' realized-pnl].

  Three cases, and the middle one is where implementations usually go wrong:

  - INCREASING (or opening): cost basis simply accumulates.
  - REDUCING: the closed portion realises PnL against the average entry, and
    the remaining basis shrinks proportionally. Taking the closed portion's
    basis as `entry-price * closed` rather than pro-rating the stored notional
    would leave a residue in `entry-notional` that never goes away, so a
    position that returned to flat would still carry a phantom cost basis.
  - FLIPPING: the position crosses through zero, so it must be split — realise
    everything against the old side, then open the remainder at the fill price
    with a fresh basis."
  [{:keys [size entry-notional] :as pos} delta price]
  (let [new-size (+ size delta)]
    (cond
      ;; opening from flat, or adding in the same direction
      (or (zero? size) (= (neg? size) (neg? delta)))
      [(assoc pos :size new-size
                  :entry-notional (+ entry-notional (fx/notional price delta)))
       0]

      ;; the fill crosses through zero: close the old side entirely, reopen
      (or (and (pos? size) (neg? new-size)) (and (neg? size) (pos? new-size)))
      ;; `closed` is the POSITION being closed (same sign as size), not the
      ;; delta that closes it. Using the delta here inverts the sign of every
      ;; realised PnL on a flip: a long that closed at a profit books a loss.
      (let [realized (- (fx/notional price size) entry-notional)]
        [(assoc pos :size new-size
                    :entry-notional (fx/notional price new-size))
         realized])

      ;; reducing without crossing zero
      :else
      (let [closed (- delta)                                 ; same sign as size
            closed-basis (fx/fdiv (* entry-notional closed) size)
            realized (- (fx/notional price closed) closed-basis)]
        [(assoc pos :size new-size
                    :entry-notional (- entry-notional closed-basis))
         realized]))))

(defn open-interest
  "Total long exposure in `mkt`, which equals total short exposure because a
  perp market nets to zero. Maintained incrementally rather than summed on
  demand: summing would make a risk check cost a walk of every account."
  [state mkt]
  (get-in state [:open-interest mkt] 0))

(defn apply-fill
  "Credit one fill to one account. `delta` is signed lots, `fee-rate` is
  applied to the traded notional. Returns the new state."
  [state acct mkt delta price fee-rate]
  (let [pos (position state acct mkt)
        [pos' realized] (apply-fill-to-position pos delta price)
        fee (fx/mul-rate (fx/abs* (fx/notional price delta)) fee-rate)
        ;; open interest counts the long side only; the delta is exact because
        ;; it is the change in THIS account's long exposure
        oi-delta (- (max 0 (:size pos')) (max 0 (:size pos)))]
    (-> state
        (assoc-in [:accounts acct :positions mkt] pos')
        (update-in [:accounts acct :collateral] (fnil + 0) (- realized fee))
        (update-in [:open-interest mkt] (fnil + 0) oi-delta)
        (update :fees-collected (fnil + 0) fee))))

;; ── equity and margin ───────────────────────────────────────────────────────

(defn- cross-positions
  [state acct]
  (->> (get-in state [:accounts acct :positions] {})
       (remove (fn [[_ p]] (:isolated p)))))

(defn equity
  "Collateral plus unrealized PnL across every CROSS position. Isolated
  positions are excluded by construction — their collateral is fenced off and
  their losses must not be able to reach the cross pool."
  [state acct marks]
  (reduce (fn [acc [mkt pos]]
            (+ acc (unrealized pos (get marks mkt 0))))
          (get-in state [:accounts acct :collateral] 0)
          (cross-positions state acct)))

(defn maintenance-margin
  "What the account's cross positions must be backed by to stay open. The rate
  comes from the position's own tier, so a larger position is held to a
  stricter one."
  [state acct marks markets]
  (reduce (fn [acc [mkt pos]]
            (let [n (fx/abs* (fx/notional (get marks mkt 0) (:size pos)))
                  [_ mm] (rates-for (get markets mkt) n)]
              (+ acc (fx/mul-rate n mm))))
          0
          (cross-positions state acct)))

(defn initial-margin
  [state acct marks markets]
  (reduce (fn [acc [mkt pos]]
            (let [n (fx/abs* (fx/notional (get marks mkt 0) (:size pos)))
                  [im _] (rates-for (get markets mkt) n)]
              (+ acc (fx/mul-rate n im))))
          0
          (cross-positions state acct)))

(defn liquidatable?
  "The solvency test. Strictly less-than: an account sitting exactly at its
  maintenance requirement is not yet liquidated."
  [state acct marks markets]
  (and (seq (cross-positions state acct))
       (< (equity state acct marks)
          (maintenance-margin state acct marks markets))))

(defn isolated-liquidatable?
  "The same test for one isolated position, against its own fenced margin."
  [state acct mkt marks markets]
  (let [pos (position state acct mkt)]
    (boolean
     (when-let [iso (:isolated pos)]
       (and (not (zero? (:size pos)))
            (< (+ iso (unrealized pos (get marks mkt 0)))
               (fx/mul-rate (fx/abs* (fx/notional (get marks mkt 0) (:size pos)))
                            (get-in markets [mkt :maintenance-margin-rate] 0))))))))

(defn free-collateral
  "What the account may withdraw or commit to new positions: equity less the
  initial margin its open positions already consume. Never negative."
  [state acct marks markets]
  (max 0 (- (equity state acct marks)
            (initial-margin state acct marks markets))))

;; ── account admin ───────────────────────────────────────────────────────────

(def ^:const volume-epoch-blocks
  "How many blocks one volume epoch spans.

  Fee tiers are supposed to reward RECENT activity, so the number they read has
  to fall as well as rise. A cumulative-forever total only ratchets: an account
  that traded once, years ago, keeps the discount it earned then.

  A rolling window needs a clock and the engine has none — so the window is
  blocks. Volume is kept per epoch and the tier reads the current epoch plus
  the previous one, which is a window between one and two epochs wide
  depending on where in the current one you ask. That wobble is the price of
  not having a clock, and it is bounded and identical on every replica, which
  a wall-clock window would not be."
  20000)

(defn volume-of
  "The rolling notional `acct` has traded: this epoch plus the last one.

  Zero for an account whose record is from an epoch that has since passed —
  the volume did not move to `:prev`, because nothing has traded to move it."
  [state acct height]
  (let [e (quot (long height) volume-epoch-blocks)
        {:keys [epoch cur prev] :or {epoch 0 cur 0 prev 0}}
        (get-in state [:volume acct])]
    (cond
      (= epoch e) (+ cur prev)
      (= epoch (dec e)) cur
      :else 0)))

(defn add-volume
  "Record `notional` traded by `acct` at `height`.

  The absent record and the current-epoch record are deliberately NOT the same
  branch. Reading defaults out of a missing map and then `update`-ing that
  missing map is how the first version threw a NullPointerException on the
  very first fill of a fresh chain — the defaults made the code look total
  while the map it went on to update was still nil."
  [state acct height notional]
  (let [e (quot (long height) volume-epoch-blocks)
        rec (get-in state [:volume acct])]
    (assoc-in state [:volume acct]
              (cond
                (nil? rec) {:epoch e :cur notional :prev 0}
                (= (:epoch rec 0) e) (update rec :cur (fnil + 0) notional)
                ;; One epoch on: what was current becomes previous.
                (= (:epoch rec 0) (dec e)) {:epoch e :cur notional :prev (:cur rec 0)}
                ;; Further than that and both windows are stale, so neither is
                ;; carried — a gap in trading is a gap in volume.
                :else {:epoch e :cur notional :prev 0}))))

(defn fee-rates-for
  "`[taker maker]` for `acct` on `market`, given its rolling volume.

  A market with no `:fee-tiers` charges its flat rates, which is what every
  market did before this existed. Tiers are ascending by `:min-volume` and the
  LAST one the account clears applies — the same shape `margin-tiers` already
  has, and for the same reason: a schedule read from the wrong end is a
  discount handed to the smallest account."
  [state acct market-spec height]
  (let [tiers (:fee-tiers market-spec)
        base [(get market-spec :taker-fee-rate 0) (get market-spec :maker-fee-rate 0)]]
    (if (empty? tiers)
      base
      (let [v (volume-of state acct height)
            t (last (filter #(<= (:min-volume %) v) tiers))]
        (if t
          [(get t :taker-fee-rate (first base)) (get t :maker-fee-rate (second base))]
          base)))))

(defn pay-builder
  "Move `amount` from `payer` to `builder`.

  A transfer, not a fee: it does not touch `:fees-collected`, because the
  venue is not the one charging it. Whoever wrote the client is, and the
  trader agreed to it by signing the order — so the money goes to them and
  the exchange's books show none of it as income.

  Refused, rather than clamped, when the payer cannot afford it: an order that
  fills and then partially pays its builder would leave a debt with no name.
  `torihiki.api` caps the rate so this is rare; making it a no-op keeps a
  builder from being paid out of collateral that is backing a position."
  [state payer builder amount]
  (let [amount (long amount)]
    (if (or (not (pos? amount))
            (= payer builder)
            (> amount (get-in state [:accounts payer :collateral] 0)))
      state
      (-> state
          (update-in [:accounts payer :collateral] - amount)
          (update-in [:accounts builder :collateral] (fnil + 0) amount)))))

(defn settle-deficit
  "Move an account's negative collateral into `:deficit`, leaving collateral at
  zero. Idempotent, and a no-op on an account that is not underwater.

  ## Why the debt moves instead of staying where it was

  Collateral going negative is how the liquidation waterfall records a hole:
  `torihiki.liquidation` hands the position to the vault at the mark, and when
  that costs more than the account had, the difference sits on the account as
  a negative number. The insurance fund adds back what it can cover; anything
  it cannot stays negative forever.

  A negative balance is a true statement in the wrong field. It is a DEBT, and
  storing it as collateral means the one number an exchange must be able to
  add up — what it owes its users — cannot be added up, because part of the
  sum is a hole rather than a holding. `torihiki.commit` had to keep every
  merkle-sum leaf at zero for exactly this reason: a sum that is only valid
  while nobody is underwater is a claim that fails when it matters most.

  Split into two non-negative fields, both are true and both are usable.
  `:collateral` is what the account has and what reserves must cover;
  `:deficit` is what it owes and what a later deposit pays down first. The
  identity `collateral - deficit` is the old single number, so nothing is
  invented and nothing is forgiven.

  Bad debt does NOT reduce equity. It was already absorbed — by the insurance
  fund, or socialised onto counterparties by auto-deleveraging — so charging
  it to the account again would double-count the loss AND put equity back
  below zero, which is the state this exists to remove."
  [state acct]
  (let [c (get-in state [:accounts acct :collateral] 0)]
    (if (neg? c)
      (-> state
          (assoc-in [:accounts acct :collateral] 0)
          (update-in [:accounts acct :deficit] (fnil + 0) (fx/check :deficit (- c))))
      state)))

(defn deposit
  "Credit `amount`, paying down any bad debt first.

  A deposit into an account that owes the system is a repayment before it is a
  balance. Crediting collateral while leaving the deficit standing would let
  the same account trade on new money while its old hole stayed on the books."
  [state acct amount]
  (let [amount (fx/check :deposit amount)
        owed (get-in state [:accounts acct :deficit] 0)
        repaid (min owed amount)]
    (cond-> state
      (pos? repaid) (update-in [:accounts acct :deficit] - repaid)
      true (update-in [:accounts acct :collateral] (fnil + 0) (- amount repaid)))))

(defn withdraw
  "Withdrawals are refused unless free collateral covers them. Returning the
  state unchanged rather than throwing keeps this usable directly as a
  transaction handler: a rejected withdrawal is a no-op block entry, not a
  halted chain."
  [state acct amount marks markets]
  (if (<= amount (free-collateral state acct marks markets))
    (update-in state [:accounts acct :collateral] (fnil - 0) amount)
    state))

(defn new-state []
  {:accounts {} :fees-collected 0})

;; ── reduce-only ─────────────────────────────────────────────────────────────

(defn reducing-qty
  "How much of `qty` on `side` would actually REDUCE the account's position in
  `mkt` — 0 when the order would only increase it.

  `side` follows the book's convention: 0 buys, 1 sells. A sell reduces a
  long, a buy reduces a short, and neither can reduce a flat position.

  Clamping rather than rejecting outright is deliberate. A trader closing a
  position often sends slightly more size than they hold — because the
  position moved between reading it and sending, or because they rounded. The
  intent is unambiguous ('get me out'), so honour the intent and cap the size.
  What must never happen is the excess silently opening a position on the
  other side, which is exactly what reduce-only exists to prevent."
  [state acct mkt side qty]
  (let [size (:size (position state acct mkt))]
    (cond
      (zero? size) 0
      ;; selling reduces a long
      (and (= side 1) (pos? size)) (min qty size)
      ;; buying reduces a short
      (and (= side 0) (neg? size)) (min qty (- size))
      :else 0)))
