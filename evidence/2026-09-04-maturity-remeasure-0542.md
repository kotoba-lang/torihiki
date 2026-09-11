# maturity remeasure 2026-09-04 0542 (25th test-parity, 23rd fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 20.4 / 18.4 / 17.9 (high; all measurements still ran).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-0542.out).
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0542.out) — **25th identical-count run** (JVM==nbb).
- Seeded fuzz 23rd digest verification: JVM
  `clojure -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0542.out) and nbb via standing driver
  `nbb --classpath "$(nbb script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
  (evidence/fuzz-nbb-0542.out) — JVM vs nbb identical after stripping the REPL
  echo lines (`#'fuzz-seeded/run` etc.; FUZZ_JVM_EQ_NBB via diff), AND matches
  the 0520 / 0507 baseline byte-for-byte (jvm-0542 vs jvm-0520 diff=0) — **23rd
  digest run**.
- `clojure -M:bench`: **RED (EXIT=1)** — ArityException,
  `Wrong number of args (2) passed to: torihiki.book/cancel!` at bench.clj:112
  (evidence/bench-0542.err) — **16th reconfirmation** of
  OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
