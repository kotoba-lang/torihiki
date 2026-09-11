;; falsify-12 JVM driver — owns the try/catch (see falsify12-multi-deficit.cljc header).
(println (falsify12-multi-deficit/header))

(defn- fmt-snap [[h s]]
  (str " k" (:k s)
       " d4 " (:d4 s) " d6 " (:d6 s) " d8 " (:d8 s)
       " c4 " (:c4 s) " c6 " (:c6 s) " c8 " (:c8 s)
       " root " (:root s)))

(defn- fmt-probe [r]
  (str "probe " (:probe r)
       (reduce (fn [acc [h s]] (str acc (fmt-snap [h s]))) "" (:snaps r))))

(doseq [[label f] [["real-sweep-walk" #(falsify12-multi-deficit/probe-real-sweep-walk)]
                   ["deficit-crossing" #(falsify12-multi-deficit/probe-deficit-crossing)]
                   ["deficit-accum" #(falsify12-multi-deficit/probe-deficit-accum)]]]
  (println
   (try (fmt-probe (f))
     (catch Exception e
       (falsify12-multi-deficit/line-threw label e)))))

(println "done")
