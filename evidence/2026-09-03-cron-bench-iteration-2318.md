# torihiki cron bench iteration evidence — 2026-09-03 23:16–23:18 JST

Host load at start: 17.67 (1min) / 15.81 / 16.49 → below 20 threshold, so tests + bench both executed.
No code changes made.

## `kbb -M:test`
- exit 0
- **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
- Full output: evidence/test-cron-2316.out
- Consistent with 2026-09-03 23:09 remeasure (357 / 915, JVM==nbb).

## `kbb -M:bench`
- exit 1 (expected red, unchanged from evidence/bench-2315.err)
- Crash point: `ArityException` at `torihiki.bench/run-tape (bench.clj:112)` —
  "Wrong number of args (2) passed to: torihiki.book/cancel!"
- Confirms maturity.md finding: bench harness does not follow the cancel! owner-required
  signature; default 5M tape crashes before any throughput measurement. Red bench-tape-cancel-arity re-confirmed.
- Full output: evidence/bench-cron-2317.out

## Status deltas
- None. テスト 3 / 反証 3 / 再現性 2 hold. NEXT remains:
  fix bench/torihiki/bench.cljk:112 to current cancel! owner-required signature,
  then 3 stable low-load bench runs → 再現性 2→3.
