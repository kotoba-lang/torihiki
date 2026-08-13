(ns torihiki.thorchain-test
  (:require [clojure.test :refer [deftest is testing]]
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
