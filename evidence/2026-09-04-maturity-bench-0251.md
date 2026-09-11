# maturity bench iteration 2026-09-04 02:51 (18th test-parity)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 15.40 1-min / 16.59 5-min / 17.69 15-min (< 20 threshold → full run allowed).
- JVM `kbb -M:test`: **Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0**
  (evidence/test-jvm-0251.out).
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  **Ran 357 tests containing 915 assertions. 0 failures, 0 errors. namespaces 17/17. EXIT=0**
  (evidence/test-nbb-0251.out) — **18th identical-count run** (JVM==nbb).
- `kbb -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape
  (bench.clj:112), `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0251.err) — **9th reconfirmation** of OPEN bench-tape-cancel-arity.
  No throughput numbers produced (harness aborts before measurement).
- Out of scope this iteration (not run): seeded fuzz digest verification, falsify-8
  (hypothesis already registered in 2026-09-04-falsify-8-cumulative-deposit-overflow-notrun.md).
- All 7-axis scores unchanged from status/maturity.md (this file records evidence only;
  maturity.md untouched per no-code-change scope).
