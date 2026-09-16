# 2026-09-15 0830 frame 453 genuine — bench 3x stable n=1M (reproducibility-3 precondition re-measured on f451 copy)

## Load gate
08:25 uptime direct: 1-min **13.78** / 5-min **14.17** / 15-min **15.85** — all <20 → genuine frame 453.

## Code invariance
HEAD **0a99c9c2** (unchanged from frames 447–452). `git diff HEAD -- src/ script/ deps.edn` = 0 lines. Measurement copy only: /tmp/tori-f451 (falsify-17 ring-owner copy, bench.cljc:112 = `(bk/cancel! b oid (aget ring-owner slot))`, ring stores owner at placement line 108). No repo copy changes (cron: no code changes).

## Measurement: bench n=1,000,000 × 3 runs on /tmp/tori-f451 (JVM `clojure -M:bench 1000000`)

| run | placed | cancelled | resting | BENCH_EXIT | elapsed |
|---|---|---|---|---|---|
| 1 | 450,908 | **153,767** | 223,196 | 0 | 29.686 s (33,685 ops/s) |
| 2 | 450,908 | **153,767** | 223,196 | 0 | 19.524 s (51,218 ops/s) |
| 3 | 450,908 | **153,767** | 223,196 | 0 | 15.270 s (65,488 ops/s) |

Evidence: evidence/bench-f453-run1.out / bench-f453-run2.out / bench-f453-run3.out (copies of /tmp/f453-bench-{1,2,3}.out).

**cancelled = 153,767 identical in all 3 runs, BENCH_EXIT=0 × 3** — matches frame-451 measurement (same copy, same tape, deterministic). Reproducibility-3 precondition (**cancel actually runs at n≥1M, 3× stable, BENCH_EXIT=0**) is now measured **twice independently** (frame 451 + this frame 453) on the falsify-17 measurement copy.

## Verdict
- Reproducibility axis stays **2** in this frame: upgrade to 3 is gated on landing the 3-part bench fix (owner wiring f15 + arg-order f16 + ring-owner f17) in the **repo copy** of bench.cljk — this frame only re-confirms the precondition on the /tmp measurement copy (cron: no code changes permitted).
- Engine cancel path: sound. Harness defect fully characterized (f15/f16/f17).
- falsify-14 already CONFIRMED (frame 452). Suite 139 both runtimes already measured (frame 452). No new hypotheses.

## Scores
7 axes unchanged: **3/3/3/3/2/1/1** (falsifications 16, discoveries 9+; f8–f17 OPEN).

## NEXT
Land bench fix (3 parts) in repo copy → 3× stable re-measure on repo copy → reproducibility **3**. Then: falsify-14 aggregate-site fix scoping (commit.cljk:113–115 tree internal-node sum gate), JVM .cljk runner permanent, fuzz suite permanent, validate-i53-halt fix package (now 7 accum sites incl. merkle aggregate), nbb bootstrap 2-stage.

Next frame = **454**.
