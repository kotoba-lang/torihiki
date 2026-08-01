(ns torihiki.bench
  "Throughput measurement for the matching engine.

  The number this prints is the whole reason the book is built out of flat
  arrays instead of Clojure collections, so it has to stay runnable and
  honest. Two rules it follows:

  1. The tape is generated INTO PRIMITIVE ARRAYS BEFORE the clock starts.
     Generating orders inside the timed loop measures the generator, and a
     generator that allocates would dominate the engine.

  2. The workload is not a straight line of resting orders. A book that only
     grows never exercises matching, cancellation, or the bit-ladder cascade,
     and it reports a number no real market would reproduce. The mix here is
     roughly what a live perp book sees: mostly quoting and cancelling, with
     a minority of aggressive orders that actually cross.

  3. The mix REACHES A STEADY STATE. An earlier version placed 58% and
     cancelled 30%, so the book grew without bound and exhausted the order
     slab at ten million operations. That is not a stress test, it is a
     different benchmark — one that measures cache behaviour on a book no
     venue would ever hold. Real venues run cancel/place ratios near one, so
     resting depth here hovers instead of climbing, and the number describes
     matching rather than memory pressure.

  Deterministic throughout — the tape comes from a fixed LCG, never from
  `rand`, so two runs measure the same work."
  (:require [torihiki.book :as bk]))

(set! *unchecked-math* :warn-on-boxed)
;; Without this, a missing hint in the HARNESS makes `aget` reflective and the
;; measurement reports the reflection instead of the engine. That happened:
;; destructuring the tape in the parameter vector dropped the `^ints` hints and
;; this benchmark reported 9,941 ns/op for an engine a chunked probe showed to
;; be running at ~1,000. Reflection warnings are the difference between
;; measuring the subject and measuring the instrument.
(set! *warn-on-reflection* true)

(def ^:const n-levels 65536)
(def ^:const mid-level 32768)

(defn- lcg
  "A 48-bit linear congruential generator. Deterministic, cheap, and good
  enough to spread orders across the ladder — this is a load generator, not
  a source of randomness anyone relies on."
  ^long [^long seed]
  (bit-and (unchecked-add (unchecked-multiply seed 25214903917) 11) 281474976710655))

(defn gen-tape
  "Build the operation tape. Each op is four parallel array entries:
  kind (0 place / 1 cancel), side, level, qty."
  [^long n]
  (let [kind (int-array n) side (int-array n)
        level (int-array n) qty (int-array n)]
    (loop [i 0 s 12345]
      (when (< i n)
        (let [s1 (lcg s)
              s2 (lcg s1)
              s3 (lcg s2)
              r (rem (bit-shift-right s1 16) 100)
              spread (rem (bit-shift-right s2 16) 40)
              sd (int (rem (bit-shift-right s3 16) 2))]
          (cond
            ;; 43% cancel an order placed earlier
            (< r 43)
            (do (aset kind i 1) (aset side i sd) (aset level i 0) (aset qty i 0))
            ;; 12% aggressive: price through the opposing quotes so it crosses
            (< r 55)
            (do (aset kind i 0) (aset side i sd)
                (aset level i (int (if (zero? sd) (+ mid-level 30) (- mid-level 30))))
                (aset qty i (int (inc (rem (bit-shift-right s3 20) 12)))))
            ;; the rest: quote passively, a few ticks off the mid
            :else
            (do (aset kind i 0) (aset side i sd)
                (aset level i (int (if (zero? sd)
                                     (- mid-level 1 spread)
                                     (+ mid-level 1 spread))))
                (aset qty i (int (inc (rem (bit-shift-right s3 20) 20))))))
          (recur (inc i) s3))))
    [kind side level qty]))

(defn run-tape
  "Replay the tape against a fresh book. Returns [book ops placed cancelled].
  Cancels target a ring of recently placed ids, which is what makes slots get
  reused and the generation counter earn its keep.

  The tape arrives as four separate parameters rather than as one destructured
  vector: hints on destructured locals are easy to lose, and losing one here
  turns every `aget` into a reflective call that dominates the measurement."
  ;; `n` is deliberately NOT hinted `^long`: a fn with a primitive signature
  ;; is limited to four parameters, and the four array hints matter more.
  [n ^ints kind ^ints side ^ints level ^ints qty]
  (let [b (bk/new-book {:n-levels n-levels :cap 2097152 :ev-cap 1})
        ;; deep enough that a cancel usually finds a live order; a short ring
        ;; makes most cancels miss, which silently turns the mix back into a
        ;; book that only grows
        ring (long-array 1048576 -1)
        ring-mask 1048575]
    (loop [i 0 w 0 placed 0 cancelled 0]
      (if (>= i (long n))
        [b n placed cancelled]
        (if (zero? (aget kind i))
          (let [oid (bk/place! b (aget side i) (aget level i) (aget qty i) 0
                               (bit-and i 1023))]
            (if (pos? oid)
              (do (aset ring (bit-and w ring-mask) (long oid))
                  (recur (inc i) (inc w) (inc placed) cancelled))
              (recur (inc i) w placed cancelled)))
          (let [slot (bit-and (- w 1 (bit-and i 524287)) ring-mask)
                oid (aget ring slot)]
            (if (pos? oid)
              (let [q (bk/cancel! b oid)]
                (aset ring slot -1)
                (recur (inc i) w placed (if (pos? q) (inc cancelled) cancelled)))
              (recur (inc i) w placed cancelled))))))))

(defn- fmt [^double x]
  (format "%,.0f" x))

(defn -main [& args]
  (let [n (if (seq args) (Long/parseLong (first args)) 5000000)
        _ (println (str "generating a " (fmt (double n)) "-operation tape..."))
        [kind side level qty] (gen-tape n)]
    (println "warming up (JIT)...")
    (dotimes [_ 3] (run-tape (min n 1000000) kind side level qty))
    (System/gc)
    (println (str "measuring " (fmt (double n)) " operations..."))
    (let [t0 (System/nanoTime)
          [b ops placed cancelled] (run-tape n kind side level qty)
          t1 (System/nanoTime)
          secs (/ (double (- t1 t0)) 1e9)
          ops-per-sec (/ (double ops) secs)
          ns-per-op (/ (double (- t1 t0)) (double ops))]
      (println)
      (println "  operations      " (fmt (double ops)))
      (println "  placed          " (fmt (double placed)))
      (println "  cancelled       " (fmt (double cancelled)))
      (println "  resting at end  " (fmt (double (bk/resting-count b))))
      (println "  elapsed         " (format "%.3f s" secs))
      (println)
      (println "  THROUGHPUT      " (str (fmt ops-per-sec) " ops/sec"))
      (println "  latency         " (format "%.0f ns/op" ns-per-op))
      (println)
      (println "  reference: HyperCore is documented at ~200,000 orders/sec")
      (println (format "  ratio vs that reference: %.1fx" (/ ops-per-sec 200000.0))))))
