(ns torihiki.evm-test
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.evm :as evm]
            [torihiki.clearing :as cl]
            [torihiki.state :as st]))

;; Its own fixtures rather than `state-test`'s, which are private — and
;; deliberately so: a test namespace that exports its scaffolding becomes a
;; second definition of what a fresh exchange is.
(def ^:private mkt
  (assoc (cl/market {:id 1 :max-leverage 20 :tick 1 :lot 1})
         :taker-fee-rate 0 :maker-fee-rate 0))

(defn- fresh []
  (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 65536 :ev-cap 65536}}))

(defn- funded [ex accts amount]
  (reduce (fn [e a] (st/apply-tx e {:tx :deposit :account a :amount amount}))
          ex accts))

(defn- ex-with-a-position []
  (-> (fresh)
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (funded [700 701] 1000000)
      (st/apply-tx {:tx :order :account 700 :market 1 :side 0 :level 500
                    :qty 4 :flags 0})
      (st/apply-tx {:tx :order :account 701 :market 1 :side 1 :level 500
                    :qty 4 :flags 0})))

(deftest a-contract-can-read-a-position
  (testing "what makes an EVM beside a venue worth having is that a contract
            can read the venue. Without this it is an EVM next to an exchange
            it cannot see, and every integration goes back to trusting an
            off-chain feed."
    (let [ex (ex-with-a-position)
          ask #(evm/call ex evm/core-address (apply evm/encode-call %&))
          long-side (ask :position 700 1)
          short-side (ask :position 701 1)]
      (is (= (str "0x" (apply str (repeat 63 \0)) "4") long-side)
          "the buyer's position did not come back as 4")
      (is (= (str "0x" (apply str (repeat 62 \f)) "fc") short-side)
          "the seller's -4 was not two's complement — a Solidity caller
           decoding int256 would read an astronomically large long"))))

(deftest the-numbers-a-contract-needs-to-price-anything
  (let [ex (ex-with-a-position)
        ask #(evm/call ex evm/core-address (apply evm/encode-call %&))
        hex->int (fn [s] #?(:clj (BigInteger. (subs s 2) 16)
                            :cljs (js/parseInt (subs s 2) 16)))]
    (is (= 500 (hex->int (ask :oracle 1))))
    (is (pos? (hex->int (ask :collateral 700))))
    (is (< (hex->int (ask :free-collateral 700))
           (hex->int (ask :collateral 700)))
        "free collateral did not fall below total after a position was
         opened — the margin is not being read")))

(deftest an-unknown-question-is-not-a-zero
  (testing "a precompile that turned an unknown selector into a word of zeroes
            would hand every caller a plausible answer to a question the
            exchange never understood, and the caller could not tell that from
            a real position of zero."
    (let [ex (ex-with-a-position)]
      (is (nil? (evm/call ex evm/core-address "0xdeadbeef"))
          "answered a selector it does not implement")
      (is (nil? (evm/call ex "0x0000000000000000000000000000000000000001"
                          (evm/encode-call :collateral 700)))
          "answered for an address that is not this precompile")
      (is (nil? (evm/call ex evm/core-address "0x9d3c6b8e"))
          "answered a call with no arguments in it")
      (is (some? (evm/call ex evm/core-address (evm/encode-call :position 999 1)))
          "an account with no position is a real zero and must answer"))))

(deftest the-address-is-matched-case-insensitively
  ;; Solidity checksums addresses by case. A bridge that only matched lower
  ;; case would refuse every caller that pasted a checksummed address.
  (let [ex (ex-with-a-position)]
    (is (some? (evm/call ex (str "0x" (clojure.string/upper-case
                                    (subs evm/core-address 2)))
                         (evm/encode-call :collateral 700)))
        "a checksummed address was refused")))
