(ns torihiki.thorchain-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [torihiki.keccak :as kc]
            [torihiki.thorchain :as tc]))

(deftest a-memo-names-one-account-or-nothing
  (testing "the memo is the whole binding between a transfer on another chain
            and an account here, and it is user-supplied text. THORChain memos
            are already a command language (SWAP:, ADD:), so a loose parser is
            one that reads somebody else's instruction as a deposit for
            whichever account the digits happen to form."
    (is (= 12345 (tc/parse-memo "TORIHIKI:12345")))
    (is (nil? (tc/parse-memo "TORIHIKI:12345 ")) "trailing space")
    (is (nil? (tc/parse-memo " TORIHIKI:12345")) "leading space")
    (is (nil? (tc/parse-memo "torihiki:12345")) "case")
    (is (nil? (tc/parse-memo "TORIHIKI:12a45")) "not digits")
    (is (nil? (tc/parse-memo "TORIHIKI:")) "no account at all")
    (is (nil? (tc/parse-memo "TORIHIKI:0")) "account zero is not an account")
    (is (nil? (tc/parse-memo "SWAP:BTC.BTC:TORIHIKI:12345"))
        "somebody else's instruction read as our deposit")
    (is (nil? (tc/parse-memo nil)))))

(defn- obs [& {:as m}]
  (merge {"memo" "TORIHIKI:900" "tx_id" "ABC" "asset" "ETH.ETH"
          "amount" 1000 "to" "vault-1" "height" 10}
         m))

(deftest only-what-arrived-in-our-vault-and-settled
  (testing "an observation is a claim a validator is about to sign. Each of
            these is a way for it to be about somebody else's money, or about
            money THORChain can still take back."
    (let [tip 100
          ok (obs)]
      (is (= 1 (count (tc/deposits-in 7 #{"vault-1"} tip [ok]))))
      (is (empty? (tc/deposits-in 7 #{"vault-2"} tip [ok]))
          "attested a transfer that never reached this venue")
      (is (empty? (tc/deposits-in 7 #{"vault-1"} tip [(obs "memo" "hello")]))
          "credited a deposit whose sender named no account")
      (is (empty? (tc/deposits-in 7 #{"vault-1"} tip [(obs "amount" 0)]))
          "attested a zero")
      (is (empty? (tc/deposits-in 7 #{"vault-1"} 11 [(obs "height" 10)]))
          "attested something one block deep, and an attestation cannot be
           withdrawn")
      (is (empty? (tc/deposits-in 7 #{"vault-1"} tip [(obs "tx_id" "")]))
          "attested a transaction with no id — the key the credit is deduped on"))))

(deftest an-observation-becomes-the-transaction-a-validator-signs
  (let [[tx] (tc/deposits-in 7 #{"vault-1"} 100 [(obs)])]
    (is (= {:tx :deposit-attest :account 7 :txid "ABC" :credit 900
            :amount 1000 :asset "ETH.ETH"}
           tx))))

(deftest a-payout-settles-the-claim-its-memo-names
  (let [tip 100
        p {"memo" (tc/withdrawal-memo 42) "tx_id" "OUT-1"
           "to" "0xabc" "height" 10}]
    (is (= [{:tx :withdraw-attest :account 7 :claim 42
             :txid "OUT-1" :dest "0xabc"}]
           (tc/payouts-in 7 tip [p])))
    (is (empty? (tc/payouts-in 7 tip [(assoc p "memo" "OUT")]))
        "a payout naming no claim settled one anyway")))

(deftest a-vault-that-churned-is-still-our-vault
  (testing "THORChain rotates its asgard every few days, and a deposit sent to
            the outgoing vault minutes before the rotation is still a real
            deposit. A watcher holding one address stops crediting on every
            churn and says nothing — the venue keeps running and quietly stops
            taking money."
    (let [old (obs "to" "vault-old")
          new (obs "to" "vault-new")
          both #{"vault-old" "vault-new"}]
      (is (= 2 (count (tc/deposits-in 7 both 100 [old new]))))
      (is (= 1 (count (tc/deposits-in 7 #{"vault-new"} 100 [old new])))
          "the address that was ours an hour ago stopped being ours"))))

;; ── reading the deposit off Ethereum ────────────────────────────────────────

(defn- format-word
  "One 32-byte word. Takes a STRING for anything large: an 18-decimal token
  amount does not fit in a JavaScript number, so writing it as a literal would
  make the TEST imprecise on one of the two runtimes — which is the failure
  being tested for."
  [n]
  (let [s (if (string? n) n (str (long n)))
        h #?(:clj (.toString (BigInteger. ^String s) 16)
             :cljs (.toString (js/BigInt s) 16))]
    (str (apply str (repeat (- 64 (count h)) \0)) h)))

(defn- char-code
  "A character's code point, on both runtimes.

  ClojureScript has no character type: a character literal reads as a
  one-character STRING, and `int` of a string is NaN. Every byte of every memo
  in this file came out NaN under nbb, the ABI string was garbage, and these
  deposit tests passed on the JVM while testing nothing at all on the runtime
  the validator actually runs on."
  [c]
  #?(:clj (int c) :cljs (.charCodeAt (str c) 0)))

(defn- abi-string
  "A `string` as the ABI lays it out: offset, length, bytes padded to 32."
  [s]
  (let [hex (apply str (map #(let [h (#?(:clj Integer/toHexString :cljs (fn [x] (.toString x 16)))
                                        (char-code %))]
                              (if (= 1 (count h)) (str "0" h) h))
                            s))
        pad (- 64 (mod (count hex) 64))]
    (str (format-word 0x40) (format-word (count s)) hex
         (apply str (repeat (if (= 64 pad) 0 pad) \0)))))

(def ^:private token
  "USDC's real address, lower-cased — the registry is keyed by it."
  "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")

(def ^:private usdc
  {token {:asset "ETH.USDC" :decimals 6}})

(defn- log-of [{:keys [vault memo amount block txid tok]
                :or {tok token}}]
  {"topics" ["0xdeadbeef"
             (str "0x" (apply str (repeat 24 \0)) (subs vault 2))
             (str "0x" (apply str (repeat 24 \0)) (subs tok 2))]
   "data" (str "0x" (format-word amount) (abi-string memo))
   "transactionHash" txid
   "blockNumber" (str "0x" (#?(:clj Integer/toHexString :cljs (fn [x] (.toString x 16))) block))})

(deftest the-topic-is-derived-not-pasted
  ;; A pasted topic is a constant nobody can check. This one is the keccak of
  ;; the signature, computed by the same code that computes mapping slots.
  (is (= 64 (count (kc/digest-hex (map char-code tc/deposit-event-signature))))))

(deftest a-deposit-is-read-out-of-an-ethereum-log
  (testing "THORChain's own hosts do not answer a Cloudflare Worker — four of
            five could not even be resolved. The deposit is also an Ethereum
            event, and the log carries every field an attestation needs."
    (let [vault "0x1234567890abcdef1234567890abcdef12345678"
          l (log-of {:vault vault :memo "TORIHIKI:900" :amount 4242
                     :block 100 :txid "0xabc"})
          [tx] (tc/deposits-from-logs 7 #{vault} 200 [l] usdc)]
      (is (= 900 (:credit tx)))
      (is (= 4242 (:amount tx)))
      (is (= "0xabc" (:txid tx)))
      (is (str/starts-with? (:asset tx) "ETH.")
          "the asset was dropped — a quorum would agree about an amount of
           nothing in particular"))))

(deftest the-same-refusals-apply-to-a-log
  (let [vault "0x1234567890abcdef1234567890abcdef12345678"
        other "0x9999999999999999999999999999999999999999"
        ok (log-of {:vault vault :memo "TORIHIKI:900" :amount 4242
                    :block 100 :txid "0xabc"})]
    (is (= 1 (count (tc/deposits-from-logs 7 #{vault} 200 [ok] usdc))))
    (is (empty? (tc/deposits-from-logs 7 #{other} 200 [ok] usdc))
        "attested a transfer into somebody else's vault")
    (is (empty? (tc/deposits-from-logs 7 #{vault} 101 [ok] usdc))
        "attested a log one block deep — and an attestation cannot be
         withdrawn")
    (is (empty? (tc/deposits-from-logs
                 7 #{vault} 200
                 [(log-of {:vault vault :memo "hello" :amount 4242
                           :block 100 :txid "0xabc"})]
                 usdc))
        "credited a deposit whose sender named no account")))

(deftest an-eighteen-decimal-amount-does-not-crash-the-chain
  ;; Measured on a live Router log: 50,000 DAI is 5x10^22, and the engine is
  ;; i53. Unscaled, that number reaches `torihiki.fixed` and THROWS — inside
  ;; `apply-block`, which is a consensus crash and not a refusal.
  ;;
  ;; Reading the word as a number was also a silent runtime split: the JVM
  ;; threw `NumberFormatException` and JavaScript answered
  ;; 5.0000000000000004e22. Two validators, two answers, one of them mute.
  (let [vault "0x1234567890abcdef1234567890abcdef12345678"
        dai "0x6b175474e89094c44da98b954eedeac495271d0f"
        reg {dai {:asset "ETH.DAI" :decimals 18}}
        l (log-of {:vault vault :memo "TORIHIKI:900" :tok dai
                   ;; 50,000 DAI in the token's own units.
                   :amount "50000000000000000000000"
                   :block 100 :txid "0xabc"})
        [tx] (tc/deposits-from-logs 7 #{vault} 200 [l] reg)]
    (is (= 50000000000 (:amount tx))
        "50,000 DAI is 5x10^10 in millionths — anything else is a scale error")
    (is (< (:amount tx) 9007199254740991)
        "the amount left the engine's domain")))

(deftest an-unregistered-token-is-not-collateral
  ;; The Router is a public contract. Anyone can send any ERC-20 to a vault
  ;; with our memo, so without an allowlist a token minted for the purpose
  ;; would be credited at face value — a mint with extra steps.
  (let [vault "0x1234567890abcdef1234567890abcdef12345678"
        junk "0xbadbadbadbadbadbadbadbadbadbadbadbadbad0"
        l (log-of {:vault vault :memo "TORIHIKI:900" :tok junk
                   :amount 4242000000 :block 100 :txid "0xabc"})]
    (is (empty? (tc/deposits-from-logs 7 #{vault} 200 [l] usdc))
        "credited a token nobody listed")))

(deftest dust-is-not-a-deposit
  ;; A token finer than the venue's unit floors to zero, and a zero-amount
  ;; attestation is a claim about nothing that still consumes a nonce.
  (let [vault "0x1234567890abcdef1234567890abcdef12345678"
        dai "0x6b175474e89094c44da98b954eedeac495271d0f"
        reg {dai {:asset "ETH.DAI" :decimals 18}}
        l (log-of {:vault vault :memo "TORIHIKI:900" :tok dai
                   :amount 999999999999 :block 100 :txid "0xabc"})]
    (is (empty? (tc/deposits-from-logs 7 #{vault} 200 [l] reg)))))

(deftest an-amount-too-large-to-represent-is-refused-not-truncated
  (let [vault "0x1234567890abcdef1234567890abcdef12345678"
        l (log-of {:vault vault :memo "TORIHIKI:900"
                   ;; USDC scales by 1, so this reaches the domain check whole.
                   :amount "90071992547409910000" :block 100 :txid "0xabc"})]
    (is (empty? (tc/deposits-from-logs 7 #{vault} 200 [l] usdc))
        "a deposit this venue cannot represent was credited as a smaller one")))
