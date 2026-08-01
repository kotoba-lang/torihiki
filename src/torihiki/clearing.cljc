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
  never be configured inconsistently."
  [{:keys [id max-leverage tick lot]}]
  (let [initial (fx/fdiv fx/rate-scale max-leverage)]
    {:id id
     :tick tick
     :lot lot
     :max-leverage max-leverage
     :initial-margin-rate initial
     ;; half the initial margin at max leverage — Hyperliquid's rule
     :maintenance-margin-rate (fx/fdiv initial 2)}))

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

(defn apply-fill
  "Credit one fill to one account. `delta` is signed lots, `fee-rate` is
  applied to the traded notional. Returns the new state."
  [state acct mkt delta price fee-rate]
  (let [pos (position state acct mkt)
        [pos' realized] (apply-fill-to-position pos delta price)
        fee (fx/mul-rate (fx/abs* (fx/notional price delta)) fee-rate)]
    (-> state
        (assoc-in [:accounts acct :positions mkt] pos')
        (update-in [:accounts acct :collateral] (fnil + 0) (- realized fee))
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
  "What the account's cross positions must be backed by to stay open."
  [state acct marks markets]
  (reduce (fn [acc [mkt pos]]
            (+ acc (fx/mul-rate
                    (fx/abs* (fx/notional (get marks mkt 0) (:size pos)))
                    (get-in markets [mkt :maintenance-margin-rate] 0))))
          0
          (cross-positions state acct)))

(defn initial-margin
  [state acct marks markets]
  (reduce (fn [acc [mkt pos]]
            (+ acc (fx/mul-rate
                    (fx/abs* (fx/notional (get marks mkt 0) (:size pos)))
                    (get-in markets [mkt :initial-margin-rate] 0))))
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

(defn deposit [state acct amount]
  (update-in state [:accounts acct :collateral] (fnil + 0) (fx/check :deposit amount)))

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
