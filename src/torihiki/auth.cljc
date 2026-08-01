(ns torihiki.auth
  "Who is allowed to move an account, and how a signature stops being reusable.

  Until this existed, `:account` was a number a client asserted. Anyone could
  submit a transaction claiming to be account 5 and the engine would apply it.
  The API validated the SHAPE of a request and never asked who sent it.

  ## Why authentication lives in the engine, not the transport

  It looks like a transport concern and it is not. Two of its three parts are
  consensus state:

  - the **nonce** is per-account state, and every validator must agree on its
    value or they disagree about which transactions are replays;
  - the **key binding** is per-account state, for the same reason;
  - only the *framing* — HTTP headers, a WebSocket envelope — belongs to a
    transport, and that part is the easy half.

  Putting nonces in a server would mean a chain whose replay protection lives
  outside the thing that agrees on state, which is not replay protection.

  ## The signed payload

  `signing-payload` covers the chain id, the account, the nonce, and every
  field of the transaction. Each of those is load-bearing:

  - **chain id** is domain separation. A signature produced on a testnet must
    not authorise the same transaction on the mainnet, and the only thing that
    stops it is the signature covering which chain it was for.
  - **nonce** is what makes the signature single-use.
  - **every field** — a payload that summarised the transaction would let two
    different transactions share one signature.

  ## Keys, accounts, and what this does not solve

  An account id is claimed by first use: the first authenticated transaction
  binds that id to its public key, and every later one must match. The KEY is
  the identity; the id is a short handle for it. That is why binding is
  immutable — a rebindable account is an account that can be stolen from
  whoever holds the key.

  Deriving the id from the key instead (as an address) would remove the
  registration step entirely, and is the right answer once there is a real
  address format. It is not done here because account ids are integers the
  book stores in a slab, and truncating a hash into that space trades a
  registration race for an account COLLISION, which is worse.

  ## Injected verification

  `verify-fn` receives `[pubkey payload sig]` and returns truthy when the
  signature is good. Nothing here imports crypto, for the same reason
  `torihiki.log` does not: `kotoba-lang/ed25519` is JVM-only, and a browser
  that cannot re-verify a block is not a verifier."
  (:require [clojure.string :as str]))

(def reasons
  #{:unsigned :bad-signature :bad-nonce :wrong-key :missing-account})

(defn- tx-fields
  "Every transaction field that can change what a transaction DOES, in a fixed
  order. Anything omitted here is a field an attacker could alter without
  invalidating the signature, so the list is deliberately exhaustive rather
  than minimal — an unused field costs a few bytes, a missing one costs the
  account."
  [tx]
  [(name (:tx tx :none))
   (:market tx) (:side tx) (:level tx) (:qty tx) (:flags tx)
   (:oid tx) (:amount tx) (:price tx) (:id tx)
   (:trigger-price tx)
   (when-let [d (:direction tx)] (name d))
   (get-in tx [:order :side]) (get-in tx [:order :level])
   (get-in tx [:order :qty]) (get-in tx [:order :flags])])

(defn signing-payload
  "The canonical string a client signs. Field-per-line with names, so two
  different transactions cannot collide by juxtaposition — the same reason
  `torihiki.state`'s canonical encoding is tagged rather than concatenated."
  [chain-id account nonce tx]
  (str "torihiki/tx/v1\n"
       "chain=" chain-id "\n"
       "account=" account "\n"
       "nonce=" nonce "\n"
       (str/join "\n" (map-indexed (fn [i v] (str "f" i "=" v)) (tx-fields tx)))
       "\n"))

(defn expected-nonce
  "The only nonce this account may use next. Strictly sequential rather than
  merely increasing: a gap would let a holder sign several transactions and
  choose their order later, which is a reordering primitive handed to whoever
  holds the key."
  [ex account]
  (inc (get-in ex [:nonces account] 0)))

(defn check
  "nil when `signed` may be applied as `account`, otherwise a keyword from
  `reasons`. Pure — `verify-fn` does the cryptography."
  [ex {:keys [tx account nonce sig pubkey]} chain-id verify-fn]
  (let [bound (get-in ex [:account-keys account])]
    (cond
      (not (integer? account)) :missing-account
      (or (nil? sig) (nil? pubkey)) :unsigned
      (and bound (not= bound pubkey)) :wrong-key
      (not= nonce (expected-nonce ex account)) :bad-nonce
      (not (verify-fn pubkey (signing-payload chain-id account nonce tx) sig))
      :bad-signature
      :else nil)))

(defn accept
  "Record that this account's nonce was consumed and its key is bound.

  The nonce is consumed even when the transaction later fails validation. The
  account holder authorised THAT nonce for THAT transaction; letting a
  rejected transaction leave the nonce unspent would make the signature
  reusable, which is exactly what the nonce exists to prevent. Only a failure
  of authentication itself leaves the state untouched, because such a message
  was never from the account at all."
  [ex {:keys [account nonce pubkey]}]
  (-> ex
      (assoc-in [:nonces account] nonce)
      (update-in [:account-keys account] #(or % pubkey))))
