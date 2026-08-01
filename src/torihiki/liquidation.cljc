(ns torihiki.liquidation
  "Liquidation, the backstop vault, the insurance fund, and auto-deleveraging.

  This is the layer that decides who absorbs a loss when a trader cannot, and
  it is the one place in the engine where a bug does not merely corrupt state
  but destroys money. It is written as a four-stage waterfall, mirroring
  Hyperliquid's published behaviour, because each stage exists to stop the
  next one from being needed:

    1. LIQUIDATE INTO THE BOOK. Send the position to market. If the book
       absorbs it above the bankruptcy price, nobody else is involved.

    2. THE BACKSTOP VAULT takes over what the book would not absorb, at the
       mark-based liquidation price.

    3. THE INSURANCE FUND covers the shortfall when even the vault cannot
       close above bankruptcy.

    4. AUTO-DELEVERAGING closes profitable counterparties, ranked by
       unrealized profit times effective leverage, and only when the three
       stages above are exhausted. ADL takes money from traders who did
       nothing wrong, which is why it is last and why its ranking has to be
       stated rather than improvised.

  ## Partial liquidation

  A large position is not dumped at once. Positions above a notional
  threshold are liquidated 20% at a time with a cooldown between slices,
  because a market order that eats its own book turns a recoverable margin
  breach into a cascade — the liquidation itself moves the price against the
  position it is trying to close.

  The cooldown is measured in the state machine's own logical time (block
  timestamps, supplied by the caller), never a wall clock: two validators
  reading their own clocks would disagree about whether a cooldown had
  elapsed and would liquidate different positions."
  (:require [torihiki.fixed :as fx]
            [torihiki.clearing :as cl]))

(def default-params
  {;; positions above this notional liquidate in slices
   :partial-threshold 100000
   ;; the fraction of a large position closed per slice
   :partial-fraction (fx/pct 20)
   ;; logical seconds between slices
   :cooldown 30
   ;; share of the liquidated notional paid into the insurance fund
   :liquidation-fee (fx/bps 10)})

(defn bankruptcy-price
  "The price at which the position's loss exactly consumes its margin — below
  it (for a long) the account is not merely liquidatable but insolvent, and
  someone other than the trader is going to absorb the difference."
  [{:keys [size entry-notional]} collateral]
  (if (zero? size)
    0
    (fx/fdiv (- entry-notional collateral) size)))

(defn slice-size
  "How much of a position to close in this liquidation step. Small positions
  go in one shot; large ones go 20% at a time. Always returns a magnitude of
  at least one lot, so a position can never become unliquidatable by being
  small enough that its slice rounds to zero."
  [size mark {:keys [partial-threshold partial-fraction]}]
  (let [magnitude (fx/abs* size)
        notional (fx/abs* (fx/notional mark size))]
    (if (<= notional partial-threshold)
      magnitude
      (max 1 (fx/mul-rate magnitude partial-fraction)))))

(defn cooling-down?
  "True when this account/market was liquidated too recently to slice again.
  `now` is logical time from the block, not a clock."
  [state acct mkt now {:keys [cooldown]}]
  (let [last (get-in state [:liquidation-clock [acct mkt]])]
    (and last (< (- now last) cooldown))))

;; ── the waterfall ───────────────────────────────────────────────────────────

(defn- transfer-position
  "Apply the closing `delta` to `from` and the opposite side to `to`, at
  `price`, with no fee on either side. Used to hand a position to the backstop
  vault: the vault is an ordinary account, so a position it absorbs is subject
  to exactly the same margin arithmetic as anyone else's rather than to a
  privileged code path.

  The direction is the thing to get right, and the first version had it
  backwards: `delta` is the fill that CLOSES the liquidated position, so it
  goes to `from`, and `to` takes the other side. Giving `from` the negated
  delta doubled the position it was supposed to be closing — silently, because
  nothing tested past stage 1."
  [state from to mkt delta price]
  (-> state
      (cl/apply-fill from mkt delta price 0)
      (cl/apply-fill to mkt (- delta) price 0)))

(defn execute-adl
  "Close `to-close` lots of the vault's inherited position against the ranked
  counterparties, at the bankruptcy price.

  This is the fourth and last stage, and it is the only one that takes money
  from people who did nothing wrong. Closing them at the BANKRUPTCY price
  rather than the mark is what makes it a transfer rather than a gift: the
  counterparty gives up exactly the profit the insolvent account could not
  pay, and no more.

  Returns `{:state :closed :unfilled}`. `:unfilled` is the part no
  counterparty could absorb — it stays with the vault, which is the honest
  outcome and not a silent one."
  [state vault mkt price to-close ranked]
  (loop [state state
         remaining (fx/abs* to-close)
         [c & more] ranked
         closed []]
    (if (or (not (pos? remaining)) (nil? c))
      {:state state :closed closed :unfilled remaining}
      (let [q (min remaining (fx/abs* (:size c)))
            ;; the counterparty moves toward flat; the vault takes the other
            ;; side and moves toward flat too
            cp-delta (if (neg? (:size c)) q (- q))]
        (recur (-> state
                   (cl/apply-fill (:account c) mkt cp-delta price 0)
                   (cl/apply-fill vault mkt (- cp-delta) price 0))
               (- remaining q)
               more
               (conj closed {:account (:account c) :qty q}))))))

(defn adl-ranking
  "Counterparties eligible for auto-deleveraging on `mkt`, worst first.

  Ranked by unrealized profit times effective leverage, following
  Hyperliquid's published rule: the most profitable and most leveraged
  traders are closed first. Only accounts on the OPPOSITE side of the
  position being closed can absorb it.

  Ties are broken by account id so the ranking is total. Without that a
  ranking is only a partial order, and two validators sorting equal-scored
  accounts differently would deleverage different traders — a consensus
  failure whose symptom is that some traders' positions vanish on one node
  and not another."
  [state mkt mark opposite-of]
  (->> (get-in state [:accounts])
       (keep (fn [[acct {:keys [positions collateral]}]]
               (let [pos (get positions mkt cl/flat)
                     size (:size pos)]
                 (when (and (not (zero? size))
                            (not= (neg? size) (neg? opposite-of))
                            (pos? (cl/unrealized pos mark)))
                   (let [profit (cl/unrealized pos mark)
                         notional (fx/abs* (fx/notional mark size))
                         base (max 1 (+ (or collateral 0) profit))
                         leverage (fx/fdiv (* notional fx/rate-scale) base)]
                     {:account acct
                      :size size
                      :score (fx/fdiv (* profit leverage) fx/rate-scale)})))))
       (sort-by (juxt (comp - :score) :account))
       vec))

(defn liquidate
  "Run the waterfall for one account and market. Returns
  `{:state .. :closed .. :stage ..}` where `:stage` names how far down the
  waterfall the close had to go — `:book`, `:vault`, `:insurance`, or `:adl`.

  `take-fn` is how the caller offers the position to the book: it receives
  the signed size to close and the mark, and returns `[filled-lots
  avg-price]`, or `[0 _]` if the book took nothing. Injecting it keeps this
  namespace pure and testable without a book, and keeps the book from having
  to know what a liquidation is."
  [state acct mkt mark now markets params take-fn]
  (let [pos (cl/position state acct mkt)
        size (:size pos)]
    (cond
      (zero? size)
      {:state state :closed 0 :stage :none}

      (cooling-down? state acct mkt now params)
      {:state state :closed 0 :stage :cooldown}

      :else
      (let [magnitude (slice-size size mark params)
            ;; closing a long means selling: the delta is opposite the position
            delta (if (pos? size) (- magnitude) magnitude)
            [filled avg-price] (take-fn delta mark)
            collateral (get-in state [:accounts acct :collateral] 0)
            bankruptcy (bankruptcy-price pos collateral)
            state (assoc-in state [:liquidation-clock [acct mkt]] now)]
        (if (and (pos? (fx/abs* filled))
                 (if (pos? size) (>= avg-price bankruptcy) (<= avg-price bankruptcy)))
          ;; stage 1: the book absorbed it above bankruptcy
          (let [fee (fx/mul-rate (fx/abs* (fx/notional avg-price filled))
                                 (:liquidation-fee params))]
            {:state (-> state
                        (cl/apply-fill acct mkt filled avg-price 0)
                        (update-in [:accounts acct :collateral] (fnil - 0) fee)
                        (update :insurance-fund (fnil + 0) fee))
             :closed filled
             :stage :book})
          ;; stage 2: hand the slice to the backstop vault at the mark
          (let [vault (:backstop-vault state ::vault)
                state' (transfer-position state acct vault mkt delta mark)
                ;; A shortfall is how far the account went NEGATIVE — money it
                ;; owes and cannot pay. Positive number, because every stage
                ;; below subtracts it from somebody.
                shortfall (max 0 (- (get-in state' [:accounts acct :collateral] 0)))]
            (if (zero? shortfall)
              {:state state' :closed delta :stage :vault}
              ;; stage 3: the insurance fund makes the vault whole
              (let [fund (get state' :insurance-fund 0)
                    covered (min fund shortfall)
                    state'' (-> state'
                                (update :insurance-fund - covered)
                                (update-in [:accounts acct :collateral] (fnil + 0) covered))]
                (if (= covered shortfall)
                  {:state state'' :closed delta :stage :insurance :covered covered}
                  ;; stage 4: socialise the remainder onto profitable
                  ;; counterparties, worst-ranked first
                  (let [ranked (adl-ranking state'' mkt mark size)
                        bankruptcy (max 1 bankruptcy)
                        r (execute-adl state'' vault mkt bankruptcy
                                       (- delta) ranked)]
                    {:state (assoc (:state r) :adl-queue ranked)
                     :closed delta
                     :stage :adl
                     :adl-closed (:closed r)
                     :adl-unfilled (:unfilled r)
                     :covered covered}))))))))))

(defn scan
  "Every account currently liquidatable on `mkt`, in a deterministic order.

  Sorted by account id, not by map order: `keys` on a Clojure map has no
  specified order, and liquidating in a different order on two validators
  produces different fills, different marks, and a fork. The sort is the
  cheapest possible insurance against that and it runs once per block."
  [state mkt marks markets]
  (->> (keys (:accounts state))
       sort
       (filter #(or (cl/liquidatable? state % marks markets)
                    (cl/isolated-liquidatable? state % mkt marks markets)))
       vec))
