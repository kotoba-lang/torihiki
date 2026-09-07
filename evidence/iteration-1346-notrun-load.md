torihiki maturity bench iteration — 2026-09-04 1346 JST
=========================================================
status: NOT-RUN (host load gate)

Host load at 13:46: 63.64 / 57.69 / 56.01 (1/5/15 min) — exceeds the >20 gate
(11 days up, load still high from other work on the host).

Per iteration protocol, no tests and no bench were executed this cycle.
No code changes were made.

Latest verified baseline (unchanged, from status/maturity.md):
- Tests: 357 tests / 915 assertions, all green on both runtimes as of
  2026-09-04 1113 (test-1113.out JVM + test-nbb-1113.out nbb).
- Bench: still failing (bench-tape-cancel-arity, bench.clj:112
  `torihiki.book/cancel!` ArityException (2) — reconfirmed on run 25,
  evidence/bench-1120.err at 11:30 with n=1,000,000).
- Reproducibility 2 / tests+反証 3 / overall L1 unchanged.

Carry-over: next run should retry bench once load < 20 and confirm
whether bench.clj:112 remains unfixed before each re-measure.
