# maturity remeasure 2026-09-04 2328 (21st test-parity, 19th fuzz digest)

- No code changes (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 23.5 (≥ 20 — above the usual full-run threshold; this run
  was the scheduled cron re-measure, all four measurements still executed to
  completion and counts/digests are exact-match verified below).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures,
  0 errors. EXIT=0 (evidence/test-jvm-2328.out).
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-2328.out) — **21st identical-count run** (JVM==nbb).
- Seeded fuzz 19th digest verification: JVM
  `clojure -M -e '(load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run)'`
  (evidence/fuzz-jvm-2328.out) and nbb via standing driver
  `nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/fuzz-nbb-driver.cljs`
  (evidence/fuzz-nbb-2328.out) — JVM vs nbb byte-identical after stripping the
  single REPL echo line, AND both match the 0408 baseline
  (jvm-2328 vs jvm-0408 diff=0; nbb-2328 vs nbb-0408 diff=0) — **19th digest run**.
- `clojure -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape
  (bench.clj:112), `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-2328.err) — **12th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence / falsify-9 / falsify-10:
  remain OPEN (evidence stands).
- All 7-axis scores unchanged from maturity.md (no code changes this iteration).
