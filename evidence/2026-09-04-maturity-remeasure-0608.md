# maturity remeasure 2026-09-04 0608 (27th test-parity, 25th fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 23.3 / 23.4 / 22.3 (high; all measurements still ran).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-0608.out).
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0608.out) — **27th identical-count run** (JVM==nbb).
- Seeded fuzz 25th digest verification: JVM
  `clojure -M -e '(load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-0608.out) and nbb via standing driver
  `nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/fuzz-nbb-driver.cljs`
  (evidence/fuzz-nbb-0608.out) — JVM vs nbb identical after stripping the REPL
  echo lines (diff=0), AND matches the 0551 / 0542 / 0520 / 0507 baseline
  byte-for-byte (jvm-0608 vs jvm-0551 diff=0) — **25th digest run**.
- `clojure -M:bench`: **RED (EXIT=1)** — ArityException,
  `Wrong number of args (2) passed to: torihiki.book/cancel!` at bench.clj:112
  (evidence/bench-0608.err) — **18th reconfirmation** of OPEN
  bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
