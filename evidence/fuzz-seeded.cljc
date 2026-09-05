;; falsify-3 harness — seeded fuzz over adversarial block sequences.
;;
;; Claim under test: JVM and nbb, folding the SAME seed-generated
;; adversarial block sequence, reach the SAME flat root, state root,
;; resting count, fill count and rejection count. This mechanises
;; "raising the suspicion itself" (status/maturity.md NEXT): instead of
;; one hand-written suspicion per iteration, a seeded generator raises
;; hundreds of adversarial situations per run, and cross-runtime digest
;; equality is the verdict.
;;
;; No code in src/ or test/ is touched by this file. It lives in
;; evidence/ like the falsify-1 harness and is invoked:
;;
;;   JVM:  clojure -M -e '(load-file "evidence/fuzz-seeded.cljc")'
;;   nbb:  nbb --classpath "src:<bytes>:<chain>:<merkle-sum>" \
;;             -e '(load-file "evidence/fuzz-seeded.cljc")'
;;
;; The two stdout dumps must be byte-identical.
;;
;; Determinism rules honoured (README invariants): integers only, no
;; wall clock (:ts is a pure function of block index), no floating
;; point, no unordered iteration (the only fold over harvested oids
;; sorts first). The PRNG is xorshift32 — bitwise math only, which
;; agrees between the JVM and JS by specification. A multiply-based
;; PRNG would NOT: JS loses precision past 2^53 and mulberry32's
;; 32x32 multiply crosses it, so xorshift32 is the honest choice here.
(ns fuzz-seeded
  (:require [torihiki.state :as st]
            [torihiki.book :as bk]
            [torihiki.snapshot :as snap]))

;; ── PRNG: xorshift32, 32-bit bitwise math only ─────────────────────────────
(defn xorshift32
  "One xorshift32 step. Both runtimes define <<, >> and xor on 32-bit
  integers identically, so the state sequence is bit-identical."
  [x]
  (-> x
      (bit-xor (bit-shift-left x 13))
      (bit-xor (unsigned-bit-shift-right x 17))
      (bit-xor (bit-shift-left x 5))
      (bit-and 0xFFFFFFFF)))

(defn rng-draw
  "Draws one uniform integer in [0, n) from state atom a; returns the draw."
  [a n]
  (let [s (swap! a xorshift32)]
    (mod (bit-and (unsigned-bit-shift-right s 1) 0x7FFFFFFF) n)))

;; ── fuzz generation ────────────────────────────────────────────────────────
(def accounts [1 2 3 4 5 6])
(def n-levels 1024)
(def cap 4096)
(def ev-cap 4096)

(defn fresh []
  (st/new-exchange {:market {:id 1 :max-leverage 40 :tick 1 :lot 1}
                    :book-opts {:n-levels n-levels :cap cap :ev-cap ev-cap}}))

(defn harvest-oids
  "Live `{:oid :owner}` pairs, sorted by oid — an unordered fold here would
  be exactly the nondeterminism the engine forbids. Levels are sampled every
  64 rungs of the ladder; orders are placed on the same grid so cancels hit."
  [ex]
  (let [b (get-in ex [:books 1])]
    (sort-by :oid
             (for [side [0 1]
                   l (range 128 n-levels 64)
                   o (bk/level-orders b side l)]
               (select-keys o [:oid :owner])))))

(defn- maybe-bad-order
  "A slice of orders is malformed on purpose: bad side, zero qty, or a level
  off the ladder. validate must refuse them and the refusal must be identical
  on both runtimes."
  [a tx]
  (case (rng-draw a 20)
    0 (assoc tx :side 2)
    1 (assoc tx :qty 0)
    2 (assoc tx :level n-levels)
    tx))

(defn gen-tx
  "One adversarial transaction from the live state's harvested oids.
  The case arms partition 0..99; their weights are the adversarial mix."
  [a live]
  (case (rng-draw a 100)
    ;; 0-7: deposits
    (0 1 2 3 4 5 6 7)
    {:tx :deposit :account (nth accounts (rng-draw a 6))
     :amount (inc (* 10000 (rng-draw a 500)))}
    ;; 8-14: oracle moves the price hard — drives marks, margin, liquidation
    (8 9 10 11 12 13 14)
    {:tx :oracle :market 1 :price (+ 200 (* 20 (rng-draw a 80)))}
    ;; 15-54: orders around 500 with wide tails so both sides cross
    (15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34
        35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54)
    (maybe-bad-order a
                     {:tx :order :market 1
                      :account (nth accounts (rng-draw a 6))
                      :side (rng-draw a 2)
                      :level (+ 250 (* 5 (rng-draw a 101)))
                      :qty (inc (rng-draw a 40))})
    ;; 55-74: cancels — right owner, wrong owner, or a bogus oid
    (55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74)
    (let [r (rng-draw a 10)]
      (if (or (empty? live) (< r 2))
        {:tx :cancel :market 1 :account (nth accounts (rng-draw a 6))
         :oid (rng-draw a 100000)}
        (let [o (nth live (rng-draw a (count live)))]
          {:tx :cancel :market 1
           :account (if (< r 4) (nth accounts (rng-draw a 6)) (:owner o))
           :oid (:oid o)})))
    ;; 75-80: cancel-all
    (75 76 77 78 79 80)
    {:tx :cancel-all :market 1 :account (nth accounts (rng-draw a 6))}
    ;; 81-87: amend a live order (falls back to a fresh order when none rest)
    (81 82 83 84 85 86 87)
    (if (empty? live)
      {:tx :order :market 1 :account (nth accounts (rng-draw a 6))
       :side (rng-draw a 2) :level (+ 250 (* 5 (rng-draw a 101)))
       :qty (inc (rng-draw a 40))}
      (let [o (nth live (rng-draw a (count live)))]
        {:tx :amend :market 1 :account (:owner o) :oid (:oid o)
         :level (+ 250 (* 5 (rng-draw a 101))) :qty (inc (rng-draw a 40))}))
    ;; 88-92: triggers above or below
    (88 89 90 91 92)
    {:tx :trigger :market 1 :account (nth accounts (rng-draw a 6))
     :trigger-price (+ 300 (* 10 (rng-draw a 60)))
     :direction (if (zero? (rng-draw a 2)) :above :below)
     :order {:side (rng-draw a 2) :level (+ 250 (* 5 (rng-draw a 101)))
             :qty (inc (rng-draw a 40))}}
    ;; 93-95: cancel-trigger (the id is usually nonsense — refusals count too)
    (93 94 95)
    {:tx :cancel-trigger :market 1 :id (rng-draw a 500)}
    ;; 96-98: liquidate / funding settle — mostly no-ops, sometimes not
    (96 97 98)
    {:tx (if (zero? (rng-draw a 2)) :liquidate :funding-settle)
     :market 1 :account (nth accounts (rng-draw a 6))}
    ;; 99: filler deposit
    {:tx :deposit :account (nth accounts (rng-draw a 6))
     :amount (inc (* 10000 (rng-draw a 500)))}))

;; ── fold one seed ──────────────────────────────────────────────────────────
(def n-blocks 24)
(def txs-per-block 48)

(defn fold-seed
  "Folds `n-blocks` adversarial blocks over one exchange; a snapshot/restore
  round-trip sits mid-fold. Returns the digest vector: flat root, state
  root, resting count, rejection count, fill count, snapshot-parity flag."
  [seed]
  (let [s0 (bit-and seed 0xFFFFFFFF)
      a (atom (if (zero? s0) 0x9E3779B9 s0))
        ex0 (st/apply-block
             (fresh)
             {:height 0 :ts 0
              :txs (vec (for [ac accounts]
                          {:tx :deposit :account ac :amount 1000000000}))})]
    (loop [ex ex0, live [], h 1, b 0, prev-fills 0, rej 0, snap-ok true]
      (if (= b n-blocks)
        (let [b1 (get-in ex [:books 1])]
          [(str (st/flat-root ex)) (str (st/state-root ex))
           (str (bk/resting-count b1)) (str rej)
           (str prev-fills) (str snap-ok)])
        (let [blk {:height h :ts (* 1000 (inc h))
                   :txs (vec (repeatedly txs-per-block #(gen-tx a live)))}
              ex' (st/apply-block ex blk)
              fills' (- (bk/event-count (get-in ex' [:books 1])) prev-fills)
              live' (harvest-oids ex')
              rej' (+ rej (count (:rejected ex')))]
          (if (= b 11)
            ;; mid-fold snapshot round-trip: capture is pure data out, restore
            ;; pure data in, so the roots on both sides must agree byte for byte
            (let [r (snap/restore (snap/capture ex'))
                  ok? (and (= (str (st/flat-root ex')) (str (st/flat-root r)))
                           (= (str (st/state-root ex')) (str (st/state-root r))))]
              (recur (snap/restore (snap/capture ex')) live' (inc h) (inc b)
                     (bk/event-count (get-in ex' [:books 1])) rej' (and snap-ok ok?)))
            (recur ex' live' (inc h) (inc b)
                   (+ prev-fills fills') rej' snap-ok)))))))

(def n-seeds 16)

(defn run
  "Prints one line per seed; the cross-runtime check is that both runtimes
  print identical lines."
  []
  (println "fuzz-seeded" n-blocks "blocks x" txs-per-block "txs,"
           n-seeds "seeds, 6 accounts, 1 market, xorshift32")
  (doseq [seed (range n-seeds)]
    (println "seed" seed (pr-str (fold-seed seed))))
  (println "done"))
