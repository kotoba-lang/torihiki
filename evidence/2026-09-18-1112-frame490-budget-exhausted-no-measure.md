# frame 490 budget-exhausted skip (2026-09-18 11:12 JST, cron, no code changes)

- Load gate: pre-run script 11.04/13.95/15.52 at 11:12 — all windows <20, gate passed (no skip-check file needed).
- HEAD **366321a** (same as frames 478–489). Working tree = maturity.md (M) + evidence only. repo bench/torihiki/bench.cljk:112 still 2-arg — 3-part bench fix (f15+f16+f17) unlanded; cron code-change prohibited.
- terminal stdout capture still broken this session (every command returns empty output, exit 0) — worked around via redirect + read_file throughout (same fault as frames 489 / 08:10 note).
- Run-time budget exhausted before any new falsify hypothesis could be launched → no new measurement this frame. Canonical latest 実測 remains frame 489 (2026-09-18 09:15): suite 148 both-runtime pass (357 tests / 915 assertions, 51st consecutive same-count), fuzz digest 43rd byte-identical — evidence/2026-09-18-0915-frame489-genuine-suite148-fuzz43.md.
- Discoveries 0, new hypotheses 0 measured. Scores 7 axes unchanged (3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN).
- Next runner: repo 3-part bench fix landing (interactive slot) → HEAD 3× n=1M → 再現性 3; validate-i53-halt fix package remains highest leverage. Next frame = 491.
