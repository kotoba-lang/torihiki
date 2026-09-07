;; falsify-10 nbb driver — probe A only (the 1M honest-walk loop is the scale
;; falsify-9 judged unrealistic for nbb; the crossing verdict rests on probe A,
;; whose sequence probe B's JVM walk independently shows is reachable).
(require '["fs" :as fs])
(require '[torihiki.state :as st] '[torihiki.fixed :as fx])
(load-string (fs/readFileSync "evidence/falsify10-funding-accum.cljc" "utf8"))

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

(doseq [[label f] [["payment-identity" #(falsify10-funding-accum/probe-payment-identity)]
                   ["residue-seeded-crossing" #(falsify10-funding-accum/probe-residue-seeded-crossing)]]]
  (println
   (try (case label
          "payment-identity" (fmt-payment (f))
          "residue-seeded-crossing" (fmt-crossing (f)))
     (catch js/Error e
       (falsify10-funding-accum/line-threw label e)))))

(println "done")
