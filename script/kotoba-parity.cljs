;; Does the Kotoba build of `torihiki.fixed` agree with the `.cljc` it was
;; migrated from?
;;
;;   AMU_HOME=<path-to-kotoba-lang/amu> \
;;   KOTOBA_CHECKOUTS=<dir-of-sibling-checkouts> \
;;     nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/kotoba-parity.cljs
;;
;; Compiles JVM-free, runs the Wasm through amu's own shipped host, evaluates
;; the same table against the `.cljc`, and compares. Exit 0 agreement, 1 drift,
;; **2 could-not-measure** -- a missing amu, a failed compile and a clean run
;; must not share an exit code.
;;
;; ## Why the oracle stays
;;
;; Superproject Q9 (`kotoba-lang/kotoba-lang` `lang/q9-migration.edn`) is
;; explicit: `:oracle-retained-until-soak true`,
;; `:old-source-deletion-forbidden-before-soak true`, and `:cutover` is a
;; consumer selecting the new source rather than a rename. So
;; `src/torihiki/fixed.cljc` is not going anywhere, and this is what makes
;; keeping it worth something.
;;
;; ## The one intended difference
;;
;; `check` threw; `checked` returns `[:result :i64 :i64]`. Kotoba forbids
;; untracked control effects permanently, so this is not a translation
;; artefact and the comparison has to state the mapping rather than hide it:
;; a throw is the error arm. `kotoba/torihiki/fixed_parity.kotoba` opens the
;; result handles, which a host holding an opaque pair cannot do -- without it
;; three of the fourteen exports would be compared against nothing.
(ns kotoba-parity
  (:require [clojure.string :as str]
            [torihiki.fixed :as fx]
            [nbb.core :refer [*file*]]
            ["fs" :as fs]
            ["path" :as path]
            ["child_process" :as cp]))

(def repo (path/resolve (path/join (path/dirname *file*) "..")))
(def amu (or (aget js/process.env "AMU_HOME")
             (some-> (aget js/process.env "KOTOBA_CHECKOUTS") (path/join "amu"))))

(defn- die [code & msg] (js/console.error (str/join " " msg)) (js/process.exit code))

(when-not (and amu (fs/existsSync (path/join amu "bin" "amu")))
  (die 2 "cannot measure: no amu at" (str amu)
       "-- set AMU_HOME or KOTOBA_CHECKOUTS. This is exit 2 and not a pass."))

(defn- run! [cmd args]
  (let [r (cp/spawnSync cmd (clj->js args) #js{:encoding "utf8"})]
    {:status (.-status r) :out (str (.-stdout r)) :err (str (.-stderr r))}))

(defn- compile! [src out]
  (let [r (run! "node" [(path/join amu "bin" "amu") "compile" src
                        "--jvm-free" "--target" "wasm32"
                        "--source-path" (path/join repo "kotoba")
                        "--output" out])]
    (when-not (zero? (:status r))
      (die 2 "cannot measure: amu could not compile" src "\n" (:out r) (:err r)))
    out))

(defn- wasm-results [wasm cases-file]
  (let [runner (path/join repo "script" "run-kotoba-wasm.mjs")
        r (run! "node" [runner wasm
                        (str "file://" (path/join amu "runtime" "browser-host.mjs"))
                        cases-file])]
    (when-not (zero? (:status r))
      (die 2 "cannot measure: the Wasm would not run\n" (:err r)))
    (js->clj (js/JSON.parse (:out r)))))

;; ── the oracle, and the mapping the migration made explicit ─────────────────

(defn- attempt [f] (try {:ok (f)} (catch :default _ {:err true})))

(defn- oracle [[f & a]]
  (case f
    "in-domain?" (str (boolean (fx/in-domain? (first a))))
    "i53-max" (str fx/i53-max)
    "i53-min" (str fx/i53-min)
    "rate-scale" (str fx/rate-scale)
    "bps" (str (fx/bps (first a)))
    "pct" (str (fx/pct (first a)))
    "fdiv" (str (fx/fdiv (first a) (second a)))
    "fmod" (str (fx/fmod (first a) (second a)))
    "abs*" (str (fx/abs* (first a)))
    "clamp" (str (fx/clamp (first a) (second a) (nth a 2)))
    "div-round-half-up" (str (fx/div-round-half-up (first a) (second a)))
    ("checked-is-ok" "checked-ok" "mul-rate-is-ok" "mul-rate-ok"
     "notional-is-ok" "notional-ok")
    (let [r (attempt #(case f
                        ("checked-is-ok" "checked-ok") (fx/check :parity (first a))
                        ("mul-rate-is-ok" "mul-rate-ok") (fx/mul-rate (first a) (second a))
                        (fx/notional (first a) (second a))))]
      (if (str/ends-with? f "-is-ok")
        (if (:err r) "0" "1")
        ;; The Kotoba side's fallback on the error arm is 0. Matching it here
        ;; keeps a disagreement visible instead of letting the two agree by
        ;; accident on a value neither computed.
        (if (:err r) "0" (str (:ok r)))))
    (die 2 "cannot measure: no oracle for" f)))

(defn- compare! [label wasm cases-file]
  (let [cases (js->clj (js/JSON.parse (str (fs/readFileSync cases-file "utf8"))))
        got (wasm-results wasm cases-file)
        drift (keep (fn [[c g]]
                      (let [want (oracle c)]
                        (when-not (= want (nth g 2))
                          {:call c :kotoba (nth g 2) :cljc want})))
                    (map vector cases got))]
    (when (empty? cases) (die 2 "cannot measure: the case table is empty"))
    (doseq [d drift] (js/console.error (str "DRIFT " (pr-str d))))
    (println (str label ": " (count cases) " cases, " (count drift) " drift"))
    (count drift)))

(let [a (compile! (path/join repo "kotoba" "torihiki" "fixed.kotoba")
                  (path/join repo ".nbb-deps" "fixed.wasm"))
      b (compile! (path/join repo "kotoba" "torihiki" "fixed_parity.kotoba")
                  (path/join repo ".nbb-deps" "fixed-parity.wasm"))
      n (+ (compare! "fixed" a (path/join repo "kotoba" "parity-cases.json"))
           (compare! "fixed/result" b (path/join repo "kotoba" "parity-cases-result.json")))]
  (if (zero? n)
    (println "KOTOBA-PARITY: pass — the compiled Wasm agrees with the .cljc oracle")
    (js/process.exit 1)))
