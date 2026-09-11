torihiki maturity bench iteration — 2026-09-04 13:16 JST
Result: NOT-RUN (host load gate)

load averages: 62.39 65.34 64.34 (threshold > 20)
uptime: up 11 days, 5:04, 10 users

Per job policy: load > 20 → not-run evidence only. `kbb -M:test` and
`kbb -M:bench` were both skipped to avoid compounding load on the host.

No code changes made.

Baseline reference: last full runs remain 2026-09-04 1113
(test-1113.out JVM + test-nbb-1113.out nbb): 357 tests / 915 assertions,
both runtimes identical — 32nd consecutive matched count.
Known red: bench-tape-cancel-arity (bench.clj:112 cancel! 2-arg ArityException,
25 runs confirmed, most recent evidence/bench-1120.err 11:30 n=1,000,000).
