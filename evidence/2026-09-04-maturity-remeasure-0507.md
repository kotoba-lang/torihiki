# maturity remeasure 2026-09-04 0507 (23rd test-parity, 21st fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 13.9 (after hours 16.8 / 15m 18.1).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-0507.out).
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0507.out) — **23rd identical-count run** (JVM==nbb).
- Seeded fuzz 21st digest verification: JVM
  `clojure -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0507.out) and nbb via standing driver
  `nbb --classpath "$(nbb script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
  (evidence/fuzz-nbb-0507.out) — JVM vs nbb byte-identical after stripping the
  single REPL echo line (FUZZ_JVM_EQ_NBB), AND matches the 0457 and 0408
  baselines (jvm-0507 vs jvm-0457 diff=0; jvm-0507 vs jvm-0408 diff=0) —
  **21st digest run**.
- `clojure -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape
  (bench.clj:112), `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0507.err) — **14th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
