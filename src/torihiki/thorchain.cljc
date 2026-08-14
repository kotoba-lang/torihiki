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

;; ── the deposit, read from Ethereum ─────────────────────────────────────────
;;
;; THORChain's own hosts are behind Cloudflare and a Worker cannot resolve
;; them — measured, five of them, four unresolvable and one refusing. But an
;; ETH deposit is not only a THORChain fact: it is a call to THORChain's
;; Router **on Ethereum**, and the Router emits
;;
;;     Deposit(address indexed to, address indexed asset, uint amount, string memo)
;;
;; which carries every field an attestation needs — the vault, the asset, the
;; amount, and the memo naming the account. An Ethereum RPC answers a Worker
;; (`ethereum-rpc.publicnode.com`, measured), so the observation can come from
;; the source chain instead.
;;
;; Reading the source chain is also the stronger position. THORChain's own view
;; of an inbound is a report; the log is the transfer.

(def ^:const deposit-event-signature
  "The event whose keccak-256 is topic0. Written out so the topic can be
  derived rather than pasted — a pasted topic is a constant nobody can check."
  "Deposit(address,address,uint256,string)")

(defn- hex->long [h]
  #?(:clj (Long/parseLong h 16) :cljs (js/parseInt h 16)))

;; ── the amount does not fit in a number ─────────────────────────────────────
;;
;; An ERC-20 amount is in the token's own units, and most of Ethereum's tokens
;; have 18 decimals: 50,000 DAI is 5×10^22. Measured on a live Router log.
;;
;; The engine is i53 — `torihiki.fixed` throws for anything outside ±(2^53-1),
;; and it throws INSIDE `apply-block`, which is a consensus crash rather than a
;; refusal. So an unscaled amount is not merely large, it is a transaction that
;; takes the chain down.
;;
;; It was also a runtime divergence of exactly the kind `.cljc` exists to
;; prevent. Reading the word with `hex->long`:
;;
;;   JVM   — `NumberFormatException`, the poll dies.
;;   JS    — `parseInt` answers 5.0000000000000004e22, quietly imprecise.
;;
;; Two validators, two different answers, and only one of them says anything.

(def ^:const collateral-decimals
  "6. Collateral here is counted in millionths of a unit of account, which is
  also USDC's own precision — so the asset this venue is actually funded in
  scales by exactly 1 and cannot be mis-scaled at all."
  6)

#?(:cljs (def ^:private jsdiv (js/Function. "a" "b" "return a / b;")))
#?(:cljs (def ^:private jsgt (js/Function. "a" "b" "return a > b;")))

(defn- hex->big [h]
  #?(:clj (BigInteger. ^String h 16)
     :cljs (js/BigInt (str "0x" h))))

(defn- pow10-big [n]
  (let [s (apply str "1" (repeat n "0"))]
    #?(:clj (BigInteger. ^String s) :cljs (js/BigInt s))))

(defn- big->i53
  "The value as an ordinary integer, or nil if it does not fit the engine's
  domain. Nil, not a clamp: a deposit too large to represent is one this venue
  cannot take, and crediting a truncated version of it would be worse than
  refusing."
  [b]
  #?(:clj (when (<= (.bitLength ^BigInteger b) 53) (.longValue ^BigInteger b))
     :cljs (when-not (jsgt b (js/BigInt "9007199254740991")) (js/Number b))))

(defn scaled-amount
  "A raw 32-byte amount word, in the token's units, as collateral here.

  Floors — the direction is a consensus rule and this is the same direction
  `torihiki.fixed` rounds everywhere else. Returns nil for anything unusable:
  a token finer than the venue's own precision, an amount that overflows the
  engine, or dust that floors to nothing. Nil means NOT ATTESTED, which is the
  only safe answer to `I cannot represent what I saw`."
  [amount-hex decimals]
  (when (and (string? amount-hex) (seq amount-hex)
             (integer? decimals)
             (<= collateral-decimals decimals) (<= decimals 36))
    (let [q #?(:clj (.divide ^BigInteger (hex->big amount-hex)
                             ^BigInteger (pow10-big (- decimals collateral-decimals)))
               :cljs (jsdiv (hex->big amount-hex)
                            (pow10-big (- decimals collateral-decimals))))
          n (big->i53 q)]
      (when (and n (pos? n)) n))))

(defn- topic->address
  "The low 20 bytes of a 32-byte topic, lower-cased. An indexed address is
  left-padded, and comparing the padded form against an ordinary address is
  how a vault check silently never matches."
  [t]
  (when (and (string? t) (>= (count t) 40))
    (str "0x" (str/lower-case (subs t (- (count t) 40))))))

(defn decode-deposit-data
  "`(amount, memo)` out of the non-indexed half of a Deposit log.

  ABI: a uint256, then an offset to the string, then the string's length, then
  its bytes padded up to 32. The offset is READ rather than assumed to be
  0x40 — it is 0x40 for this event today, and a decoder that hard-codes an
  offset is one ABI change away from reading the length as the amount."
  [data]
  (let [h (str/replace (or data "") #"^0x" "")
        word (fn [i] (subs h (* 64 i) (* 64 (inc i))))]
    (when (>= (count h) 192)
      (let [;; The word itself, not a number. Scaling needs the token's
            ;; decimals, which are not in the log — see `scaled-amount`.
            amount (word 0)
            off (hex->long (word 1))
            base (* 2 off)
            len (hex->long (subs h base (+ base 64)))
            body (subs h (+ base 64) (+ base 64 (* 2 len)))
            memo (apply str (for [i (range len)]
                              (char (hex->long (subs body (* 2 i) (+ 2 (* 2 i)))))))]
        {:amount amount :memo memo}))))

(defn deposits-from-logs
  "`:deposit-attest` transactions from Ethereum logs of THORChain's Router.

  Same refusals as `deposits-in`, for the same reasons: the vault must be one
  we watch, the memo must name an account exactly, the amount must be
  positive, and the log must be deep enough that the chain will not take it
  back. `tip` is the current Ethereum head.

  ## The asset registry

  `assets` maps a token's contract address to `{:asset :decimals}` — the name
  this venue books it under and the precision it counts in. It is an
  ALLOWLIST, and everything not in it is dropped.

  That is the whole defence against the obvious attack. The Router is a public
  contract: anybody can deposit any ERC-20 to a vault with the memo
  `TORIHIKI:<n>`, and without an allowlist a worthless token minted for the
  purpose would be credited as collateral at face value. Reading the decimals
  from somewhere is not optional either — the same integer means a millionth
  of a unit for USDC and a millionth of a millionth of a millionth for DAI,
  and a validator that guesses is a validator that credits 10^12 times too
  much."
  [me vaults tip logs assets]
  (vec
   (for [l logs
         :let [topics (get l :topics (get l "topics"))
               to (topic->address (second topics))
               token (topic->address (nth topics 2 nil))
               spec (get assets token)
               txid (get l :transactionHash (get l "transactionHash"))
               bn (get l :blockNumber (get l "blockNumber"))
               height (when (string? bn) (hex->long (str/replace bn #"^0x" "")))
               {:keys [amount memo]} (decode-deposit-data
                                      (get l :data (get l "data")))
               acct (parse-memo memo)
               credited (when spec (scaled-amount amount (:decimals spec)))]
         :when (and acct to token spec credited
                    (string? txid) (seq txid)
                    (contains? (set (map str/lower-case vaults)) to)
                    (integer? height) (integer? tip)
                    (>= (- tip height) min-confirmations))]
     {:tx :deposit-attest
      :account me
      :txid txid
      :credit acct
      :amount credited
      ;; The asset as the REGISTRY names it, not as the log does. An
      ;; attestation is a claim four validators have to agree on character for
      ;; character, so the name has to come from configuration they share
      ;; rather than from a string derived per-node.
      :asset (:asset spec)})))
