(ns torihiki.evm-interp-test
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.clearing :as cl]
            [torihiki.evm :as bridge]
            [torihiki.evm.interp :as evm]
            [torihiki.state :as st]))

(def ^:private mkt
  (assoc (cl/market {:id 1 :max-leverage 20 :tick 1 :lot 1})
         :taker-fee-rate 0 :maker-fee-rate 0))

(defn- ex-with-a-position []
  (-> (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 65536 :ev-cap 65536}})
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (as-> e (reduce (fn [x a] (st/apply-tx x {:tx :deposit :account a :amount 1000000}))
                      e [700 701]))
      (st/apply-tx {:tx :order :account 700 :market 1 :side 0 :level 500 :qty 4 :flags 0})
      (st/apply-tx {:tx :order :account 701 :market 1 :side 1 :level 500 :qty 4 :flags 0})))

(defn- push32 [hex]
  ;; PUSH32 <hex>, as bytes
  (into [0x7f] (map #(#?(:clj Integer/parseInt :cljs js/parseInt)
                        (subs hex (* 2 %) (+ 2 (* 2 %))) 16)
                    (range 32))))

(deftest arithmetic-and-return
  (let [r (evm/run {} [0x60 2 0x60 3 0x01 0x60 0 0x52 0x60 32 0x60 0 0xf3] "0x")]
    (is (= :return (:status r)))
    (is (= 5 (#?(:clj Integer/parseInt :cljs js/parseInt) (:data r) 16)))))

(deftest a-contract-reads-a-position-through-the-precompile
  (testing "the whole reason for an EVM beside a venue. Everything else here
            is a stack machine; this is the opcode that makes it worth having."
    (let [ex (ex-with-a-position)
          ;; The calldata for position(700, 1), written into memory at 0,
          ;; then STATICCALL(gas, 0x801, 0, 36, 128, 32), then return it.
          cd (bridge/encode-call :position 700 1)
          sel (subs cd 2 10)
          code (vec (concat
                     ;; selector, left-aligned at memory 0
                     (push32 (str sel (apply str (repeat 56 \0))))
                     [0x60 0x00 0x52]
                     ;; account word at 4, market word at 36
                     (push32 (subs cd 10 74)) [0x60 0x04 0x52]
                     (push32 (subs cd 74 138)) [0x60 0x24 0x52]
                     ;; STATICCALL: push args in reverse (retLen retOff argLen argOff addr gas)
                     [0x60 0x20                      ; retLen 32
                      0x60 0x80                      ; retOff 128
                      0x60 0x44                      ; argLen 68
                      0x60 0x00                      ; argOff 0
                      0x61 0x08 0x01                 ; addr 0x0801  (PUSH2)
                      0x60 0xff]                     ; gas
                     [0xfa]
                     [0x50]                          ; drop the success flag
                     [0x60 0x20 0x60 0x80 0xf3]))    ; return 32 bytes at 128
          r (evm/run ex code "0x")]
      (is (= :return (:status r)) (pr-str r))
      (is (= 4 (#?(:clj Integer/parseInt :cljs js/parseInt) (:data r) 16))
          "the contract did not read the buyer's position of 4"))))

(deftest an-unknown-opcode-halts-rather-than-being-skipped
  ;; An interpreter that ignores what it does not know computes a wrong answer
  ;; confidently. This is not a place for confidence.
  (let [r (evm/run {} [0x60 1 0x0c 0x60 0 0x52] "0x")]
    (is (= :halt (:status r)))
    (is (= :unknown-opcode (:reason r)))
    (is (= 0x0c (:opcode r)))))

(deftest a-loop-is-stopped
  ;; JUMPDEST; PUSH1 0; JUMP — forever, without a limit.
  (let [r (evm/run {} [0x5b 0x60 0x00 0x56] "0x")]
    (is (= :halt (:status r)))
    (is (= :out-of-gas (:reason r)))))

(defn- hex->n [s] (#?(:clj Integer/parseInt :cljs js/parseInt) s 16))

(deftest signed-arithmetic-reads-the-top-bit
  (testing "a position is signed and a word is not. An interpreter whose SDIV
            and SLT read the same bits as unsigned would price a short as an
            astronomically large long — the exact failure the bridge's two's
            complement exists to prevent, one layer up."
    ;; PUSH1 2, PUSH32 -4, SDIV -> -2 ; and SLT(-4, 1) -> 1
    (let [neg4 (push32 (str (apply str (repeat 62 \f)) "fc"))
          sdiv (evm/run {} (vec (concat [0x60 2] neg4 [0x05 0x60 0 0x52 0x60 32 0x60 0 0xf3]))
                        "0x")
          slt (evm/run {} (vec (concat [0x60 1] neg4 [0x12 0x60 0 0x52 0x60 32 0x60 0 0xf3]))
                       "0x")]
      (is (= (str (apply str (repeat 62 \f)) "fe") (:data sdiv))
          "-4 / 2 did not come back as -2")
      (is (= 1 (hex->n (:data slt))) "-4 was not less than 1"))))

(deftest storage-survives-within-a-call-and-comes-back
  ;; PUSH1 7, PUSH1 3, SSTORE ; PUSH1 3, SLOAD ; return it
  (let [r (evm/run {} [0x60 7 0x60 3 0x55 0x60 3 0x54 0x60 0 0x52 0x60 32 0x60 0 0xf3] "0x")]
    (is (= 7 (hex->n (:data r))) "SLOAD did not see what SSTORE wrote")
    (is (= 1 (count (:storage r)))
        "what the contract wrote did not come back with the result — a store
         that vanishes is a store nobody can check")))

(deftest a-shift-past-the-word-is-zero-not-a-wrap
  ;; PUSH1 1, PUSH1 255, SHL -> the sign bit; PUSH1 1, PUSH1 256, SHL -> 0
  (let [at255 (evm/run {} [0x60 1 0x60 255 0x1b 0x60 0 0x52 0x60 32 0x60 0 0xf3] "0x")
        at256 (evm/run {} [0x60 1 0x61 0x01 0x00 0x1b 0x60 0 0x52 0x60 32 0x60 0 0xf3] "0x")]
    (is (= \8 (first (:data at255))) "1 << 255 did not land on the sign bit")
    (is (= 0 (hex->n (:data at256))) "1 << 256 wrapped instead of vanishing")))

(deftest a-log-is-kept-with-the-result
  ;; PUSH1 32, PUSH1 0, LOG0
  (let [r (evm/run {} [0x60 32 0x60 0 0xa0] "0x")]
    (is (= 1 (count (:logs r))))
    (is (= [] (:topics (first (:logs r)))))))
