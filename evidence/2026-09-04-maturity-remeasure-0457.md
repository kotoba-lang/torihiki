# maturity remeasure 2026-09-04 0457 (22nd test-parity, 20th fuzz digest)

- No code changes (HEAD unchanged; only untracked evidence/, status/).
- Host load at start: 18.8.
- JVM `kbb -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-0457.out).
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0457.out) — **22nd identical-count run** (JVM==nbb).
- Seeded fuzz 20th digest verification: JVM
  `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0457.out) and nbb via standing driver
  `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
  (evidence/fuzz-nbb-0457.out) — JVM vs nbb byte-identical after stripping the
  single REPL echo line, AND both match the 0408 baseline
  (jvm-0457 vs jvm-0408 diff=0; nbb-0457 vs nbb-0408 diff=0) — **20th digest run**.
- `kbb -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape
  (bench.clj:112), `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0457.err) — **13th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
