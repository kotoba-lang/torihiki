# Frame 575 — load gate PASSED, budget exhausted → NOT-RUN (2026-10-03 09:0x–09:1x JST)

- HEAD: `1ceb357` (unchanged since 2026-09-25 kotoba-migrate merge; frame-548 base remains non-citable at this HEAD).
- Load series (all <20 gate = every window <20):
  - 09:05 pre-run: 25.14 / 27.58 / 27.62 → FAIL
  - 09:05 re-check: 23.51 / 27.15 / 27.47 → FAIL
  - 09:05:41: 16.26 / 24.93 / 26.65 → FAIL (1-min under, 5/15-min over)
  - 09:07:02: 12.10 / 21.39 / 25.12 → FAIL
  - 09:13:47: **12.74 / 14.02 / 19.86 → PASS (first all-three sub-20 window since 2026-09-30; frames 549/561/562/563/574 had all failed)**
- Gate passed but cron run-time budget nearly exhausted → full 1ceb357 re-baseline (suite/parity/fuzz/bench, kotoba.test + native book invocation conventions) NOT RUN this frame (precedent: frame 564 gate-passed-budget-exhausted, frame 570 0540 gate-passed-budget-exhausted).
- IN-FLIGHT re-confirmed this frame (git diff --stat): `M src/torihiki/fixed.cljk` (+36 = **fx/sum + fx/mul** defs, i53-domain-checked accumulator/product primitives — the falsify-8..14 sum-gate fix core, per frame 569 note), `M src/torihiki/slab.cljk` (±1 doc-only: CLAUDE.md→AGENTS.md rename), `M status/maturity.md`. Unchanged since frame 562 → landing/revert still unconfirmed; genuine re-baseline remains blocked behind this decision.
- Untracked evidence notes accumulated since 09-25 (34 files) remain unregistered; retro frame-number register recommended (per frame 569 precedent).
- No score changes; maturity.md untouched this run. Scores stand at **3/3/3/3/2/1/1** on the frame-548 (92b9fc9) base, which is not citable at 1ceb357 — scores are effectively pending re-anchor.
- Reproducibility note: the sub-20 window pattern observed here (1-min lead dropping fast, 15-min trailing) matches the frame-397/401 genuine-lite entries; a follow-up frame with the load already <20 at pre-run can start the re-baseline immediately without waiting for the next drop.

NEXT (unchanged, ① still top and now time-critical since a passing window exists): ① **resolve IN-FLIGHT fixed.cljk/slab.cljk (land the fx/sum+fx/mul defs as part of the ② fix package, or revert) then start the 1ceb357 full re-baseline at the next load <20 pre-run** (suite same-count, parity, seeded fuzz, bench; re-establish JVM `clojure -M:test` / nbb 2-stage classpath conventions post kotoba.test migration + native book) → ② validate-i53-halt fix package (i53 caps + notional cap + balance-domain gate + accumulation sum gates at :deficit/:funding-residue/:fees-collected/:insurance-fund, now with fx/sum/fx/mul in-tree as the primitive) → bench 3-part fix → HEAD 3× n=1M → 再現性 3 → fuzz 常設化 → テスト/反証 4.

Next frame = 576.
