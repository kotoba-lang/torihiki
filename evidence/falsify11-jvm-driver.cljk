;; falsify-11 JVM driver — owns the try/catch (see falsify11-fees-accum.cljc header).
(println (falsify11-fees-accum/header))

(defn- fmt-snap [s]
  (str " cross" (:k s) " fees " (:fees s) " coll1 " (:coll1 s)
       " coll2 " (:coll2 s) " root " (:root s)))

(defn- fmt-payment [r]
  (str "probe " (:probe r)
       " notional " (:notional r) " rate " (:rate r)
       " product " (:product r)
       " prod-lt-2-63 " (:prod-lt-2-63 r)
       " notional-lt-2-53 " (:notional-lt-2-53 r)
       " boundary-dist " (:boundary-dist r)
       " fee " (:fee r) " fee-expected " (:fee-expected r)
       " fee-odd " (:fee-odd r)))

(defn- fmt-crossing [r]
  (str "probe " (:probe r)
       " seed-fees " (:seed-fees r) " seed-root " (:seed-root r)
       (reduce (fn [acc [k s]] (str acc (fmt-snap s))) ""
               (sort-by key (select-keys r [:c1 :c2 :c3 :c4])))))

(defn- fmt-walk [r]
  (str "probe " (:probe r)
       " crosses " (:crosses r)
       " fees " (:fees r)
       " max-abs-coll " (:max-abs-coll r)
       (reduce (fn [acc [k s]] (str acc (fmt-snap s))) "" (sort-by key (:marks r)))))

(doseq [[label f] [["payment-identity" #(falsify11-fees-accum/probe-payment-identity)]
                   ["fees-seeded-crossing" #(falsify11-fees-accum/probe-fees-seeded-crossing)]
                   ["honest-walk" #(falsify11-fees-accum/probe-honest-walk)]]]
  (println
   (try
     (case label
       "payment-identity" (fmt-payment (f))
       "fees-seeded-crossing" (fmt-crossing (f))
       "honest-walk" (fmt-walk (f)))
     (catch Exception e
       (if (= label "honest-walk")
         (str (falsify11-fees-accum/line-threw label e)
              " | stack: "
              (reduce (fn [acc s] (str acc " <- " s))
                      "" (take 12 (.getStackTrace e))))
         (falsify11-fees-accum/line-threw label e))))))

(println "done")
