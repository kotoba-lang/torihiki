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

  Storage, signed arithmetic, shifts, `EXP`, and logs are here too.

  **Not** implemented: contract creation, `CALL` with value, `DELEGATECALL`,
  most environment opcodes.

  `KECCAK256` is here, over `torihiki.keccak` — which is why mappings work: a
  Solidity mapping slot is `keccak256(key . slot)` and nothing else computes
  the same address. An unimplemented opcode halts with `:unknown-opcode`
  rather than being skipped — an interpreter that ignores what it does not
  know computes a wrong answer confidently, and this is not a place for
  confidence.

  Gas is counted flat, one unit per step, and bounded. It is not Ethereum's
  schedule and does not claim to be: what it is for here is stopping a loop,
  and pricing that is wrong in the same direction for every opcode still
  stops one."
  (:require [torihiki.evm :as bridge]
            [torihiki.keccak :as keccak]))

;; ── 256-bit words ───────────────────────────────────────────────────────────
;;
;; Every value on the stack is an unsigned 256-bit integer. The two runtimes
;; have different big integers, so the arithmetic goes through these and
;; nothing else touches a raw number — the alternative is finding out on the
;; node that `+` coerced a BigInt.

(def ^:private modulus
  "2^256 — on BOTH sides.

  The ClojureScript half was `(js/BigInt.asUintN 256 (js/BigInt -1))`, which
  is 2^256 MINUS ONE. Everything derived from it was off by one there and
  right on the JVM, so `NOT` would have differed between the runtime the tests
  run on and the runtime the node runs on. The two lines have to name the same
  number or the parity this file exists for is decorative."
  #?(:clj (.shiftLeft java.math.BigInteger/ONE 256)
     :cljs (js/BigInt "115792089237316195423570985008687907853269984665640564039457584007913129639936")))

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
#?(:cljs (def ^:private jsshl (js/Function. "a" "b" "return a << b;")))
#?(:cljs (def ^:private jsshr (js/Function. "a" "b" "return a >> b;")))

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

(def ^:private sign-bit (w #?(:clj (.shiftLeft java.math.BigInteger/ONE 255)
                              :cljs (js/BigInt "57896044618658097711785492504343953926634992332820282019728792003956564819968"))))

(defn- neg?w
  "Is this word negative when read as `int256`? The top bit, which is the only
  thing that makes a word signed — the value itself is the same 256 bits."
  [a]
  (not (wzero? (wand a sign-bit))))

(defn- ->signed
  "The signed value of a word, as a big integer that may be negative.

  NOT `w-`, which wraps: `(w- a modulus)` is `wrap(a - 2^256)`, which is `a`
  again. SDIV(-4, 2) came back as 2^255 - 2 — the unsigned quotient, wearing
  the shape of a real answer. On the ClojureScript side `BigInt.asIntN` says
  this in one call and is the same conversion `asUintN` undoes."
  [a]
  #?(:clj (if (neg?w a) (.subtract (biginteger a) (biginteger modulus)) (biginteger a))
     :cljs (js/BigInt.asIntN 256 a)))

(defn- <-signed [n] (w n))

(defn- wsdiv [a b]
  (if (wzero? b) zero
      (let [x (->signed a) y (->signed b)]
        (<-signed #?(:clj (.divide (biginteger x) (biginteger y))
                     :cljs (jsdiv x y))))))

(defn- wsmod [a b]
  (if (wzero? b) zero
      (let [x (->signed a) y (->signed b)]
        (<-signed #?(:clj (.remainder (biginteger x) (biginteger y))
                     :cljs (jsmod x y))))))

(defn- wslt [a b]
  (let [x (->signed a) y (->signed b)]
    #?(:clj (if (neg? (.compareTo (biginteger x) (biginteger y))) one zero)
       :cljs (if (jslt x y) one zero))))

(defn- wsgt [a b]
  (let [x (->signed a) y (->signed b)]
    #?(:clj (if (pos? (.compareTo (biginteger x) (biginteger y))) one zero)
       :cljs (if (jsgt x y) one zero))))

(defn- wshl [shift a]
  (let [n (w->int shift)]
    (if (>= n 256) zero
        #?(:clj (wrap (.shiftLeft (biginteger a) n))
           :cljs (wrap (jsshl a (js/BigInt n)))))))

(defn- wshr [shift a]
  (let [n (w->int shift)]
    (if (>= n 256) zero
        #?(:clj (.shiftRight (biginteger a) n)
           :cljs (wrap (jsshr a (js/BigInt n)))))))

(defn- wexp [a b]
  ;; Bounded by the modulus at every step, so a large exponent costs time and
  ;; not memory. `modPow` on the JVM does the same thing in one call.
  #?(:clj (.modPow (biginteger a) (biginteger b)
                   (.shiftLeft java.math.BigInteger/ONE 256))
     :cljs (loop [base (wrap a) e b acc one]
             (if (wzero? e) acc
                 (recur (wrap (* base base))
                        (jsdiv e (js/BigInt 2))
                        (if (wzero? (jsmod e (js/BigInt 2))) acc (wrap (* acc base))))))))

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

(def ^:private zero-address "0x0000000000000000000000000000000000000000")

(defn- addr-of-word
  "The low 20 bytes of a word, as an address. An address built at full width
  never equals one written the ordinary way, and the call goes nowhere while
  looking like it went somewhere."
  [x]
  (str "0x" (subs (w->hex64 x) 24)))

(def ^:const max-depth
  "How deep calls may nest. **64.**

  The EVM allows 1024. This is lower on purpose: each level here is a
  ClojureScript stack frame as well as an EVM one, and a limit that the host
  reaches first is a limit that shows up as a crash instead of as a failed
  call. A contract that needs more than 64 is a contract this interpreter is
  not for yet."
  64)

(declare run)

(defn- do-call
  "`CALL`, `STATICCALL` and `DELEGATECALL`, which differ in two things: whose
  storage the code runs against, and who the code sees as its caller.

  `DELEGATECALL` runs another contract's CODE against THIS contract's storage
  and keeps the original caller — that is the whole of what a library or a
  proxy is, and getting it backwards means an upgrade wipes the storage it was
  meant to preserve. So the two are one function with two flags rather than
  two functions that have to agree.

  The exchange precompile answers first, whichever form is used: it is a read
  either way, and code that reaches it through `CALL` should not get a
  different answer than code that reaches it through `STATICCALL`."
  [ex world ctx {:keys [to args delegate?]}]
  (let [pre (bridge/call ex to args)]
    (cond
      pre {:status :return :data (subs pre 2) :world world :logs []}

      (>= (:depth ctx) max-depth)
      {:status :halt :reason :call-depth-exceeded :world world :logs []}

      :else
      (let [code (get-in world [to :code])]
        (if (nil? code)
          ;; Calling an address with no code succeeds and returns nothing.
          ;; That is the EVM's rule and it matters: a contract that treats it
          ;; as a failure would refuse every plain transfer.
          {:status :return :data "" :world world :logs []}
          (run ex world
               (if delegate?
                 (assoc ctx :depth (inc (:depth ctx)))
                 {:address to :caller (:address ctx) :depth (inc (:depth ctx))})
               code args))))))

(defn create-address
  "Where `CREATE2` puts a contract.

  `torihiki.keccak/create2-address`, not a second copy. The chain computes
  this too — `:evm-deploy` puts code at the same address — and two derivations
  that have to agree are one derivation with an extra chance to disagree."
  [sender salt-hex init-code]
  ;; The CODE, not its digest. `create2-address` hashes the code itself —
  ;; handing it a hash hashed twice and put every contract at an address no
  ;; other implementation would name.
  (keccak/create2-address sender salt-hex (keccak/bytes->hex init-code)))

(defn run
  "Execute `code` against the exchange. Returns
  `{:status :return|:revert|:halt :data hex :world w :logs ls :gas n}`.

  The state is a map rather than a dozen loop bindings. It began as the
  bindings and grew to nine, and adding `world` and the return-data buffer
  would have meant editing thirty-five `recur` forms in their right positions
  — which is a mechanical change that fails silently by putting the memory
  where the storage goes.

  `world` is `{address {:code [bytes] :storage {}}}`. `ctx` says which account
  the code is running AS, who called it, and how deep. The exchange itself is
  read-only from in here: the only way to it is the precompile, and there is
  no opcode that writes to it."
  ([ex code calldata]
   (run ex {} {:address zero-address :caller zero-address :depth 0} code calldata))
  ([ex world ctx code calldata]
   (let [cd (if (and (string? calldata) (> (count calldata) 2)) (subs calldata 2) "")
         self (:address ctx)]
     (loop [st {:pc 0 :stack [] :mem {} :logs [] :gas 0 :rdata ""
                :sto (get-in world [self :storage] {})
                :world world}]
       (let [{:keys [pc stack mem sto logs gas world rdata]} st
             done (fn [m] (merge {:world (assoc-in world [self :storage] sto)
                                  :logs logs :gas gas :data ""} m))]
         (cond
           (>= gas gas-limit) (done {:status :halt :reason :out-of-gas})
           (>= pc (count code)) (done {:status :return})
           :else
           (let [op (nth code pc)
                 a (peek stack) s1 (when (seq stack) (pop stack))
                 b (when (seq s1) (peek s1)) s2 (when (seq s1) (pop s1))
                 c* (when (and s2 (seq s2)) (peek s2)) s3 (when (and s2 (seq s2)) (pop s2))
                 ;; RETURNS the next state; it does not recur.
                 ;;
                 ;; It did, and `recur` inside a `fn` targets the FN, not the
                 ;; loop — so every opcode called itself with one argument
                 ;; forever and the test run had to be killed at ten minutes.
                 ;; The loop below is the only place that recurs, and a branch
                 ;; is terminal exactly when what it returns carries `:status`.
                 nxt (fn [m] (merge st (assoc m :gas (inc gas))))
                 push (fn [stk v] (conj stk v))]
             (let [r
                   (case op
                     0x00 (done {:status :return})
               0x01 (nxt {:pc (inc pc) :stack (push s2 (w+ a b))})
               0x02 (nxt {:pc (inc pc) :stack (push s2 (w* a b))})
               0x03 (nxt {:pc (inc pc) :stack (push s2 (w- a b))})
               0x04 (nxt {:pc (inc pc) :stack (push s2 (wdiv a b))})
               0x05 (nxt {:pc (inc pc) :stack (push s2 (wsdiv a b))})
               0x06 (nxt {:pc (inc pc) :stack (push s2 (wmod a b))})
               0x07 (nxt {:pc (inc pc) :stack (push s2 (wsmod a b))})
               0x08 (nxt {:pc (inc pc) :stack (push s3 (wmod (w+ a b) c*))})
               0x09 (nxt {:pc (inc pc) :stack (push s3 (wmod (w* a b) c*))})
               0x0a (nxt {:pc (inc pc) :stack (push s2 (wexp a b))})
               0x10 (nxt {:pc (inc pc) :stack (push s2 (wlt a b))})
               0x11 (nxt {:pc (inc pc) :stack (push s2 (wgt a b))})
               0x12 (nxt {:pc (inc pc) :stack (push s2 (wslt a b))})
               0x13 (nxt {:pc (inc pc) :stack (push s2 (wsgt a b))})
               0x14 (nxt {:pc (inc pc) :stack (push s2 (weq a b))})
               0x15 (nxt {:pc (inc pc) :stack (push s1 (if (wzero? a) one zero))})
               0x16 (nxt {:pc (inc pc) :stack (push s2 (wand a b))})
               0x17 (nxt {:pc (inc pc) :stack (push s2 (wor a b))})
               0x18 (nxt {:pc (inc pc) :stack (push s2 (wxor a b))})
               0x19 (nxt {:pc (inc pc) :stack (push s1 (wnot a))})
               0x1a (nxt {:pc (inc pc)
                          :stack (push s2 (let [i (w->int a)]
                                            (if (>= i 32) zero
                                                (hex->w (subs (w->hex64 b) (* 2 i) (+ 2 (* 2 i)))))))})
               0x1b (nxt {:pc (inc pc) :stack (push s2 (wshl a b))})
               0x1c (nxt {:pc (inc pc) :stack (push s2 (wshr a b))})
               0x20 (nxt {:pc (inc pc)
                          :stack (push s2 (hex->w (keccak/digest-of-hex
                                                   (mem-read mem (w->int a) (w->int b)))))})
               0x30 (nxt {:pc (inc pc) :stack (push stack (hex->w (subs self 2)))})   ; ADDRESS
               0x33 (nxt {:pc (inc pc) :stack (push stack (hex->w (subs (:caller ctx) 2)))})
               0x34 (nxt {:pc (inc pc) :stack (push stack zero)})                     ; CALLVALUE
               0x35 (nxt {:pc (inc pc)
                          :stack (push s1 (hex->w (subs (str cd (apply str (repeat 64 \0)))
                                                        (* 2 (w->int a)) (+ (* 2 (w->int a)) 64))))})
               0x36 (nxt {:pc (inc pc) :stack (push stack (w (quot (count cd) 2)))})
               0x38 (nxt {:pc (inc pc) :stack (push stack (w (count code)))})         ; CODESIZE
               0x3d (nxt {:pc (inc pc) :stack (push stack (w (quot (count rdata) 2)))})
               0x3e (nxt {:pc (inc pc) :stack s3                                      ; RETURNDATACOPY
                          :mem (mem-write mem (w->int a)
                                          (subs rdata (* 2 (w->int b))
                                                (min (count rdata) (+ (* 2 (w->int b)) (* 2 (w->int c*))))))})
               0x50 (nxt {:pc (inc pc) :stack s1})
               0x51 (nxt {:pc (inc pc) :stack (push s1 (hex->w (mem-read mem (w->int a) 32)))})
               0x52 (nxt {:pc (inc pc) :stack s2 :mem (mem-write mem (w->int a) (w->hex64 b))})
               0x53 (nxt {:pc (inc pc) :stack s2 :mem (assoc mem (w->int a) (w->int (wmod b (w 256))))})
               0x54 (nxt {:pc (inc pc) :stack (push s1 (get sto (w->hex64 a) zero))})
               0x55 (nxt {:pc (inc pc) :stack s2 :sto (assoc sto (w->hex64 a) b)})
               0x56 (nxt {:pc (w->int a) :stack s1})
               0x57 (nxt {:pc (if (wzero? b) (inc pc) (w->int a)) :stack s2})
               0x58 (nxt {:pc (inc pc) :stack (push stack (w pc))})
               0x59 (nxt {:pc (inc pc)
                          :stack (push stack (w (if (seq mem)
                                                  (* 32 (inc (quot (apply max (keys mem)) 32))) 0)))})
               0x5a (nxt {:pc (inc pc) :stack (push stack (w (- gas-limit gas)))})
               0x5b (nxt {:pc (inc pc)})
               0xa0 (nxt {:pc (inc pc) :stack s2
                          :logs (conj logs {:topics []
                                            :data (mem-read mem (w->int a) (w->int b))})})
               0xa1 (nxt {:pc (inc pc) :stack s3
                          :logs (conj logs {:topics [(w->hex64 c*)]
                                            :data (mem-read mem (w->int a) (w->int b))})})
               0xf3 (done {:status :return :data (mem-read mem (w->int a) (w->int b))})
               0xfd (done {:status :revert :data (mem-read mem (w->int a) (w->int b))})

               (0xf1 0xf2 0xfa)                                     ; CALL, CALLCODE, STATICCALL
               (let [[rl ro al ao addr _g] (take-last 6 stack)
                     stk (vec (drop-last 6 stack))
                     args (str "0x" (mem-read mem (w->int ao) (w->int al)))
                     r (do-call ex (assoc-in world [self :storage] sto)
                                ctx {:to (addr-of-word addr) :args args})
                     ok? (= :return (:status r))
                     out (or (:data r) "")]
                 (nxt {:pc (inc pc)
                       :stack (push stk (if ok? one zero))
                       :world (:world r)
                       :logs (into logs (:logs r))
                       :rdata out
                       :mem (if (and ok? (pos? (w->int rl)))
                              (mem-write mem (w->int ro)
                                         (subs out 0 (min (count out) (* 2 (w->int rl)))))
                              mem)}))

               0xf4                                                  ; DELEGATECALL
               (let [[rl ro al ao addr _g] (take-last 6 stack)
                     stk (vec (drop-last 6 stack))
                     args (str "0x" (mem-read mem (w->int ao) (w->int al)))
                     r (do-call ex (assoc-in world [self :storage] sto)
                                ctx {:to (addr-of-word addr) :args args :delegate? true})
                     ok? (= :return (:status r))
                     out (or (:data r) "")]
                 (nxt {:pc (inc pc)
                       :stack (push stk (if ok? one zero))
                       ;; The callee ran as US, so its storage IS ours and has
                       ;; to come back into `sto`. Taking the world's copy and
                       ;; leaving `sto` alone would drop everything a library
                       ;; wrote the moment this frame returned.
                       :sto (get-in (:world r) [self :storage] sto)
                       :world (:world r)
                       :logs (into logs (:logs r))
                       :rdata out
                       :mem (if (and ok? (pos? (w->int rl)))
                              (mem-write mem (w->int ro)
                                         (subs out 0 (min (count out) (* 2 (w->int rl)))))
                              mem)}))

               0xf5                                                  ; CREATE2
               (let [[salt len off _value] (take-last 4 stack)
                     stk (vec (drop-last 4 stack))
                     init (mem-read mem (w->int off) (w->int len))
                     addr (create-address self (w->hex64 salt) (keccak/hex->bytes init))
                     r (run ex (assoc-in world [self :storage] sto)
                            {:address addr :caller self :depth (inc (:depth ctx))}
                            (keccak/hex->bytes init) "0x")]
                 (if (= :return (:status r))
                   ;; What the initcode RETURNED is the deployed code. The
                   ;; initcode itself is not: a constructor that ran and
                   ;; returned nothing deploys an empty account, which is what
                   ;; every other implementation does.
                   (nxt {:pc (inc pc)
                         :stack (push stk (hex->w (subs addr 2)))
                         :world (assoc-in (:world r) [addr :code] (keccak/hex->bytes (:data r)))
                         :logs (into logs (:logs r))})
                   (nxt {:pc (inc pc) :stack (push stk zero) :world world})))

               (cond
                 (and (>= op 0x60) (<= op 0x7f))
                 (let [[v pc'] (push-bytes code pc (- op 0x5f))]
                   (nxt {:pc pc' :stack (push stack v)}))

                 (and (>= op 0x80) (<= op 0x8f))
                 (let [n (- op 0x7f)]
                   (nxt {:pc (inc pc) :stack (push stack (nth stack (- (count stack) n)))}))

                 (and (>= op 0x90) (<= op 0x9f))
                 (let [n (- op 0x8f)
                       i (dec (count stack)) j (- (count stack) 1 n)]
                   (nxt {:pc (inc pc) :stack (assoc stack i (nth stack j) j (nth stack i))}))

                 :else
                 (done {:status :halt :reason :unknown-opcode :opcode op :pc pc})))]
               (if (:status r) r (recur r))))))))))
