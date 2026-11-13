# Frame 563 — load gate PASS, no measurement (run budget exhausted)

- Time: 2026-10-08 13:17 JST
- HEAD check: not performed beyond script (script showed IN-FLIGHT unchanged: `M fixed.cljk`, `M slab.cljk`, `M maturity.md`)
- Load at script run 13:05: 24.39/23.03/24.47 → gate FAIL, waited
- Load at 13:10: 6.86/13.96/20.02 → 15-min avg 20.02, still ≥20, gate FAIL, waited
- Load at 13:17:45: **15.55/14.06/17.40 — all <20, gate PASS**
- First gate-passing window since 2026-09-30 (frames 549–562 all skipped at load 105–114 / 20–25).
- Outcome: cron run budget exhausted immediately after gate opened; no suite/parity/fuzz/bench re-measure executed this frame. Frame 563 is therefore NOT a genuine measurement frame.
- MANDATE for next run (frame 564): execute the 1ceb357 re-baseline in full on this opened window — suite + parity + fuzz + bench, re-establishing post-kotoba-migrate JVM/nbb invocation conventions; resolve IN-FLIGHT fixed.cljk +36/slab.cljk landing-or-revert FIRST (unchanged through frame 563).
