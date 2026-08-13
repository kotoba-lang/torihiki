(ns torihiki.evm.interp
  "An EVM interpreter, for contracts that read the exchange.

  `torihiki.evm` is the bridge — the precompile a contract calls to learn a
  position or a mark. This is the machine that runs the contract doing the
  calling. Together they are what makes an EVM beside a venue worth having:
  code that can act on what the venue actually holds, rather than on a feed
  somebody attests to off-chain.

  ## Written here rather than pulled in

  `ethereumjs` exists and is complete. It is also JavaScript, and this engine
  is `.cljc` because the JVM side is where the tests run and the ClojureScript
  side is where the node runs — a rule already paid for three times in one
  session, most recently by `bigint`, which passed 305 JVM tests and would not
  start the node. An EVM layer that only ran on one of them would put the most
  security-sensitive code in the workspace outside the parity everything else
  keeps.

  ## What it implements, and what it does not

  Arithmetic, comparison, bitwise, stack, memory, control flow, calldata, and
  `STATICCALL` — enough to run a contract that reads the exchange and returns
  the answer, which is the case that exists today.

  **Not** implemented: storage (`SLOAD`/`SSTORE`), contract creation, logs,
  `CALL` with value, `DELEGATECALL`, the environment opcodes, and the
  cryptographic ones. An unimplemented opcode halts with `:unknown-opcode`
  rather than being skipped — an interpreter that ignores what it does not
  know computes a wrong answer confidently, and this is not a place for
  confidence.

  Gas is counted flat, one unit per step, and bounded. It is not Ethereum's
  schedule and does not claim to be: what it is for here is stopping a loop,
  and pricing that is wrong in the same direction for every opcode still
  stops one."
  (:require [torihiki.evm :as bridge]))

;; ── 256-bit words ───────────────────────────────────────────────────────────
;;
;; Every value on the stack is an unsigned 256-bit integer. The two runtimes
;; have different big integers, so the arithmetic goes through these and
;; nothing else touches a raw number — the alternative is finding out on the
;; node that `+` coerced a BigInt.

(def ^:private modulus
  #?(:clj (.shiftLeft java.math.BigInteger/ONE 256)
     :cljs (js/BigInt.asUintN 256 (js/BigInt -1))))       ; 2^256 - 1

(defn- w
  "Coerce to a 256-bit word."
  [n]
  #?(:clj (.mod (biginteger n) (.shiftLeft java.math.BigInteger/ONE 256))
     :cljs (js/BigInt.asUintN 256 (js/BigInt n))))

(def ^:private zero (w 0))
(def ^:private one (w 1))

(defn- wrap
  "Back into range after an operation.

  `biginteger` first on the JVM, not a cast. Clojure's `+` on two
  `BigInteger`s returns a `clojure.lang.BigInt` — a different class — and the
  cast that assumed otherwise threw `ClassCastException` on the first
  addition. The two big-integer types on one runtime are a hazard this
  namespace has to absorb rather than pass on."
  [x]
  #?(:clj (.mod (biginteger x) (.shiftLeft java.math.BigInteger/ONE 256))
     :cljs (js/BigInt.asUintN 256 x)))

#?(:cljs (def ^:private jsdiv (js/Function. "a" "b" "return a / b;")))
#?(:cljs (def ^:private jsmod (js/Function. "a" "b" "return a % b;")))
#?(:cljs (def ^:private jslt  (js/Function. "a" "b" "return a < b;")))
#?(:cljs (def ^:private jsgt  (js/Function. "a" "b" "return a > b;")))
#?(:cljs (def ^:private jseq  (js/Function. "a" "b" "return a === b;")))
#?(:cljs (def ^:private jsand (js/Function. "a" "b" "return a & b;")))
#?(:cljs (def ^:private jsor  (js/Function. "a" "b" "return a | b;")))
#?(:cljs (def ^:private jsxor (js/Function. "a" "b" "return a ^ b;")))

(defn- w+ [a b] (wrap (+ a b)))
(defn- w- [a b] (wrap (- a b)))
(defn- w* [a b] (wrap (* a b)))

(defn- wzero? [a]
  #?(:clj (zero? (.signum ^java.math.BigInteger a)) :cljs (jseq a (js/BigInt 0))))

(defn- wdiv [a b]
  (if (wzero? b) zero
      #?(:clj (.divide ^java.math.BigInteger a b) :cljs (wrap (jsdiv a b)))))

(defn- wmod [a b]
  (if (wzero? b) zero
      #?(:clj (.mod ^java.math.BigInteger a b) :cljs (wrap (jsmod a b)))))

(defn- wlt [a b]
  #?(:clj (if (neg? (.compareTo ^java.math.BigInteger a b)) one zero)
     :cljs (if (jslt a b) one zero)))

(defn- wgt [a b]
  #?(:clj (if (pos? (.compareTo ^java.math.BigInteger a b)) one zero)
     :cljs (if (jsgt a b) one zero)))

(defn- weq [a b]
  #?(:clj (if (zero? (.compareTo ^java.math.BigInteger a b)) one zero)
     :cljs (if (jseq a b) one zero)))

(defn- wand [a b] #?(:clj (.and ^java.math.BigInteger a b) :cljs (wrap (jsand a b))))
(defn- wor  [a b] #?(:clj (.or ^java.math.BigInteger a b)  :cljs (wrap (jsor a b))))
(defn- wxor [a b] #?(:clj (.xor ^java.math.BigInteger a b) :cljs (wrap (jsxor a b))))
(defn- wnot [a] (w- (w- modulus one) (w- a zero)))

(defn- w->int
  "A word as a host integer, for memory offsets and jump targets. Only ever
  used where the value must be small — an offset that does not fit is a
  program addressing memory it will not get, and it halts."
  [x]
  #?(:clj (.longValue ^java.math.BigInteger x) :cljs (js/Number x)))

(defn- w->hex64 [x]
  (let [s #?(:clj (.toString ^java.math.BigInteger x 16) :cljs (.toString x 16))]
    (str (apply str (repeat (- 64 (count s)) \0)) s)))

(defn- hex->w [s] (w #?(:clj (java.math.BigInteger. ^String s 16) :cljs (js/BigInt (str "0x" s)))))

;; ── the machine ─────────────────────────────────────────────────────────────

(def ^:const gas-limit
  "Steps a call may take. **100000.**

  Flat, one per step. Not Ethereum's schedule and not claiming to be: what a
  limit is for here is stopping a loop, and being wrong in the same direction
  for every opcode still stops one. A contract that needs more than this is a
  contract this interpreter is not for yet."
  100000)

(defn- push-bytes
  "The immediate operand of a PUSHn, and where the counter lands after it."
  [code pc n]
  (let [end (min (count code) (+ pc 1 n))
        bs (subvec code (min (count code) (inc pc)) end)
        hex (apply str (map #(let [h #?(:clj (Integer/toHexString %)
                                        :cljs (.toString % 16))]
                               (if (= 1 (count h)) (str "0" h) h))
                            bs))]
    [(if (seq hex) (hex->w hex) zero) (+ pc 1 n)]))

(defn- mem-read [mem off len]
  (apply str (for [i (range len)] (let [b (get mem (+ off i) 0)
                                        h #?(:clj (Integer/toHexString b)
                                             :cljs (.toString b 16))]
                                    (if (= 1 (count h)) (str "0" h) h)))))

(defn- mem-write [mem off hex]
  (reduce (fn [m i]
            (assoc m (+ off i)
                   #?(:clj (Integer/parseInt (subs hex (* 2 i) (+ 2 (* 2 i))) 16)
                      :cljs (js/parseInt (subs hex (* 2 i) (+ 2 (* 2 i))) 16))))
          mem
          (range (quot (count hex) 2))))

(defn run
  "Execute `code` against the exchange. Returns
  `{:status :return|:revert|:halt :data hex :gas n}`.

  `code` is a vector of byte values, `calldata` a `0x`-prefixed string. The
  exchange is read-only from in here: the only way out to it is `STATICCALL`
  to the precompile, which is a read by construction — there is no opcode in
  this interpreter that can move a position, and adding one would make a
  second matching engine with none of the first one's checks."
  [ex code calldata]
  (let [cd (if (and (string? calldata) (> (count calldata) 2)) (subs calldata 2) "")]
    (loop [pc 0 stack [] mem {} gas 0 ret nil]
      (cond
        (>= gas gas-limit) {:status :halt :reason :out-of-gas :gas gas}
        ret ret
        (>= pc (count code)) {:status :return :data "" :gas gas}
        :else
        (let [op (nth code pc)
              pop1 (peek stack) s1 (when (seq stack) (pop stack))
              pop2 (when s1 (peek s1)) s2 (when (seq s1) (pop s1))]
          (case op
            0x00 {:status :return :data "" :gas gas}
            0x01 (recur (inc pc) (conj s2 (w+ pop1 pop2)) mem (inc gas) nil)
            0x02 (recur (inc pc) (conj s2 (w* pop1 pop2)) mem (inc gas) nil)
            0x03 (recur (inc pc) (conj s2 (w- pop1 pop2)) mem (inc gas) nil)
            0x04 (recur (inc pc) (conj s2 (wdiv pop1 pop2)) mem (inc gas) nil)
            0x06 (recur (inc pc) (conj s2 (wmod pop1 pop2)) mem (inc gas) nil)
            0x10 (recur (inc pc) (conj s2 (wlt pop1 pop2)) mem (inc gas) nil)
            0x11 (recur (inc pc) (conj s2 (wgt pop1 pop2)) mem (inc gas) nil)
            0x14 (recur (inc pc) (conj s2 (weq pop1 pop2)) mem (inc gas) nil)
            0x15 (recur (inc pc) (conj s1 (if (wzero? pop1) one zero)) mem (inc gas) nil)
            0x16 (recur (inc pc) (conj s2 (wand pop1 pop2)) mem (inc gas) nil)
            0x17 (recur (inc pc) (conj s2 (wor pop1 pop2)) mem (inc gas) nil)
            0x18 (recur (inc pc) (conj s2 (wxor pop1 pop2)) mem (inc gas) nil)
            0x19 (recur (inc pc) (conj s1 (wnot pop1)) mem (inc gas) nil)
            0x33 (recur (inc pc) (conj stack zero) mem (inc gas) nil)   ; CALLER: nobody
            0x35 (let [off (w->int pop1)                                 ; CALLDATALOAD
                       hex (subs (str cd (apply str (repeat 64 \0)))
                                 (* 2 off) (+ (* 2 off) 64))]
                   (recur (inc pc) (conj s1 (hex->w hex)) mem (inc gas) nil))
            0x36 (recur (inc pc) (conj stack (w (quot (count cd) 2))) mem (inc gas) nil)
            0x50 (recur (inc pc) s1 mem (inc gas) nil)                   ; POP
            0x51 (recur (inc pc)                                          ; MLOAD
                        (conj s1 (hex->w (mem-read mem (w->int pop1) 32)))
                        mem (inc gas) nil)
            0x52 (recur (inc pc) s2                                       ; MSTORE
                        (mem-write mem (w->int pop1) (w->hex64 pop2))
                        (inc gas) nil)
            0x56 (recur (w->int pop1) s1 mem (inc gas) nil)                ; JUMP
            0x57 (recur (if (wzero? pop2) (inc pc) (w->int pop1))          ; JUMPI
                        s2 mem (inc gas) nil)
            0x58 (recur (inc pc) (conj stack (w pc)) mem (inc gas) nil)     ; PC
            0x5b (recur (inc pc) stack mem (inc gas) nil)                   ; JUMPDEST
            0xf3 (recur (inc pc) s2 mem (inc gas)                           ; RETURN
                        {:status :return
                         :data (mem-read mem (w->int pop1) (w->int pop2))
                         :gas gas})
            0xfd (recur (inc pc) s2 mem (inc gas)                           ; REVERT
                        {:status :revert
                         :data (mem-read mem (w->int pop1) (w->int pop2))
                         :gas gas})
            0xfa ;; STATICCALL(gas, addr, argOff, argLen, retOff, retLen)
            ;; Bottom-to-top, which is the order `take-last` gives and the
            ;; REVERSE of the order the opcode is written in. Destructured the
            ;; other way round it read the gas as the return length and the
            ;; address as the gas: the call went to address 255, the bridge
            ;; said nothing, and the contract returned a word of zeroes —
            ;; indistinguishable from a real position of zero, which is the
            ;; failure this layer keeps having to be careful about.
            (let [[rl ro al ao addr g] (take-last 6 stack)
                  st (vec (drop-last 6 stack))
                  data (str "0x" (mem-read mem (w->int ao) (w->int al)))
                  ;; An address is the LOW 20 bytes of the word, which is 40
                  ;; hex characters and not 64. Built at full width it never
                  ;; equalled `core-address`, the bridge answered nil, and the
                  ;; contract got a word of zeroes back — a real-looking
                  ;; position of zero produced by an address that does not
                  ;; exist.
                  out (bridge/call ex (str "0x" (subs (w->hex64 addr) 24)) data)]
              (if out
                (recur (inc pc) (conj st one)
                       (mem-write mem (w->int ro) (subs out 2 (+ 2 (* 2 (min 32 (w->int rl))))))
                       (inc gas) nil)
                ;; A failed static call pushes zero and writes nothing. That is
                ;; the EVM's own convention and it matters here: the bridge
                ;; returns nil for a question the exchange did not understand,
                ;; and turning that into a zero WORD would be a plausible
                ;; answer rather than a failure the contract can branch on.
                (recur (inc pc) (conj st zero) mem (inc gas) nil)))
            ;; PUSH1..PUSH32
            (if (and (>= op 0x60) (<= op 0x7f))
              (let [[v pc'] (push-bytes code pc (- op 0x5f))]
                (recur pc' (conj stack v) mem (inc gas) nil))
              ;; DUP1..DUP16
              (if (and (>= op 0x80) (<= op 0x8f))
                (let [n (- op 0x7f)]
                  (recur (inc pc) (conj stack (nth stack (- (count stack) n))) mem (inc gas) nil))
                ;; SWAP1..SWAP16
                (if (and (>= op 0x90) (<= op 0x9f))
                  (let [n (- op 0x8f)
                        i (- (count stack) 1) j (- (count stack) 1 n)]
                    (recur (inc pc)
                           (assoc stack i (nth stack j) j (nth stack i))
                           mem (inc gas) nil))
                  {:status :halt :reason :unknown-opcode :opcode op :pc pc
                   :gas gas})))))))))
