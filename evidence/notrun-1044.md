# bench iteration NOT-RUN — host load over threshold (2026-09-04 10:44 JST)

- Scheduled maturity bench iteration for torihiki.
- Trigger condition: `clojure -M:test` (+ optional `clojure -M:bench`) with results recorded under evidence/.
- **Not run.** Host load at job time: `load averages: 29.87 31.63 28.00` (1-min 29.87 > 20 threshold), `uptime` 11 days. Measurement recorded via `date` + `uptime` + `sysctl vm.loadavg` at 2026-09-04 10:44:54 JST.
- Per job spec: host load > 20 → not-run evidence only. No tests, no bench, no code changes.

## Status against maturity.md (unchanged)

- Latest valid green remains 2026-09-04 0839/0840 remeasure (357 tests / 915 assertions, both runtimes, 31st identical count) — this iteration neither confirms nor refutes it.
- Open reds unchanged: validate-i53-halt, cumulative-deposit-divergence, and the NEXT fix list (i53/notional/balance-domain gates, accum sum gates incl. falsify-11 fees-collected, mul-rate pre-check, REDUCING `(* entry-notional closed)` bound, rate cap), plus bench-tape-cancel-arity (bench.clj:112) blocking 再現性 3.
- Host load has been elevated across recent iterations (multiple notrun-*.md files from 09:24–10:39 today); the bench harness fix (cancel! 3-arg arity) remains the blocker for the n ≥ 1M × 3 stable runs required for 再現性 3.

Next iteration: re-check load; if < 20, run `clojure -M:test` and `clojure -M:bench` normally.
