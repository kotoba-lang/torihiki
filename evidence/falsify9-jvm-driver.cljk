;; falsify-9 JVM driver — owns the try/catch (see falsify9-accum-paths.cljc header).
(println (falsify9-accum-paths/header))

(defn- fmt-round [s]
  (str " settle" (:k s) " deficit " (:deficit s) " coll " (:coll s) " root " (:root s)))

(defn- fmt [r]
  (str "probe " (:probe r)
       (when (contains? r :s1)
         (str (fmt-round (:s1 r)) " |" (fmt-round (:s2 r)) " |" (fmt-round (:s3 r)) " |" (fmt-round (:s4 r))))
       (when (contains? r :deficit3)
         (str " deficit3 " (:deficit3 r) " deficit-after " (:deficit-after r)
              " coll-after " (:coll-after r) " root " (:root r)))
       (when (contains? r :value)
         (str " value " (:value r)))))

(doseq [[label f] [["deficit-walk" falsify9-accum-paths/probe-deficit-walk]
                   ["deficit-3x-deposit" falsify9-accum-paths/probe-deficit-validate-path]
                   ["mul-rate-limit" falsify9-accum-paths/probe-mul-rate-limit]]]
  (println
   (try (fmt (f))
     (catch Exception e
       (falsify9-accum-paths/line-threw label e)))))

(println "done")
