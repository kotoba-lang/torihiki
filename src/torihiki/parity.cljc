(ns torihiki.parity
  "A single scenario, executed on both runtimes, printing one digest.

  The claim this exists to check is not 'the ClojureScript build compiles' but
  'the two runtimes agree on the state root, byte for byte'. Only the second
  one means a browser can verify a block a JVM validator produced.

  It was not idle. A JVM-side optimization — reading the Book record's fields
  with direct interop instead of keyword lookup — silently broke the entire
  ClojureScript path: `(.-lvl_head b)` does not fail there, it returns
  `undefined`, and the failure surfaces much later as an array read on
  nothing. The JVM suite could not observe it. Run this after touching
  anything in `torihiki.slab` or `torihiki.book`.

    clojure -M:parity
    nbb --classpath \"src:<path-to>/bytes/src\" -e \"(require '[torihiki.parity :as p]) (p/report)\"

  The two must print the same root."
  (:require [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.state :as st]))

(def market (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))

(defn fresh []
  (st/new-exchange {:market market :book-opts {:n-levels 1024 :cap 4096 :ev-cap 4096}}))

(def scenario
  "Deposits, two-sided quoting around 500, and enough crossing to exercise
  matching, partial fills, and the bit-ladder cascade."
  {:height 1 :ts 1000
   :txs (into [{:tx :deposit :account 10 :amount 1000000}
               {:tx :deposit :account 11 :amount 1000000}]
              (for [i (range 200)]
                {:tx :order :account (+ 10 (mod i 2)) :market 1
                 :side (mod i 2)
                 :level (if (zero? (mod i 2)) (- 500 (mod i 9)) (+ 500 (mod i 9)))
                 :qty (inc (mod i 5))}))})

(defn report []
  (let [b (bk/new-book {:n-levels 1024 :cap 256 :ev-cap 256})]
    (bk/place! b bk/ask 100 5 0 11)
    (bk/place! b bk/ask 100 5 0 22)
    (bk/place! b bk/bid 100 7 0 33)
    (println "  best-ask   " (bk/best b bk/ask))
    (println "  fills      " (pr-str (mapv (juxt :maker-owner :qty) (bk/fills b)))))
  (let [ex (st/apply-block (fresh) scenario)]
    (println "  resting    " (bk/resting-count (get-in ex [:books 1])))
    (println "  STATE ROOT " (st/state-root ex))
    (st/state-root ex)))

#?(:clj (defn -main [& _] (report)))
