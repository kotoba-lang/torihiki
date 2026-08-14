;; The whole test suite, run under nbb.
;;
;;   npx nbb -cp "src:test:$(clojure -Spath)" script/tests-on-nbb.cljs
;;
;; ## Why this exists, given `loads-on-nbb`
;;
;; `loads-on-nbb` proves both runtimes can READ every namespace. That caught
;; three real failures and it is still worth running first, because it is a
;; second and it localises the damage. But it does not run a single line, and
;; the parity mandate is about ANSWERS, not about loading.
;;
;; The gap is not hypothetical. On the day this was written, the escrow's
;; deposit reader passed 328 JVM assertions and `loads-on-nbb` — and under nbb
;; every deposit test silently tested nothing, because the test helper built
;; its memos with `(int c)`. ClojureScript has no character type: a character
;; literal is a one-character string and `int` of a string is NaN, so every
;; byte of every memo was NaN, the ABI payload was garbage, and the assertions
;; that "passed" on the JVM were reading a corpus of nothing on the runtime the
;; validator actually runs on.
;;
;; Nothing short of executing the assertions on both sides finds that. The JVM
;; suite cannot: it was green. `loads-on-nbb` cannot: everything loaded.
;;
;; ## What it costs
;;
;; A few seconds, and the discipline that a test may only use what both
;; runtimes have. That discipline is the point — a test helper that works on
;; one runtime is a test that only exists on one runtime.
;;
;; Exits non-zero on any failure, so it can gate a deploy.
(ns tests-on-nbb
  (:require [clojure.test :as t]
            [torihiki.address-test]
            [torihiki.api-test]
            [torihiki.auth-test]
            [torihiki.book-test]
            [torihiki.clearing-test]
            [torihiki.commit-test]
            [torihiki.evm-interp-test]
            [torihiki.evm-test]
            [torihiki.funding-test]
            [torihiki.log-test]
            [torihiki.mark-test]
            [torihiki.oracle-test]
            [torihiki.snapshot-test]
            [torihiki.state-test]
            [torihiki.thorchain-test]
            [torihiki.trigger-test]))

;; `run-tests` reports asynchronously under nbb, so the exit code is set from
;; the summary rather than by reading a return value. A runner that always
;; exits zero is a gate that never fails.
(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (println (str "TESTS-ON-NBB: " (:test m) " tests, " (:pass m) " assertions, "
                (:fail m) " failures, " (:error m) " errors"))
  (when-not (t/successful? m)
    (set! (.-exitCode js/process) 1)))

(t/run-tests 'torihiki.address-test
             'torihiki.api-test
             'torihiki.auth-test
             'torihiki.book-test
             'torihiki.clearing-test
             'torihiki.commit-test
             'torihiki.evm-interp-test
             'torihiki.evm-test
             'torihiki.funding-test
             'torihiki.log-test
             'torihiki.mark-test
             'torihiki.oracle-test
             'torihiki.snapshot-test
             'torihiki.state-test
             'torihiki.thorchain-test
             'torihiki.trigger-test)
