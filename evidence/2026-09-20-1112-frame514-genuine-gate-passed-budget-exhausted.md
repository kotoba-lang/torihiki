# frame 514 (2026-09-20 11:1x, cron) — genuine-gate-passed budget-exhausted frame (no code changes)

## Load gate
- 11:10 uptime direct: 1-min **9.07** / 5-min **11.36** / 15-min **12.30** — all windows <20 → **gate passed** (single agreement; pre-run torihiki_state.sh stdout empty, known fault, uptime direct-measured via redirect→read_file workaround, /tmp/tori_state_f522_load.txt).
- However runtime budget exhausted before any measurement slot could start → suite/parity/fuzz/bench/falsify new measurements **not-run**.

## HEAD / code invariance (direct-measured 11:12)
- HEAD **1274d12a3d503ce26c387a5e44a691c0b8065de7** (branch bot/maint-202609172329; moved from frame-513's 6552005c — ledger/evidence commits only).
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** (empty file direct-measured, /tmp/tori_f522_diffstat.txt).
- `git diff 694e2ac7..HEAD --stat -- src/ script/ deps.edn` = **0 bytes** → code unchanged since the frame-503 measurement base; all frame-513 citations remain valid.
- `git status --porcelain` = **0 lines** (clean tree — maturity.md committed, /tmp/tori_f522_porc.txt).

## Measurement copy
- /tmp/tori-f506 **present** (script/ 5 files incl. kotoba-parity.cljc, nbb-classpath.cljc, tests-on-nbb.cljc, run-kotoba-wasm.mjs, loads-on-nbb.cljc; evidence/ duplicated) — reusable at next genuine frame, no rebuild needed (/tmp/tori_f522_copy.txt).

## Canonical ref
- **frame-513 (2026-09-20 09:0x, HEAD 6552005c) measured base maintained**: suite **155** both runtimes PASS (same-count 59th, 357/915 0F/0E) / parity **52** (FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3…) / fuzz digest **52** byte-identical / bench known-red (repo bench.cljk:112 2-arg, 3-part fix unlanded). Code-invariance (diff 0 bytes vs 694e2ac7) makes the citation valid at 1274d12a.

## Findings
- New discoveries 0, new hypotheses 0, falsifications 0.
- NEXT unmeasured list unchanged and entirely code-change-gated (cron prohibited): validate-i53-halt fix package (api i53/notional/balance-domain gates + accum sum gates 7 sites incl. commit.cljk:113/:115 merkle aggregate [falsify-14 measured & CONFIRMED frame 452] + fx/mul-rate pre-limit + REDUCING product pre-limit + rate cap + settle-deficit delta clamp) → 3-part bench fix landing (f15+f16+f17) → HEAD 3× n=1M → reproducibility 3 → fuzz suite permanent.

## Scores
- 7 axes unchanged: **3/3/3/3/2/1/1** (falsifications 16, f8–f17 OPEN; falsify-14 aggregation measured frame 452).

## Next frame = **515** (at next genuine measurement: suite 156 / parity 53 / fuzz 53 / bench 40th).
