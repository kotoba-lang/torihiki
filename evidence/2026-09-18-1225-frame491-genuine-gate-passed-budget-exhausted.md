# frame 491 genuine-start → budget-exhausted (2026-09-18 12:1x–12:2x JST, cron, no code changes)

- Load gate: 12:14 script snapshot 8.64/8.98/9.51; direct 12:15 8.40/8.90/9.45; re-check 12:19 11.24/9.48/9.52 — all windows <20, gate passed.
- HEAD 366321a (unchanged since frames 478–489); working tree = maturity.md (M) + evidence only. repo bench/torihiki/bench.cljk:112 re-read 12:16: still 2-arg `(bk/cancel! b oid)` — 3-part bench fix (f15+f16+f17) unlanded at HEAD.
- Measurement copy /tmp/tori-f451 verified intact this frame: evidence/ holds fuzz-seeded.cljk + fuzz-nbb-driver.cljk (correct layout), bench/torihiki/bench.cljc owner-wired (+ .f15orig/.f16orig backups), kotoba/ present, script/ present; src|test vs repo differ only by the known .cljk→.cljc rename (frame-483 convention).
- Runner staged at /tmp/f491_runner.sh (JVM suite → nbb 2-stage classpath w/ gitlibs text-src 73bdb13a… prefix + KOTOBA_CHECKOUTS + NODE_PATH → fuzz both runtimes + diff → bench n=1M; f491-* evidence names). **Execution did not start: run-time budget exhausted while recovering the nbb recipe from frame-476 evidence (no new discovery permitted after the budget notice).** No JVM_EXIT/NBB_EXIT/fuzz/bench numbers were measured this frame — none are claimed.
- terminal stdout capture still broken this session (every command returns empty output, exit 0); all state read via redirect + read_file.
- ripgrep absent this session (search_files content mode blocked) — recipe recovery done via read_file on evidence/2026-09-17-0210-frame476 + ls of ~/.gitlibs pins (73bdb13a… present).
- Scores 7 axes unchanged: 3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN; suite count reference remains frame 489 (148th, 357/915 both runtimes); fuzz byte-identical reference remains 43rd (frame 489).
- Next runner (frame 492): execute /tmp/f491_runner.sh as-is under the load gate (it self-documents the recipe), then the standing NEXT: 3-part bench fix landing at HEAD → 3× n=1M → 再現性 3.
