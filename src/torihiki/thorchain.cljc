(ns torihiki.thorchain
  "Reading THORChain, so a validator can attest what it saw.

  The escrow is THORChain: a user sends an asset to the network's inbound
  vault with a memo naming their torihiki account, THORChain's own validators
  observe it into their consensus, and the asset sits behind a key no single
  party holds. `torihiki.state/deposit-attest` decides when that becomes
  collateral here — it needs a quorum of bonded validators to say the same
  thing about the same transaction.

  This namespace is the half that turns THORChain's answers into that claim.
  It is **pure**: it takes parsed JSON and returns transactions to sign. The
  polling, the HTTP and the signing live in the node, because those are the
  parts that cannot be tested without a network and this is the part that must
  be.

  ## The memo is the whole binding

  Nothing about a THORChain transfer says which torihiki account it belongs
  to. The memo does, and a memo is user-supplied text, so it is parsed
  strictly: the exact prefix, then digits, and nothing else. A memo that does
  not parse is not a deposit for anybody — crediting a best guess would mean
  crediting an account the sender did not name."
  (:require [clojure.string :as str]))

(def ^:const memo-prefix
  "What a deposit memo must start with. Namespaced, because a THORChain vault
  serves every protocol that points at it and a bare number would be somebody
  else's convention as easily as ours."
  "TORIHIKI:")

(defn- js-or-jvm-parse [s]
  #?(:clj (Long/parseLong s)
     :cljs (js/parseInt s 10)))

(defn parse-memo
  "The torihiki account a memo names, or nil.

  Strict on purpose. `TORIHIKI:123` and nothing else — no whitespace, no
  suffix, no other case. THORChain memos are already a command language
  (`SWAP:`, `ADD:`), so a loose parser here is a parser that reads somebody
  else's instruction as a deposit for whichever account the digits happen to
  form."
  [memo]
  (when (and (string? memo) (str/starts-with? memo memo-prefix))
    (let [tail (subs memo (count memo-prefix))]
      (when (re-matches #"[0-9]+" tail)
        (let [n (js-or-jvm-parse tail)]
          (when (pos? n) n))))))

(def ^:const min-confirmations
  "How deep an observed transaction must be before it is attested. **2.**

  THORChain reports an inbound as soon as its own validators have observed it,
  and its own reorg handling is what makes that final — but a validator here
  that attests at depth zero is attesting something that can still be undone,
  and an attestation cannot be withdrawn. Two blocks is small, and the point is
  that it is not zero."
  2)

(defn deposits-in
  "Every credited deposit in a THORChain `/tx/details`-shaped answer, as the
  transactions a validator would attest.

  `me` is the attesting validator's account id. `vaults` is the SET of inbound
  addresses this venue accepts — an observation naming anything else is
  somebody else's deposit, and checking it here is what stops a validator from
  being talked into attesting a transfer that never reached us.

  A SET, not one address, because THORChain's vaults churn: the network
  rotates its asgard every few days, and a deposit sent to the outgoing vault
  minutes before the rotation is still a real deposit. Watching one address
  means that, on every churn, deposits stop being credited and nothing says
  so — the venue keeps running and quietly stops taking money. The set is the
  current inbound plus the ones this validator has seen before.

  Returns a vector of `:deposit-attest` transactions, one per usable
  observation, in the order given. Anything unusable is DROPPED rather than
  reported: a validator that cannot read an observation has nothing to say
  about it, and saying something anyway is the failure this whole path exists
  to avoid."
  [me vaults tip-height observations]
  (vec
   (for [o observations
         :let [memo (get o :memo (get o "memo"))
               acct (parse-memo memo)
               txid (get o :tx-id (get o "tx_id" (get o "txID")))
               asset (get o :asset (get o "asset"))
               amount (get o :amount (get o "amount"))
               to (get o :to (get o "to"))
               height (get o :height (get o "height"))]
         :when (and acct
                    (string? txid) (seq txid)
                    (string? asset) (seq asset)
                    (integer? amount) (pos? amount)
                    ;; One of the vaults we watch, not any vault.
                    (contains? (set vaults) to)
                    ;; Deep enough that THORChain will not take it back.
                    (integer? height)
                    (>= (- tip-height height) min-confirmations))]
     {:tx :deposit-attest
      :account me
      :txid txid
      :credit acct
      :amount amount
      :asset asset})))

(defn payouts-in
  "Every outbound payment in a THORChain answer, as `:withdraw-attest`
  transactions.

  The exit needs witnessing for the same reason the entrance does: one key
  that can settle a real claim can settle one that was never paid. A payout
  carries the claim number in its own memo, written by whoever sent it, and
  the same strictness applies — a payout whose memo does not name a claim
  settles nothing."
  [me tip-height observations]
  (vec
   (for [o observations
         :let [memo (get o :memo (get o "memo"))
               claim (parse-memo memo)
               txid (get o :tx-id (get o "tx_id" (get o "txID")))
               dest (get o :to (get o "to"))
               height (get o :height (get o "height"))]
         :when (and claim
                    (string? txid) (seq txid)
                    (string? dest) (seq dest)
                    (integer? height)
                    (>= (- tip-height height) min-confirmations))]
     {:tx :withdraw-attest
      :account me
      :claim claim
      :txid txid
      :dest dest})))

(defn withdrawal-memo
  "The memo a payout must carry so the validators can attest it back to the
  claim it settles. The same grammar as a deposit memo, and deliberately so:
  one thing to parse, one thing to get wrong."
  [claim]
  (str memo-prefix claim))
