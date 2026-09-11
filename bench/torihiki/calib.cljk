(ns torihiki.calib
  (:require [torihiki.book :as bk] [torihiki.slab :as slab]))

(defn- ns-per [t0 t1 n] (/ (double (- t1 t0)) n))

(defn baseline-loop [n]
  (let [t0 (System/nanoTime)
        a (loop [i 0 a 0] (if (< i n) (recur (inc i) (+ a i)) a))
        t1 (System/nanoTime)]
    (println (format "  empty loop        %7.2f ns/iter (%d)" (ns-per t0 t1 n) a))))

(defn one-aget [n]
  (let [arr (slab/alloc 4096)
        _ (slab/set! arr 7 3)
        t0 (System/nanoTime)
        a (loop [i 0 a 0] (if (< i n) (recur (inc i) (+ a (slab/get arr 7))) a))
        t1 (System/nanoTime)]
    (println (format "  one slab/get      %7.2f ns/iter (%d)" (ns-per t0 t1 n) a))))

(defn call-best [n]
  (let [b (bk/new-book {:n-levels 65536 :cap 1024 :ev-cap 1})]
    (bk/place! b bk/ask 40000 5 0 1)
    (let [t0 (System/nanoTime)
          a (loop [i 0 a 0] (if (< i n) (recur (inc i) (+ a (bk/best b bk/ask))) a))
          t1 (System/nanoTime)]
      (println (format "  bk/best           %7.2f ns/iter (%d)" (ns-per t0 t1 n) a)))))

(defn -main [& _]
  (dotimes [_ 3] (baseline-loop 20000000) (one-aget 20000000) (call-best 20000000))
  (println "=== measured ===")
  (baseline-loop 50000000)
  (one-aget 50000000)
  (call-best 50000000))
