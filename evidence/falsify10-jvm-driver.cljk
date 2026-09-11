;; falsify-10 JVM driver — owns the try/catch (see falsify10-funding-accum.cljc header).
(println (falsify10-funding-accum/header))

(defn- fmt-snap [s]
  (str " settle" (:k s) " residue " (:residue s) " coll " (:coll s)
       " rate " (:rate s) " root " (:root s)))

(defn- fmt-payment [r]
  (str "probe " (:probe r)
       " notional " (:notional r) " product " (:product r)
       " prod-lt-2-63 " (:prod-lt-2-63 r)
       " boundary-dist " (:boundary-dist r)
       " p " (:p r) " p-expected " (:p-expected r)))

(defn- fmt-crossing [r]
  (str "probe " (:probe r)
       (fmt-snap (:s1 r)) " |" (fmt-snap (:s2 r)) " |"
       (fmt-snap (:s3 r)) " |" (fmt-snap (:s4 r))))

(defn- fmt-walk [r]
  (str "probe " (:probe r)
       " settled " (:settled r)
       " max-abs-coll " (:max-abs-coll r)
       (reduce (fn [acc [k s]] (str acc (fmt-snap s))) "" (sort-by key (:marks r)))))

(doseq [[label f] [["payment-identity" #(falsify10-funding-accum/probe-payment-identity)]
                   ["residue-seeded-crossing" #(falsify10-funding-accum/probe-residue-seeded-crossing)]
                   ["honest-walk" #(falsify10-funding-accum/probe-honest-walk 1000801)]]]
  (println
   (try
     (case label
       "payment-identity" (fmt-payment (f))
       "residue-seeded-crossing" (fmt-crossing (f))
       "honest-walk" (fmt-walk (f)))
     (catch Exception e
       (falsify10-funding-accum/line-threw label e)))))

(println "done")
