(ns torihiki.chainlink
  "Reading a price off Ethereum, so a validator can publish one.

  ## Why this exists

  The venue had no price feed at all. Nothing in either repository sent an
  `:oracle-submit`, so every market carried the single price the bridge gave
  it when it was listed and never moved again. A market whose price is a
  constant is not a market — margin, funding and liquidation are all computed
  against it, and all of them were computing against 1000.

  ## Why Chainlink, and why through the Ethereum RPC

  The escrow watcher already reaches `eth_call` from a Cloudflare Worker; that
  path is measured and works, while THORChain's own hosts are behind a bot
  challenge and answer nothing. A Chainlink aggregator is a contract on that
  same chain, so reading it costs no new reachability assumption.

  It is also the better kind of source. A private price API is a host this
  venue would have to trust and cannot show anyone; an aggregator's answer is
  on a public chain, carries the round it came from, and every validator reads
  the same one and can be checked against it afterwards.

  This namespace is **pure**: it takes the hex a node returned and produces
  the transactions a validator would sign. The polling, the HTTP and the
  signing live in the node, because those are the parts that cannot be tested
  without a network and this is the part that must be."
  (:require [clojure.string :as str]))

(def ^:const latest-round-data-selector
  "`latestRoundData()`. Returns
  `(roundId, answer, startedAt, updatedAt, answeredInRound)` — five words, and
  the two that matter here are word 1 and word 3."
  "0xfeaf968c")

(defn- hex->big [h]
  #?(:clj (BigInteger. ^String h 16)
     :cljs (js/BigInt (str "0x" h))))

#?(:cljs (def ^:private jsdiv (js/Function. "a" "b" "return a / b;")))
#?(:cljs (def ^:private jsgt (js/Function. "a" "b" "return a > b;")))

(defn- big->i53
  "The value as an ordinary integer, or nil if it does not fit the engine's
  domain. Nil rather than a clamp: a price this venue cannot represent is one
  it must not publish, and a truncated price is a wrong price that looks
  right."
  [b]
  #?(:clj (when (<= (.bitLength ^BigInteger b) 53) (.longValue ^BigInteger b))
     :cljs (when-not (jsgt b (js/BigInt "9007199254740991")) (js/Number b))))

(defn- pow10-big [n]
  (let [s (apply str "1" (repeat n "0"))]
    #?(:clj (BigInteger. ^String s) :cljs (js/BigInt s))))

(defn- first-nibble [h]
  #?(:clj (Character/digit ^char (first h) 16)
     :cljs (js/parseInt (subs h 0 1) 16)))

(defn- signed?
  "Is this 256-bit word negative in two's complement? An aggregator's `answer`
  is `int256`, and a negative price is a broken feed rather than a cheap
  asset — it must be refused, not read as an enormous positive number."
  [h]
  (>= (first-nibble h) 8))

(defn decode-latest-round
  "`{:answer :updated-at :round}` out of a `latestRoundData()` return, or nil.

  Nil for anything unusable — short data, a negative answer, a zero answer.
  Nil means NOT PUBLISHED, which is the only safe answer to `I cannot read
  the price`. A validator that published a guess would be a validator whose
  quorum agrees about a number nobody measured."
  [data]
  (let [h (str/replace (or data "") #"^0x" "")
        word (fn [i] (subs h (* 64 i) (* 64 (inc i))))]
    (when (>= (count h) 320)
      (let [a (word 1)]
        (when-not (signed? a)
          (let [answer (hex->big a)
                updated (hex->big (word 3))]
            (when-not (zero? #?(:clj (.signum ^BigInteger answer)
                                :cljs (if (jsgt answer (js/BigInt 0)) 1 0)))
              {:answer answer
               :updated-at (big->i53 updated)
               :round (big->i53 (hex->big (word 0)))})))))))

(defn scaled-price
  "An aggregator answer, in the venue's price units.

  `feed-decimals` is the aggregator's own precision (8 for every USD pair
  Chainlink publishes today, but it is read from configuration rather than
  assumed — a feed that changed it would otherwise move every price by a
  factor of a hundred with nothing saying so). `px-decimals` is this venue's.

  Floors, like every other division in this engine, and returns nil rather
  than zero: a price that floors away is a price this venue cannot express at
  its own precision, and publishing zero would liquidate every position on
  that market."
  [answer feed-decimals px-decimals]
  (when (and answer (integer? feed-decimals) (integer? px-decimals)
             (<= px-decimals feed-decimals) (<= feed-decimals 36))
    (let [d (- feed-decimals px-decimals)
          q #?(:clj (.divide ^BigInteger answer ^BigInteger (pow10-big d))
               :cljs (jsdiv answer (pow10-big d)))
          n (big->i53 q)]
      (when (and n (pos? n)) n))))

(defn submissions
  "The `:oracle-submit` transactions a validator would sign for what it read.

  `feeds` maps a market id to `{:answer-hex :feed-decimals :px-decimals}` —
  what the node got back for that market's aggregator and how to scale it.
  Anything that does not decode, or does not scale, is DROPPED rather than
  reported: a validator that cannot read a price has nothing to say about it,
  and saying something anyway is the failure this whole path exists to avoid.

  `me` is the publishing validator's account id."
  [me feeds]
  (vec
   (for [[market {:keys [answer-hex feed-decimals px-decimals]}] (sort-by key feeds)
         :let [decoded (decode-latest-round answer-hex)
               px (scaled-price (:answer decoded) feed-decimals px-decimals)]
         :when px]
     {:tx :oracle-submit
      :account me
      :market market
      :price px})))
