(ns torihiki.probe
  "Isolate where the time actually goes, one operation kind at a time."
  (:require [torihiki.book :as bk]))

(defn- ms [t0 t1] (/ (double (- t1 t0)) 1e6))

(defn probe-alloc []
  (let [t0 (System/nanoTime)
        b (bk/new-book {:n-levels 65536 :cap 2097152 :ev-cap 1})
        t1 (System/nanoTime)]
    (println (format "  new-book(cap=2M)      %8.1f ms" (ms t0 t1)))
    b))

(defn probe-place [n cap]
  (let [b (bk/new-book {:n-levels 65536 :cap cap :ev-cap 1})
        t0 (System/nanoTime)]
    (dotimes [i n]
      (bk/place! b (bit-and i 1) (+ 30000 (rem (* i 7) 500)) 5 0 1))
    (let [t1 (System/nanoTime)]
      (println (format "  place x%-9d      %8.1f ms   %7.0f ns/op  resting=%d"
                       n (ms t0 t1) (/ (double (- t1 t0)) n) (bk/resting-count b))))
    b))

(defn probe-place-then-cancel [n]
  (let [b (bk/new-book {:n-levels 65536 :cap (* 2 n) :ev-cap 1})
        oids (long-array n)
        t0 (System/nanoTime)]
    (dotimes [i n]
      (aset oids i (long (bk/place! b (bit-and i 1) (+ 30000 (rem (* i 7) 500)) 5 0 1))))
    (let [t1 (System/nanoTime)]
      (dotimes [i n] (bk/cancel! b (aget oids i)))
      (let [t2 (System/nanoTime)]
        (println (format "  place x%-9d      %8.1f ms   %7.0f ns/op" n (ms t0 t1) (/ (double (- t1 t0)) n)))
        (println (format "  cancel x%-8d      %8.1f ms   %7.0f ns/op" n (ms t1 t2) (/ (double (- t1 t2)) n)))))))

(defn probe-cross [n]
  "Every taker order crosses a resting maker: pure matching cost."
  (let [b (bk/new-book {:n-levels 65536 :cap (* 2 n) :ev-cap 1})]
    ;; seed one maker per taker
    (dotimes [i n] (bk/place! b bk/ask 30000 5 0 1))
    (let [t0 (System/nanoTime)]
      (dotimes [_ n] (bk/place! b bk/bid 30000 5 0 2))
      (let [t1 (System/nanoTime)]
        (println (format "  cross x%-9d      %8.1f ms   %7.0f ns/op"
                         n (ms t0 t1) (/ (double (- t1 t0)) n)))))))

(defn probe-best [n]
  (let [b (bk/new-book {:n-levels 65536 :cap 1024 :ev-cap 1})]
    (bk/place! b bk/ask 40000 5 0 1)
    (bk/place! b bk/bid 30000 5 0 1)
    (let [t0 (System/nanoTime)
          acc (loop [i 0 a 0]
                (if (< i n) (recur (inc i) (+ a (bk/best b bk/ask))) a))
          t1 (System/nanoTime)]
      (println (format "  best x%-10d      %8.1f ms   %7.0f ns/op  (acc %d)"
                       n (ms t0 t1) (/ (double (- t1 t0)) n) acc)))))

(defn -main [& _]
  (println "\n=== warmup ===")
  (dotimes [_ 2] (probe-place 200000 500000) (probe-cross 100000) (probe-best 1000000))
  (println "\n=== measured ===")
  (probe-alloc)
  (probe-best 5000000)
  (probe-place 1000000 2000000)
  (probe-place-then-cancel 500000)
  (probe-cross 500000))
