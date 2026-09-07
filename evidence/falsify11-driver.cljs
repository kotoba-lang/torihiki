;; falsify-11 nbb driver — probes A and B (the ~1M honest-walk loop is the
;; scale falsify-9/10 judged unrealistic for nbb; the crossing verdict rests
;; on probe B, whose sequence probe C's JVM walk independently shows is
;; reachable).
(require '["fs" :as fs])
(require '[torihiki.state :as st] '[torihiki.fixed :as fx])
(load-string (fs/readFileSync "evidence/falsify11-fees-accum.cljc" "utf8"))

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

(doseq [[label f] [["payment-identity" #(falsify11-fees-accum/probe-payment-identity)]
                   ["fees-seeded-crossing" #(falsify11-fees-accum/probe-fees-seeded-crossing)]]]
  (println
   (try (case label
          "payment-identity" (fmt-payment (f))
          "fees-seeded-crossing" (fmt-crossing (f)))
     (catch js/Error e
       (falsify11-fees-accum/line-threw label e)))))

(println "done")
