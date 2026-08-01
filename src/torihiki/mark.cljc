(ns torihiki.mark
  "The mark price — what margin and liquidation are measured against.

  ## Why this namespace exists

  The first version of this engine used the LAST TRADED PRICE as the mark.
  That is not a small simplification, it is a way to liquidate other people:
  a single lot lifted through a thin book moves the mark, the mark decides who
  is under maintenance margin, and the attacker collects. The cost of the
  attack is one small fill; the payoff is somebody else's position. Every
  venue that has shipped this has been drained through it.

  So the mark is anchored to the oracle and allowed to move only as far as the
  BOOK CAN CARRY WEIGHT:

    mark = oracle + clamp(impact-mid - oracle, ±band)

  Two independent defences, and each one covers the other's weakness:

  1. IMPACT PRICES, NOT THE MID. `impact-mid` is the midpoint of what a
     reference-sized order would actually pay on each side
     (`torihiki.book/impact-price`), which is the same input the funding rate
     already uses. Quoting one lot at an absurd price does not move it,
     because one lot cannot fill the reference size. Moving it costs real
     size at real risk.

  2. A BAND AROUND THE ORACLE. Even an attacker willing to pay for real size
     can only drag the mark `band` away from the external price. That bounds
     the damage of a successful manipulation to a number the operator chooses,
     rather than to whatever the book can be pushed to.

  When either side is too thin to absorb the reference size, the mark is the
  oracle outright. A book that cannot price itself does not get to.

  ## What the mark is not

  It is not the last trade, and `torihiki.state` keeps that separately as
  `:last` for display. Showing a trader the last print is right; margining
  them against it is not."
  (:require [torihiki.fixed :as fx]
            [torihiki.book :as bk]))

(def default-params
  {;; how far the mark may sit from the oracle, in torihiki.fixed's 1e9 scale
   :band (fx/bps 50)
   ;; the order size the book is asked to price, in quote notional — the same
   ;; reference the funding premium uses, for the same reason
   :impact-notional 20000})

(defn impact-mid
  "Midpoint of the two impact prices, or nil when either side cannot absorb
  the reference size. nil is deliberate: it means 'the book has no opinion I
  am willing to act on', which is different from zero."
  [book impact-notional]
  (let [b (bk/impact-price book bk/bid impact-notional)
        a (bk/impact-price book bk/ask impact-notional)]
    (when (and (pos? b) (pos? a))
      (fx/fdiv (+ a b) 2))))

(defn mark-price
  "The mark for one market. Falls back to the oracle whenever the book cannot
  price the reference size."
  [book oracle {:keys [band impact-notional] :as _params}]
  (if-not (pos? oracle)
    0
    (if-let [m (impact-mid book impact-notional)]
      (let [limit (fx/mul-rate oracle band)]
        (+ oracle (fx/clamp (- m oracle) (- limit) limit)))
      oracle)))
