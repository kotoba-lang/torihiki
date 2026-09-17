# frame 478 genuine run (2026-09-17 06:14–06:45 JST, cron, no code changes)

- Load gate: 06:14 pre-run 12.82/14.27/13.87 — all windows <20, gate passed.
- HEAD 366321a; measurement copy /tmp/tori-f451 (unchanged from frame 476, tests as .cljc).
- **JVM suite 145 PASS**: `clojure -M:test` on the measurement copy → 357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0 (evidence/f478-test-jvm.out) — 33rd+ same-count pass, reproduces frame 476.
- **In-repo `clojure -M:test` finding**: at repo HEAD the cognitect test-runner sees only `.cljk` files in test/ (default matcher is `.clj[s|c]?$`) and reports "Ran 0 tests containing 0 assertions" after loading ns `user`. All genuine suite measurements therefore run on the measurement copy whose tests are .cljc. Recorded so future runners don't mistake in-repo 0-test output for a broken deps resolution.
- **bench (BENCH_EXIT=0)**: `clojure -M:bench` on the copy → n≥1M low-load, cancelled=1,386,589, elapsed 121.244 s, THROUGHPUT 41,239 ops/sec (24,249 ns/op), 0.2x vs HyperCore ~200k reference (evidence/f478-bench-jvm.out). cancelled≠0 confirmed again; but this is the f451 owner-wired copy, not repo HEAD — the bench.cljc:112 3-part fix (f15+f16+f17) is still unlanded at 366321a, so 再現性 stays 2.
- Scores 7 axes unchanged: 3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN; no code changes (cron 禁止).
- nbb leg not re-run this frame (no script/env change since frame 476 same-copy pass).
