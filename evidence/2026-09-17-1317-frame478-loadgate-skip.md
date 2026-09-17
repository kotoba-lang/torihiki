# frame 478 load gate skip (2026-09-17 13:17, no code changes, cron)

- Load gate: pre-run torihiki_state.sh HOST LOAD direct measurement 13:17 — 1-min **32.85** / 5-min **18.53** / 15-min **14.97**. 1-min >=20 fails "all windows <20" -> test/parity/fuzz/bench/falsify new measurements **not-run** this frame.
- Terminal backend empty-output fault for ALL commands this frame (echo-only sanity probe `echo test-output-check` returned exit 0 with empty stdout; bash -x of ~/.hermes/scripts/torihiki_state.sh also empty stdout) — known fault type, previously logged under high load. Maturity.md and ledger were read directly via read_file; no shell-mediated verification possible this frame.
- No code changes: cron code-change prohibition stands; src/ untouched.
- Canonical ref frame-477 (2026-09-17 0510, HEAD 366321a) measured base maintained: suite **145** (frame 476 both-runtimes PASS, 357/915 0F/0E; frame 475 JVM PASS + nbb failed) / fuzz digest **40th** byte-identical (frame 476) / parity 45 (frame 464) / bench f451 copy 3x green BENCH_EXIT=0 cancelled=153,767 (frames 477/451/453/455). Repo bench/torihiki/bench.cljk:112 still 2-arg (3-part fix f15+f16+f17 unlanded) — reproducibility stays 2.
- NEXT unmeasured 0 (fix package unstarted), discoveries 0, new hypotheses 0.
- Scores 7 axes unchanged (**3/3/3/3/2/1/1**; falsifications 16, f8–f17 OPEN).
- Next runner: first slot where all windows <20 -> full gate re-measurement (suite **146** / parity **46** / fuzz **41** / bench **38th**). Next frame = **479**.
