# frame 464 genuine run (2026-09-16 09:0x, cron, no code changes)

- Load gate: 09:06 uptime direct 1-min 15.31 / 5-min 12.41 / 15-min 11.89 — all <20, gate passed. (frame-463 was gate-passed but budget-exhausted; this frame = 464.)
- HEAD 0a99c9c2 direct-measured; git diff HEAD -- src/ script/ deps.edn = 0 lines; untracked = maturity.md (M) + evidence only; /tmp/tori-f451 copy present.
- suite 144 both runtimes PASS (same-count 50th): JVM /tmp/tori-f451 `clojure -M:test` 357/915 0F/0E EXIT=0 (/tmp/t464-test-jvm.out); nbb 2-stage classpath via repo copy 357/915 0F/0E 17/17 TESTS-ON-NBB pass EXIT=0 (/tmp/t464-test-nbb.out).
- parity 45th: JVM `clojure -M:parity` PJ_EXIT=0, FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3… / PROOF a 10 verifies true — baseline identical (frame-440/454/456/458/460/461).
- fuzz digest 38th byte-identical: JVM load-file run FJ_EXIT=0 vs nbb driver FN_EXIT=0, diff = 1 echo line only (`#'fuzz-seeded/run`, baseline shape). Operator error noted: first nbb fuzz attempt ran without exported CP (FN_EXIT=1 torihiki.state unresolved) — rerun in one invocation with `CP=$(...) &&` succeeded; classpath must be generated and consumed in the same shell.
- bench n=1M on f451 copy: BENCH_EXIT=0, cancelled 153,767 / placed 450,908 / resting 223,196 — identical to frames 451/453/455/456/460/461 (deterministic tape). Reproducibility stays 2 (repo-copy landing pending).
- Scores 7 axes unchanged: 3/3/3/3/2/1/1; falsifications 16; f8–f17 OPEN. NEXT unmeasured 0.
