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

  The KEY is the identity; the id is a short handle for it. Binding is
  immutable — a rebindable account is an account that can be stolen from
  whoever holds the key.

  ## Which id a key may bind, and why first-use was not enough

  With `derive-fn` supplied, a key may only bind the id DERIVED FROM IT. That
  closes an attack that first-use binding does not survive under consensus:
  the party ordering transactions sees a pending binding before it commits and
  can insert its own binding for that id first. Under a single sequencer the
  owner is always first, which is why this was invisible until four replicas
  ran with a Byzantine leader — it claimed the account, and the genuine owner
  was refused `:wrong-key` on their own id, permanently.

  The earlier note here said deriving the id trades a registration race for an
  account collision, and that this was worse. That was the wrong comparison. A
  collision is REFUSED — the second key gets `:wrong-key`, sees it, and can
  use another key — while the race is silent, permanent, and profitable for
  whoever orders the transactions. And the slab holds i53, not 32 bits, so the
  space is large enough that a collision needs tens of millions of accounts
  rather than tens of thousands.

  `derive-fn` is injected for the same reason `verify-fn` is: this namespace
  has no crypto and a browser that cannot hash is still allowed to replay.
  Without one, ids remain first-come — which is correct for replaying a
  history already agreed and wrong anywhere a proposer can front-run.

  ## Injected verification

  `verify-fn` receives `[pubkey payload sig]` and returns truthy when the
  signature is good. Nothing here imports crypto, for the same reason
  `torihiki.log` does not: `kotoba-lang/ed25519` is JVM-only, and a browser
  that cannot re-verify a block is not a verifier."
  (:require [clojure.string :as str]))

(def reasons
  #{:unsigned :bad-signature :bad-nonce :wrong-key :missing-account
    :not-your-account})

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
  `reasons`. Pure — `verify-fn` does the cryptography and `derive-fn` the
  hashing.

  `derive-fn` maps a public key to the only account id it may CLAIM. Supplying
  one is what stops a proposer from binding an id it does not hold the key
  for; omitting it leaves ids first-come. Already-bound accounts are unchanged
  either way: the binding is immutable and the key must match."
  ([ex signed chain-id verify-fn] (check ex signed chain-id verify-fn nil))
  ([ex {:keys [tx account nonce sig pubkey]} chain-id verify-fn derive-fn]
   (let [bound (get-in ex [:account-keys account])]
     (cond
       (not (integer? account)) :missing-account
       (or (nil? sig) (nil? pubkey)) :unsigned
       (and bound (not= bound pubkey)) :wrong-key
       ;; Only checked when the account is UNBOUND: this decides who may claim
       ;; an id, not who may keep using one they already hold.
       (and (nil? bound) derive-fn (not= account (derive-fn pubkey)))
       :not-your-account
       (not= nonce (expected-nonce ex account)) :bad-nonce
       (not (verify-fn pubkey (signing-payload chain-id account nonce tx) sig))
       :bad-signature
       :else nil))))

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
