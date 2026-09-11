# maturity remeasure 2026-09-04 0408 (20th test-parity, 18th fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 11.07 (< 20 threshold → full run).
- JVM `kbb -M:test`: Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-jvm-0408.out).
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0408.out) — **20th identical-count run** (JVM==nbb).
- Seeded fuzz 18th digest verification: JVM
  `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0408.out) and nbb via standing driver
  `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
  (evidence/fuzz-nbb-0408.out) — all 18 digest lines byte-identical across runtimes
  (whole-file diff shows only the JVM REPL echo line `#'fuzz-seeded/run`) AND vs the
  0231 baseline (nbb-0408 vs nbb-0231 diff=0; jvm-0408 vs jvm-0349 diff=0).
- `kbb -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape
  (bench.clj:112), `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0408.err) — **11th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9: remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
