(ns torihiki.curve
  "Is the per-operation cost constant, or does it grow with book size?"
  (:require [torihiki.book :as bk] [torihiki.bench :as bench]))

(defn -main [& _]
  (let [n 3000000
        [kind side level qty :as tape] (bench/gen-tape n)
        b (bk/new-book {:n-levels 65536 :cap 2097152 :ev-cap 1})
        ring (long-array 4096 -1)
        chunk 250000]
    (loop [i 0 w 0 t-chunk (System/nanoTime)]
      (when (< i n)
        (let [w' (if (zero? (aget ^ints kind i))
                   (let [oid (bk/place! b (aget ^ints side i) (aget ^ints level i)
                                        (aget ^ints qty i) 0 (bit-and i 1023))]
                     (if (pos? oid)
                       (do (aset ring (bit-and w 4095) (long oid)) (inc w))
                       w))
                   (let [s (bit-and (- w 1 (bit-and i 2047)) 4095)
                         oid (aget ring s)]
                     (when (pos? oid) (bk/cancel! b oid) (aset ring s -1))
                     w))]
          (if (zero? (rem (inc i) chunk))
            (let [t (System/nanoTime)]
              (println (format "  ops %8d   resting %8d   %7.0f ns/op"
                               (inc i) (bk/resting-count b)
                               (/ (double (- t t-chunk)) chunk)))
              (recur (inc i) w' t))
            (recur (inc i) w' t-chunk)))))))
