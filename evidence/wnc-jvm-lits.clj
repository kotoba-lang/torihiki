;; ground truth: exact decimal expansions of doubles, JVM BigDecimal(double) ctor route
(doseq [[label v] [["5.5" 5.5]
                   ["-2.5" -2.5]
                   ["0.1" 0.1]
                   ["0.1+0.2" (+ 0.1 0.2)]
                   ["1e-300" 1e-300]
                   ["-0.0" -0.0]
                   ["1e17" 1e17]
                   ["-2.5e18" -2.5e18]
                   ["1.5e300" 1.5e300]
                   ["2.5" 2.5]
                   ["1e16" 1e16]]]
  (println (str "EXPAN " label " => "
                (if (and (Double/isFinite ^double v) (not (== v (Math/floor ^double v))))
                  (.toPlainString (BigDecimal. (double v)))
                  (str "<not-fractional> " (str (.toBigIntegerExact (BigDecimal. (double v)))))))))
(println (str "STR 1e17 => " (str 1e17)))
(println (str "STR ##Inf => " (str ##Inf)))
(println (str "PARSE Infinity => " (Double/parseDouble "Infinity")))
(println (str "PARSE -Infinity => " (Double/parseDouble "-Infinity")))
(println (str "BN 9007199254740993 = 2^53+1 => " (= (bigint "9007199254740993") 9007199254740993N)))
(println (str "INT? 1e17 => " (integer? 1e17)))
(println (str "INT? 5.5 => " (integer? 5.5)))
