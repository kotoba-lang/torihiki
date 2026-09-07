# maturity re-measure 2026-09-04 11:13–11:30 JST (remeasure-0839 +1)

## Gate check (host load)

- Job start 11:05: load 40.43 (1-min) > 20 → waited.
- 11:12:56: load 15.05 < 20 → re-measure started (first under-threshold window after 6 consecutive load skips 08:26–10:55).

## Measured

1. **JVM tests**: `clojure -M:test` → evidence/test-1113.out: **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.** exit=0.
2. **nbb tests**: `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs` → evidence/test-nbb-1113.out: **same count 357/915, 0 failures, 0 errors. TESTS-ON-NBB: pass.** exit=0. (32nd identical-count measurement.)
3. **Seeded fuzz JVM**: `clojure -M -e "(load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"` → evidence/fuzz-jvm-1114.out, 16 seeds, done. (Note: `(torihiki.fuzz-seeded/run)` fully-qualified form fails with ClassNotFoundException; bare `(fuzz-seeded/run)` after load-file is correct.)
4. **Seeded fuzz nbb**: `nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/fuzz-nbb-driver.cljs` → evidence/fuzz-nbb-1118.out, 16 seeds, done.
5. **Cross-runtime digest**: `diff` of JVM output (minus the `#'fuzz-seeded/run` REPL echo line) vs nbb output → **empty** — byte-identical digests, 29th byte-identical measurement. Also byte-identical to the 0841/0842 baseline and, per that baseline, to 0657/0608.
6. **Bench n=1,000,000**: `clojure -M:bench 1000000` → **RED exit=1**, `ArityException at torihiki.bench/run-tape (bench.clj:112) — Wrong number of args (2) passed to: torihiki.book/cancel!` (evidence/bench-1120.{out,err}). **25th confirmation** of bench-tape-cancel-arity; still fails at n ≥ 1M, so the 再現性 3 condition (n ≥ 1M × 3 stable low-load runs) remains unmet.

## Score impact

All 7-axis scores unchanged: spec 3 / impl 3 / test 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1. The re-measure re-grounds test (32nd identical count) and 反証/再現性 (29th byte-identical digest) but breaks no new ground: no new falsify was run (NEXT's fix prerequisites unchanged), bench still red on the known arity bug. No code changes (job spec).

## Notes

- Host load spiked back above 20 (44–61) during the JVM/nbb runs themselves; tests completed unaffected. The load-gate threshold of 20 is below what the machine sustains during a clojure JIT warm-up — the gate should probably be re-scoped (e.g. gate on 1-min at job start only, as this run did).
