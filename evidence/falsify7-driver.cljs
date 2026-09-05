;; falsify-7 nbb driver — pre-require + load-string (no load-file on nbb;
;; load-string does not resolve reader conditionals, so the shared .cljc
;; carries no try/catch — the driver's `catch :default` owns it).
(require '["fs" :as fs])
(require '[torihiki.state :as st])
(load-string (fs/readFileSync "evidence/falsify7-halt-paths.cljc" "utf8"))

(println (falsify7-halt-paths/header))

(doseq [seeded? [false true]]
  (println (try
             (let [r (falsify7-halt-paths/probe-withdraw seeded?)]
               (str "probe " (:probe r) " validate " (pr-str (:validate r))
                    " -> " (:outcome r) " " (:detail r)))
             (catch :default e
               (falsify7-halt-paths/line-threw (str "withdraw-2^53 seeded=" seeded?) nil e)))))

(doseq [flags [0 falsify7-halt-paths/flag-reduce-only]]
  (println (try
             (let [r (falsify7-halt-paths/probe-order-oob flags)]
               (str "probe " (:probe r) " validate " (pr-str (:validate r))
                    " -> " (:outcome r) " " (:detail r)))
             (catch :default e
               (falsify7-halt-paths/line-threw (str "order-qty-2^53 flags=" flags) nil e)))))

(println (try
           (let [p (falsify7-halt-paths/probe-indomain-selfhalt)
                 ex' (st/apply-block (:block2-ex p) {:height 3 :ts 1002 :txs [(:take-tx p)]})]
             (str "probe " (:probe p)
                  " validate-rest " (pr-str (:validate-rest p))
                  " validate-take " (pr-str (:validate-take p))
                  " block2 " (:block2 p) " " (:block2-detail p)
                  " -> applied " (str (st/state-root ex'))))
           (catch :default e
             (let [p (falsify7-halt-paths/probe-indomain-selfhalt)]
               (str "probe " (:probe p)
                    " validate-rest " (pr-str (:validate-rest p))
                    " validate-take " (pr-str (:validate-take p))
                    " block2 " (:block2 p) " " (:block2-detail p)
                    " -> threw"
                    " msg=" (ex-message e)
                    " where=" (pr-str (:where (ex-data e)))
                    " value=" (pr-str (:value (ex-data e))))))))

(println "done")
