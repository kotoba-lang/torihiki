;; falsify-8 JVM driver — owns the try/catch (see falsify8-halt-paths.cljc header).
(println (falsify8-halt-paths/header))

(doseq [p [(falsify8-halt-paths/probe-deposit-steps)
           (falsify8-halt-paths/probe-validate-2nd)
           (falsify8-halt-paths/probe-post-deficit-deposit)]]
  (println (str "probe " (:probe p)
                (when (contains? p :validate) (str " validate " (pr-str (:validate p))))
                (when (contains? p :coll1) (str " coll1 " (:coll1 p) " coll2 " (:coll2 p) " coll3 " (:coll3 p)))
                (when (contains? p :coll4) (str " coll3 " (:coll3 p) " coll4 " (:coll4 p) " root4 " (:root4 p)))
                (when (contains? p :root3) (str " root3 " (:root3 p)))
                (when (contains? p :outcome) (str " -> " (:outcome p "applied") " " (:detail p))))))

(doseq [[label f] [["post-2dep order rest qty=1" falsify8-halt-paths/probe-post-order]
                   ["post-2dep cross fill" falsify8-halt-paths/probe-post-cross]
                   ["post-2dep withdraw 1" falsify8-halt-paths/probe-post-withdraw]]]
  (println (try
             (let [r (f)]
               (str "probe " (:probe r)
                    " validate " (pr-str (:validate r))
                    " -> " (:outcome r "applied") " " (:detail r)))
             (catch Exception e
               (falsify8-halt-paths/line-threw label e)))))

(println "done")
