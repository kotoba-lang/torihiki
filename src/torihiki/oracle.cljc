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
   ;; How long a submission keeps counting, in the SAME unit as `:ts`.
   ;;
   ;; `:ts` is not a wall clock. A block's ts is its parent's plus the
   ;; consensus layer's `:block-interval`, which is 100 — so ts advances 100
   ;; per block and nothing else moves it. That makes the old value of 60 a
   ;; window of **0.6 blocks**: a quorum could only ever form from three
   ;; publishers whose submissions landed in the SAME block, for every market,
   ;; in every block. Nothing published prices at all while that was true, so
   ;; it cost nothing and was never noticed; it would have made the first
   ;; price feed look broken for a reason nowhere near the price feed.
   ;;
   ;; 3000 is thirty blocks. Long enough that a publisher whose transaction
   ;; misses a block still counts, short enough that a publisher that has
   ;; stopped is dropped well before anybody trades on its last word. The
   ;; number is a window in blocks; the unit is what matters here, not the
   ;; exact count.
   :max-age 3000})

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

(defn weighted-median
  "The price at which half the STAKE sits below, or nil when empty.

  A plain median counts publishers; this counts what they have at risk. The
  difference is who has to collude: with one publisher one vote, a majority of
  publishers moves the price however small they are, and publishers are cheap
  to create. Weighting by stake makes the cost of moving the price the cost of
  acquiring the stake, which is the property the bond was posted for.

  `stake-of` returns a publisher's stake; a publisher with none contributes
  nothing, which is the honest reading of an unbonded voice rather than a
  special case.

  Lower middle again — the first price at which the running stake reaches half
  the total. Reading it positionally the way the unweighted median does would
  not mean anything here, because the positions are not equal weights."
  [sorted stake-of]
  (when (seq sorted)
    (let [weights (mapv (fn [[pub _]] (max 0 (or (stake-of pub) 0))) sorted)
          total (reduce + 0 weights)]
      (if (zero? total)
        ;; Nobody has anything at risk. Falling back to the unweighted median
        ;; is the same answer this had before stake existed, and it is better
        ;; than no price: the alternative is a market that stops because its
        ;; publishers are unbonded, which is a governance problem wearing an
        ;; outage.
        (median sorted)
        (loop [i 0 acc 0]
          (let [acc (+ acc (nth weights i))]
            (if (or (>= (* 2 acc) total) (= i (dec (count sorted))))
              (second (nth sorted i))
              (recur (inc i) acc))))))))

(defn aggregate
  "Return `{:price p :n n :stale? false}` when quorum is met, or
  `{:price nil :n n :stale? true}` when it is not.

  Never returns a price computed from too few submissions. A price the system
  cannot vouch for is worse than no price, because everything downstream —
  the mark, margin, liquidation — will treat it as vouched for."
  ([submissions publishers now params] (aggregate submissions publishers now params nil))
  ([submissions publishers now {:keys [quorum max-age] :as _params} stake-of]
   (let [f (fresh submissions publishers now max-age)]
     (if (>= (count f) quorum)
       {:price (if stake-of (weighted-median f stake-of) (median f))
        :n (count f)
        :weighted? (boolean stake-of)
        :stale? false}
       {:price nil :n (count f) :weighted? (boolean stake-of) :stale? true}))))

(defn deviation
  "How far `price` sits from `reference`, as a rate. Used to report how much a
  publisher disagrees with the aggregate — a monitoring signal, not an
  enforcement one. Slashing a publisher for disagreeing would punish the
  honest one during a genuine dislocation."
  [price reference]
  (if (pos? reference)
    (fx/fdiv (* (fx/abs* (- price reference)) fx/rate-scale) reference)
    0))
