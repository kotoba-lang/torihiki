# maturity remeasure 2026-09-04 0551 (26th test-parity, 24th fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 22.5 / 25.1 / 21.9 (high; all measurements still ran).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-0551.out).
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0551.out) — **26th identical-count run** (JVM==nbb).
  (First bare `nbb script/tests-on-nbb.cljk` call failed with "Could not find
  namespace: torihiki.address-test" — classpath via script/nbb-classpath.cljk
  is mandatory, as recorded.)
- Seeded fuzz 24th digest verification: JVM
  `clojure -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0551.out) and nbb via standing driver
  `nbb --classpath "$(nbb script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
  (evidence/fuzz-nbb-0551.out) — JVM vs nbb identical after stripping the REPL
  echo lines (FUZZ_JVM_EQ_NBB via diff), AND matches the 0542 / 0520 / 0507
  baseline byte-for-byte (jvm-0551 vs jvm-0542 diff=0) — **24th digest run**.
- `clojure -M:bench`: **RED (EXIT=1)** — ArityException,
  `Wrong number of args (2) passed to: torihiki.book/cancel!` at bench.clj:112
  (evidence/bench-0551.err) — **17th reconfirmation** of OPEN
  bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
