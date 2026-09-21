# frame 518 (2026-09-20 18:2x JST, cron) — genuine-gate-passed budget-exhausted frame (no code changes)

## Load gate
- pre-run torihiki_state.sh block (18:14): 1-min 7.34 / 5-min 8.27 / 15-min 10.02 (all <20)
- direct 18:17: 1-min **24.43** / 5-min 13.13 / 15-min 11.62 (1-min spike ≥20)
- direct 18:19: 1-min **12.91** / 5-min **13.87** / 15-min **12.26** (all <20)
- direct 18:20: 1-min **13.57** / 5-min **13.93** / 15-min **12.32** (all <20)
- Two consecutive full-window measurements <20 (18:19 + 18:20) → **gate passed** (declining after 18:17 transient spike; /tmp/tori_f518_load.txt, /tmp/tori_frame_probe.txt).

## Outcome
- Runtime budget exhausted before any measurement slot could start → suite/parity/fuzz/bench/falsify new measurements **not-run**.

## HEAD / code invariance (direct-measured 18:19–18:20)
- HEAD **6552005c7cc96b02b62c5613974b3ccf0de5e5d6** (git rev-parse HEAD direct, /tmp/tori_f518_head.txt) = frame-512 ledger commit; same as frame-513 canonical base.
- `git diff 694e2ac7..HEAD -- src/ script/ deps.edn | wc -c` = **0 bytes** (code unchanged since the frame-503 measurement base; /tmp/tori_f518_srcdiff.txt) → all frame-513 citations remain valid.
- working tree: status/maturity.md (M) + ~230 untracked evidence rows (frames 467–517 records uncommitted) — no src/ script/ changes (/tmp/tori_frame_probe.txt).

## Measurement copy
- /tmp/tori-f513 present (instrumented copy: 3-part bench fix + cancel reason counters per /tmp/tori-f513-build.py); /tmp/tori-f506 / /tmp/tori-f499 / /tmp/tori-f495 also present (/tmp/tori_f518_toridir.txt). Reusable at next genuine frame, no rebuild needed.

## Canonical ref maintained
- **frame-513 (2026-09-20 09:0x, HEAD 6552005c) measured base**: suite **155** both runtimes PASS (same-count 59th, 357/915 0F/0E, evidence/f513-test-*) / parity **52** (FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3…, evidence/f513-parity-*) / fuzz digest **52** byte-identical (evidence/f513-fuzz-*) / bench known-red (repo bench.cljk:112 2-arg ArityException, 3-part fix unlanded, evidence/f513-bench-*). Code-invariance (diff 0 bytes) keeps the citation valid.

## Findings
- New discoveries 0, new hypotheses 0, falsifications 0.
- NEXT unmeasured list unchanged and entirely code-change-gated (cron prohibited): validate-i53-halt fix package (api i53/notional/balance-domain gates + accum sum gates 7 sites incl. commit.cljk:113/:115 + fx/mul-rate pre-limit + REDUCING product pre-limit + rate cap + settle-deficit delta clamp) → 3-part bench fix landing (f15+f16+f17) → HEAD 3× n=1M → reproducibility 3 → fuzz suite permanent.

## Scores
- 7 axes unchanged: **3/3/3/3/2/1/1** (falsifications 18, f8–f18 OPEN; falsify-14 aggregation measured frame 452; falsify-18 cancel-miss decomposition confirmed frame 513).

## Next frame = **519** (at next genuine measurement: suite 156 / parity 53 / fuzz 53 / bench 40th).
