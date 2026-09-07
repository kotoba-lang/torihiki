# maturity remeasure 2026-09-04 03:49 (19th test-parity, 17th fuzz digest)

- No code changes since last remeasure (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- Host load at start: 8.49 (< 20 threshold → full run).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-jvm-0349.out).
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors. EXIT=0
  (evidence/test-nbb-0350.out) — **19th identical-count run** (JVM==nbb).
- Seeded fuzz 17th digest verification: JVM (load-file evidence/fuzz-seeded.cljc) and nbb via
  standing driver evidence/fuzz-nbb-driver.cljs (pins classpath) — all 16 digest lines
  **byte-identical across runtimes AND vs the 0231 baseline**
  (evidence/fuzz-{jvm,nbb}-0349.out; whole-file diff vs nbb shows only the JVM REPL echo line).
- `clojure -M:bench`: **RED (EXIT=1)** — ArityException at torihiki.bench/run-tape (bench.clj:112),
  `Wrong number of args (2) passed to: torihiki.book/cancel!`
  (evidence/bench-0349.err) — **10th reconfirmation** of OPEN bench-tape-cancel-arity.
- validate-i53-halt / cumulative-deposit-divergence: remain OPEN (falsify-7/-8 evidence stands).
- **falsify-9 registered into maturity.md OPEN 赤 this iteration**
  (evidence/2026-09-04-falsify-9-deficit-accum-overflow.md, both-runtime measurement of the
  :deficit accumulator sum overflow + fx/mul-rate pre-check product overflow).
- All 7-axis scores unchanged from maturity.md.
