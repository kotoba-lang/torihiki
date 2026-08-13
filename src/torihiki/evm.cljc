(ns torihiki.evm
  "The bridge a contract reads the exchange through.

  Hyperliquid's EVM is a second chain beside the matching engine, and the thing
  that makes it worth having is not that it runs Solidity — plenty of chains do
  — but that a contract there can READ the exchange: a position, a balance, a
  mark. Without that it is an EVM sitting next to a venue it cannot see, and
  every integration goes back to trusting an off-chain feed.

  So the bridge comes first, before any interpreter. This namespace is the
  contract between the two layers, and it is the part that has to be right:
  an interpreter with a wrong bridge computes correct EVM over the wrong
  exchange.

  ## What it is

  Pure, `.cljc`, and read-only. Given the exchange state and an EVM `call`, it
  answers with 32-byte words the way a precompile does. It does not execute
  bytecode and does not pretend to — `call` returns nil for an address that is
  not a precompile, which is exactly what an interpreter needs in order to fall
  through to ordinary contract execution.

  ## Signed values

  A position is signed and a 256-bit word is not. Everything here is two's
  complement over 256 bits, which is what `int256` means in the ABI and what
  every Solidity caller will decode. Returning a magnitude and a sign flag
  would be a second convention for something the ABI already has one for."
  (:require [clojure.string :as str]
            [torihiki.clearing :as cl]))

(def ^:const word-bits 256)

(def two-256
  "2^256, as the modulus two's complement is taken in.

  Reader-conditional, because the two runtimes have different big integers and
  this namespace has to give the same 64 characters on both. `bigint` is
  Clojure's and does not exist in ClojureScript: the first version used it
  unguarded, every JVM test passed, and the node would not start —
  `Unable to resolve symbol: bigint`. A `.cljc` file that only runs on one
  side is a `.cljc` file in name."
  #?(:clj (apply * (repeat 32 256N))
     :cljs (js/BigInt "115792089237316195423570985008687907853269984665640564039457584007913129639936")))

(defn- hex-word
  "One 32-byte ABI word, as 64 hex characters. Two's complement for negatives:
  a position of -5 is not `-5` in a word, it is `2^256 - 5`, and a caller
  decoding `int256` reads it back as -5.

  `BigInt.asUintN` on the ClojureScript side, which is exactly this
  conversion and one call. Two attempts before it were arithmetic —
  `(mod (bigint n) two-256)` does not compile there at all (`bigint` is
  Clojure's), and `(rem (+ (rem b m) m) m)` compiles and then throws
  `Cannot convert a BigInt value to a number`, because ClojureScript's
  arithmetic coerces its arguments and BigInt refuses to be coerced. The
  JVM tests passed through both."
  [n]
  (let [s #?(:clj (.toString (biginteger (mod (bigint n) two-256)) 16)
             :cljs (.toString (js/BigInt.asUintN 256 (js/BigInt n)) 16))
        s (str/replace s #"^0x" "")]
    (str (apply str (repeat (- 64 (count s)) \0)) s)))

(defn- parse-word
  "The nth 32-byte argument of a calldata payload, as a non-negative integer.

  Arguments here are account ids and market ids, which are never negative, so
  this does not undo two's complement — a caller passing a negative market id
  gets an enormous one and finds no such market, which is the same refusal."
  [calldata n]
  (let [body (subs calldata 10)                     ; past "0x" and the selector
        off (* n 64)]
    (when (>= (count body) (+ off 64))
      ;; Account ids reach 2^45 and market ids are small, so a double holds
      ;; every argument these five take exactly. Parsing the whole 256-bit word
      ;; into a JS number would lose precision above 2^53 — but a caller
      ;; passing something that large is passing an account that cannot exist,
      ;; and the lookup misses either way.
      #?(:clj (BigInteger. ^String (subs body off (+ off 64)) 16)
         :cljs (js/parseInt (subs body off (+ off 64)) 16)))))

(def ^:const core-address
  "Where the exchange answers. Fixed, low, and outside the range Ethereum has
  spoken for — a precompile at an address a contract could also be deployed to
  is a precompile somebody can shadow."
  "0x0000000000000000000000000000000000000801")

;; Selectors are the first four bytes of keccak256 of the signature. They are
;; written out rather than computed because this namespace has no keccak and
;; should not grow one to name four constants — and because a selector that is
;; computed is a selector nobody can grep for when a caller says it does not
;; match.
;;
;; Each is followed by the signature it comes from, which is the thing a
;; Solidity caller writes; getting the two out of step is the failure this
;; comment exists to make visible.
(def selectors
  {"0x9d3c6b8e" :position          ; position(uint64,uint32) -> int256
   "0x4a3f7c21" :collateral        ; collateral(uint64) -> uint256
   "0x2b8d1e05" :mark              ; markPx(uint32) -> uint256
   "0x7e1a9f43" :oracle            ; oraclePx(uint32) -> uint256
   "0x1c5b2d76" :free-collateral}) ; freeCollateral(uint64) -> uint256

(defn- selector-of [calldata]
  (when (and (string? calldata) (>= (count calldata) 10))
    (get selectors (str/lower-case (subs calldata 0 10)))))

(defn call
  "Answer an `eth_call` to the exchange precompile.

  Returns a `0x`-prefixed 32-byte word, or nil — nil for an address that is not
  this precompile, a selector that is not one of ours, or calldata too short to
  carry its arguments. **Nil is not zero.** An interpreter that turned an
  unknown selector into a word of zeroes would hand every caller a plausible
  answer to a question the exchange never understood, and the caller would have
  no way to tell that from a real position of zero.

  Reads. There is deliberately no write path: a contract that could move a
  position would be a second matching engine with none of the first one's
  checks, and the transaction types in `torihiki.state` are how positions
  move."
  [ex address calldata]
  (when (and (string? address)
             (= (str/lower-case address) core-address))
    (when-let [sel (selector-of calldata)]
      (let [c (:clearing ex)]
        (case sel
          :position
          (let [a (parse-word calldata 0) m (parse-word calldata 1)]
            (when (and a m)
              ;; `[:accounts a :positions m]`, which is where a position
              ;; lives — `[:positions a m]` is a shape this namespace invented
              ;; and it answered zero for every account that had one.
              ;; `cl/position`, and the field is `:size`. Reaching into
              ;; `[:positions a m :qty]` — a shape this namespace invented —
              ;; answered zero for every account that had one, which is the
              ;; failure mode a read bridge must never have: a wrong number
              ;; that looks like a legitimate flat position.
              (str "0x" (hex-word (:size (cl/position c a m) 0)))))

          :collateral
          (when-let [a (parse-word calldata 0)]
            (str "0x" (hex-word (get-in c [:accounts a :collateral] 0))))

          :free-collateral
          (when-let [a (parse-word calldata 0)]
            (str "0x" (hex-word (cl/free-collateral c a (:marks ex) (:markets ex)))))

          :mark
          (when-let [m (parse-word calldata 0)]
            (str "0x" (hex-word (get-in ex [:marks m] 0))))

          :oracle
          (when-let [m (parse-word calldata 0)]
            ;; `[:oracle m]`. The market SPEC has an `:oracle` key too and it
            ;; is not the price — reading it there returned zero for a market
            ;; whose oracle had been set, which a contract would price against.
            (str "0x" (hex-word (get-in ex [:oracle m] 0))))

          nil)))))

(defn encode-call
  "The calldata for one of these, for a caller that has no ABI encoder handy —
  the node's own tests and the JSON-RPC surface. Not a general encoder: it
  takes the arguments these five take, which are all unsigned integers."
  [sel & args]
  (let [hex (some (fn [[k v]] (when (= v sel) k)) selectors)]
    (when hex
      (apply str hex (map hex-word args)))))
