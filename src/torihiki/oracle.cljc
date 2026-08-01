(ns torihiki.oracle
  "Where the external price comes from, and why one publisher cannot set it.

  `torihiki.mark` anchors the mark to the oracle precisely so that a thin book
  cannot be used to liquidate people. That defence is only as good as the
  oracle: until this namespace existed, `:oracle` was a transaction carrying
  an already-agreed price, and whoever could send it moved the mark. The hole
  had simply been pushed one layer down.

  ## The median, not the mean

  The aggregate is the median of fresh submissions. A mean lets one publisher
  move the result by however much they are willing to lie — the error is
  proportional to the lie. With a median, a minority of publishers cannot move
  the result at all, however extreme their submissions, which is the property
  worth having.

  For an even count the LOWER middle is taken. Averaging the two middles would
  reintroduce an arithmetic mean over the two most pivotal submissions, and it
  would reintroduce a division and its rounding. Which of the two is chosen
  barely matters; that it is stated does.

  ## Quorum and staleness are the same defence

  A price is only aggregated from submissions that are both AUTHORISED and
  FRESH. Below the quorum the oracle does not update — and, importantly, it is
  marked stale rather than silently reused.

  A stale oracle stops liquidation. That is the whole reason to track it: the
  alternative is closing somebody's position at a price nobody currently
  vouches for. Refusing to liquidate has an obvious cost — bad debt can grow
  while the feed is down — and it is the smaller cost, because a liquidation
  is irreversible and waiting is not.

  ## Publishers are not governance

  The publisher set is configured at genesis and this namespace does not
  change it. Rotating publishers is a governance question, and answering it
  badly (say, letting the current set vote itself smaller) is worse than not
  answering it here."
  (:require [torihiki.fixed :as fx]))

(def default-params
  {;; how many fresh authorised submissions are needed to publish a price
   :quorum 3
   ;; logical seconds after which a submission stops counting
   :max-age 60})

(defn fresh
  "Submissions from authorised publishers that are not stale, as a vector of
  `[publisher price]` sorted by price then publisher.

  Sorted totally, because the median is read positionally: two validators that
  ordered equal prices differently could pick different publishers' values on
  an even count and diverge."
  [submissions publishers now max-age]
  (->> submissions
       (keep (fn [[pub {:keys [price ts]}]]
               (when (and (contains? publishers pub)
                          (pos? (or price 0))
                          (<= (- now ts) max-age))
                 [pub price])))
       (sort-by (juxt second first))
       vec))

(defn median
  "The lower middle of `sorted` (a vector of `[publisher price]`), or nil when
  empty. Lower middle rather than an average of the two middles — see the ns
  docstring."
  [sorted]
  (when (seq sorted)
    (second (nth sorted (quot (dec (count sorted)) 2)))))

(defn aggregate
  "Return `{:price p :n n :stale? false}` when quorum is met, or
  `{:price nil :n n :stale? true}` when it is not.

  Never returns a price computed from too few submissions. A price the system
  cannot vouch for is worse than no price, because everything downstream —
  the mark, margin, liquidation — will treat it as vouched for."
  [submissions publishers now {:keys [quorum max-age] :as _params}]
  (let [f (fresh submissions publishers now max-age)]
    (if (>= (count f) quorum)
      {:price (median f) :n (count f) :stale? false}
      {:price nil :n (count f) :stale? true})))

(defn deviation
  "How far `price` sits from `reference`, as a rate. Used to report how much a
  publisher disagrees with the aggregate — a monitoring signal, not an
  enforcement one. Slashing a publisher for disagreeing would punish the
  honest one during a genuine dislocation."
  [price reference]
  (if (pos? reference)
    (fx/fdiv (* (fx/abs* (- price reference)) fx/rate-scale) reference)
    0))
