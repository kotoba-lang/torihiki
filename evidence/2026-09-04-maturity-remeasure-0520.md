# maturity remeasure 2026-09-04 0520 (24th test-parity, 22nd fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 13.5 / 14.0 / 16.1.
- JVM `kbb -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-0520.out).
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0520.out) — **24th identical-count run** (JVM==nbb).
- Seeded fuzz 22nd digest verification: JVM
  `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0520.out) and nbb via standing driver
  `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
  (evidence/fuzz-nbb-0520.out) — JVM vs nbb identical after stripping the REPL
  echo lines (`#'fuzz-seeded/run` etc.; FUZZ_JVM_EQ_NBB via diff), AND matches
  the 0507 baseline byte-for-byte (jvm-0520 vs jvm-0507 diff=0) — **22nd
  digest run**.
- `kbb -M:bench`: **RED (EXIT=1)** — ArityException,
  `Wrong number of args (2) passed to: torihiki.book/cancel!` at bench.clj:112
  (evidence/bench-0520.err) — **15th reconfirmation** of
  OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
