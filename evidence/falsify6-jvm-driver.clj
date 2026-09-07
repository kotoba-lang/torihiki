;; falsify-6 JVM driver — owns the try/catch (see falsify6-i53.cljc header).
(println (falsify6-i53/header))
(doseq [a falsify6-i53/amounts]
  (println (try
             (falsify6-i53/line (falsify6-i53/probe (first a) (second a)))
             (catch Exception e (falsify6-i53/line-threw a e)))))
(println "done")
