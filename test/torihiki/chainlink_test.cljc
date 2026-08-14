(ns torihiki.chainlink-test
  "The venue had no price feed at all — nothing anywhere sent an
  `:oracle-submit`, so every market carried the one price the bridge gave it
  at listing and never moved. Margin, funding and liquidation were all
  computing against a constant."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.chainlink :as cl]))

(defn- word [n]
  (let [h #?(:clj (.toString (BigInteger. (str n)) 16)
             :cljs (.toString (js/BigInt (str n)) 16))]
    (str (apply str (repeat (- 64 (count h)) \0)) h)))

(defn- round-data
  "A `latestRoundData()` return: roundId, answer, startedAt, updatedAt,
  answeredInRound."
  [answer updated]
  (str "0x" (word 1) (word answer) (word updated) (word updated) (word 1)))

;; The real ETH/USD answer, read from the mainnet aggregator on 2026-08-14.
;; Kept as a literal so the decoder is tested against something a node
;; actually returned rather than only against what this file can imagine.
(def ^:private live-eth-usd 186591766498)
(def ^:private live-btc-usd 6253900000000)

(deftest an-aggregator-answer-is-read-out-of-the-call-return
  (let [d (cl/decode-latest-round (round-data live-eth-usd 1786716167))]
    (is (some? d) "a well-formed return did not decode")
    (is (= 1786716167 (:updated-at d)))
    (is (= 186591766 (cl/scaled-price (:answer d) 8 5))
        "ETH at 1865.91766498, five decimals of venue precision")))

(deftest a-price-this-venue-cannot-read-is-not-published
  (testing "Nil means NOT PUBLISHED. A validator that published a guess would
            be one whose quorum agrees about a number nobody measured."
    (is (nil? (cl/decode-latest-round nil)))
    (is (nil? (cl/decode-latest-round "0x")) "short data")
    (is (nil? (cl/decode-latest-round (str "0x" (word 1) (word 0))))
        "truncated before updatedAt")
    (is (nil? (cl/decode-latest-round (round-data 0 1)))
        "a zero answer is a broken feed, not a free asset")
    (is (nil? (cl/decode-latest-round
               (str "0x" (word 1)
                    ;; answer = -1 in two's complement
                    (apply str (repeat 64 \f))
                    (word 1) (word 1) (word 1))))
        "a negative answer must be refused, not read as an enormous price")))

(deftest scaling-refuses-rather-than-rounding-to-nothing
  (let [d (cl/decode-latest-round (round-data 1 1))]
    (is (nil? (cl/scaled-price (:answer d) 8 2))
        "a price that floors to zero was published — that liquidates every
         position on the market")
    (is (nil? (cl/scaled-price (:answer d) 2 8))
        "venue precision finer than the feed's is a scale nobody can honour")
    (is (= 1 (cl/scaled-price (:answer d) 8 8))
        "equal precision is a factor of one")))

(deftest submissions-drop-what-they-cannot-read
  (let [feeds {1 {:answer-hex (round-data live-btc-usd 1786716167)
                  :feed-decimals 8 :px-decimals 2}
               2 {:answer-hex (round-data live-eth-usd 1786716167)
                  :feed-decimals 8 :px-decimals 2}
               3 {:answer-hex "0x" :feed-decimals 8 :px-decimals 2}}
        txs (cl/submissions 7 feeds)]
    (is (= 2 (count txs)) "the unreadable feed produced a transaction anyway")
    (is (= [{:tx :oracle-submit :account 7 :market 1 :price 6253900}
            {:tx :oracle-submit :account 7 :market 2 :price 186591}]
           txs))))
