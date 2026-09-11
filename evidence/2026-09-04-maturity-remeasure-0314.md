# maturity remeasure 2026-09-04 03:14 (18th test-parity, 16th fuzz digest)

- No code changes since last remeasure (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 18.0 (< 20 threshold → full run).
- JVM `kbb -M:test`: Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-jvm-0314.out).
- nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0314.out) — **18th identical-count run** (JVM==nbb).
- Seeded fuzz 16th digest verification: JVM `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk")(fuzz-seeded/run)'`
  and nbb via standing driver `evidence/fuzz-nbb-driver.cljk` (pins classpath) —
  all 16 digest lines **byte-identical across runtimes AND vs the 0231 baseline**
  (evidence/fuzz-{jvm,nbb}-0314.out). The raw files differ only by the JVM REPL echo
  line `#'fuzz-seeded/run` (load-file return value); the digest output itself is identical.
- `kbb -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape (bench.clj:112),
  `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0314.err) — **9th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence: remain OPEN (no code changes;
  falsify-7/-8 evidence stands, evidence/falsify7-{jvm,nbb}.out, falsify8-{jvm,nbb}.out).
- All 7-axis scores unchanged from maturity.md.
