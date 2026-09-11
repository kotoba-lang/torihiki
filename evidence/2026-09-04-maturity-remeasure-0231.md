# maturity remeasure 2026-09-04 02:31 (17th test-parity, 15th fuzz digest)

- No code changes since last remeasure (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 17.5 (< 20 threshold → full run).
- JVM `kbb -M:test`: Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-jvm-0231.out).
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0231.out) — **17th identical-count run** (JVM==nbb).
- Seeded fuzz 15th digest verification: JVM `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk")(fuzz-seeded/run)'`
  and nbb via standing driver `evidence/fuzz-nbb-driver.cljk` (pins classpath) —
  **byte-identical across runtimes AND vs the 0128 baseline**
  (evidence/fuzz-{jvm,nbb}-0231.out). Note: the invocation comment inside fuzz-seeded.cljc
  still says plain `kbb --backend sci -e '(load-file ...)'` — nbb has no load-file; the driver is required.
  Also, JVM load-file alone only returns the var without invoking `(run)` — the explicit
  `(fuzz-seeded/run)` call is required on both runtimes.
- `kbb -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape (bench.clj:112),
  `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0231.err) — **8th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt: remains OPEN (no code changes; falsify-7 evidence stands,
  evidence/falsify7-{jvm,nbb}.out, 2026-09-04-falsify-7-indomain-notional-halt.md).
- All 7-axis scores unchanged from maturity.md.
