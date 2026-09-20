# frame 478 — skip: terminal tool outage (not load gate)

- 2026-09-17, ~23:10 JST window (host load 12.15/12.56/12.60 — under the 20 gate, so this is NOT a 高負荷 skip)
- Cause: Hermes `terminal` tool returned `{"output": "", "exit_code": 0, "error": null}` for every command this frame, including `echo hello` and `true; echo marker-2`. `execute_code` is blocked in cron mode (approval gate). `search_files` also failed (rg missing error). No shell access ⇒ no kbb/clojure measurement, no bench, no suite run possible.
- Attempted commands (all silent): `bash ~/.hermes/scripts/torihiki_state.sh`, `echo hello`, `date`, `echo test123`, `pwd && ls` (workdir torihiki), `true; echo marker-$((1+1))` — all empty output.
- Canonical 実測 reference remains the latest measurement per ledger convention: frame 477 (2026-09-17 0510, bench 3-run green series, /tmp/tori-f451, cancelled=153,767 ×3, BENCH_EXIT=0) and frame 476 (suite145 both runtimes pass, 40th seeded-fuzz repro).
- No code changes. No maturity.md edits this frame (open: bench.cljk:112 owner-wired 3-part fix not landed in repo copy — still blocks 再現性 3 on HEAD).
