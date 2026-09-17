# frame 476 loadgate skip (2026-09-17 05:11, cron, no code changes)

- Pre-run script loadavg: 30.97/17.81/14.84 (1-min > 20 at gate time).
- Direct measurement 05:11:19: `sysctl vm.loadavg` = **27.00 / 19.07 / 15.51** — 1-min window > 20, gate FAILED.
- Per convention (most recent 実測 canonical): this frame is skip-only. No suite/parity/fuzz/bench attempts were made; no code changes; maturity.md untouched this frame.
- Pending work carried to frame 477 (unchanged from frame 475 plan): first re-run nbb suite with captured .out/.err + `wc -c` on the $(...) classpath byte count (frame-475 attempt failed, cause undetermined between stray-output classpath contamination and genuine regression); on PASS record suite 145 same-count (51st), then parity 46 / fuzz 39 / bench 37th on the same gate.
- Note: this session's terminal tool returned empty stdout for every command (side effects verified working via file probe); all checks this frame were done through side-effect files.
