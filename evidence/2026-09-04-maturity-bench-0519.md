# 2026-09-04 05:19 maturity measure (cron)

Host load at start: 15.41 (avg 15.41/16.98/17.88) < 20 → full run.

## Tests
- JVM `kbb -M:test`: **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.** (evidence/test-jvm-0519.out)
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`: **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.** namespaces 17/17, "TESTS-ON-NBB: pass" (evidence/test-nbb-0519.out)
- → 同一カウント JVM==nbb **24 度目**実測。

## Bench (`kbb -M:bench`)
exit=1. Known red: `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112). Wrong number of args (2) passed to: torihiki.book/cancel!` — bench-tape-cancel-arity **15 実行目で再確認** (evidence/bench-0519.out/.err). Throughput 計測不可 (cancel! owner 必須化に未追従)。再現性 3 の条件は n ≥ 1M で 3 回安定 (falsify-5 crash 下限 n=524289 由来)。

No code changes.
