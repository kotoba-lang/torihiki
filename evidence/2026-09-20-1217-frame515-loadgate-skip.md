# frame 515 loadgate-skip (2026-09-20 12:17, cron, no code changes)

- Load gate **FAILED**: 12:15 pre-run 48.03/53.85/49.52; 12:17 direct 30.40/44.66/46.50 — all windows >20 → **not-run** (suite/parity/fuzz/bench/falsify all skipped per the >20 rule).
- HEAD 6552005c7cc96b02b62c5613974b3ccf0de5e5d6 (12:17 direct; == frame-513 base, NOT 1274d12a cited in frame 514 — that commit is absent from this checkout's HEAD, no code deltas to cite).
- Working tree: status/maturity.md (M) + untracked evidence backlog only (unchanged pattern).
- Canonical ref unchanged: **frame-513 (2026-09-20 09:0x)** suite 155 both runtimes / parity 52 / fuzz digest 52 byte-identical / bench known-red.
- Findings 0, scores 7 axes unchanged 3/3/3/3/2/1/1.
- Next frame = **516** (at next genuine measurement: suite 156 / parity 53 / fuzz 53 / bench 40th; requires load <20 at both pre-run and direct check).
