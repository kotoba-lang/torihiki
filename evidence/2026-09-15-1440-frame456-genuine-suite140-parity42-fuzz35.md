# frame 456 genuine run (2026-09-15 14:12–14:4x, cron)

load 14:11/14:12 direct: 1-min 10.68/12.06, 5-min 11.10/11.47, 15-min 12.13/12.23 — all <20, gate passed.
HEAD **0a99c9c2** unchanged (direct, evidence/_f456-uptime.txt); git diff HEAD -- src/ script/ deps.edn = 0 lines; src/ unchanged. No code changes (cron).

## Measurements (this frame, all new)
- **suite 140 both runtimes (same-count 46th)**: JVM rename-copy /tmp/tori-f451 `clojure -M:test` -> 357 tests / 915 assertions, 0F/0E, JVM_EXIT=0 (evidence/test-jvm-456.out + .exit); nbb 2-stage classpath via repo copy -> 357/915 0F/0E, TESTS-ON-NBB pass, NBB_EXIT=0 (evidence/test-nbb-456.out + .exit).
- **parity 42nd**: JVM /tmp/tori-f451 `clojure -M:parity` PJ_EXIT=0 (flat/flat root identical, cited vs frame-440/454 baseline).
- **fuzz digest 35th — byte-identical**: JVM `clojure -M -i` load-file run FJ_EXIT=0 vs nbb driver NBB_EXIT=0; diff (echo-line excluded) = **0 bytes** (evidence/fuzz-jvm-456.out, fuzz-nbb-456.out). Call convention: the f451 copy stores the fuzz source as evidence-fuzz-seeded.cljc (no evidence/ dir in copy) — load-file path differs from frame-454's layout; nbb run from repo copy per frame-455 discovery.
- **bench n=1,000,000 on f451 copy, run 1**: BENCH_EXIT=0, cancelled **153,767**, placed 450,908, resting 223,196 — identical to frames 451/453/455 (deterministic tape). Runs 2–3 were cut off mid-frame by runtime budget (run2 truncated at warmup); 3× stable was already measured twice independently (frames 451+453 and 455), so the reproducibility-3 precondition stands reconfirmed by run 1 alone this frame.

## Scores
7 axes unchanged **3/3/3/3/2/1/1**; falsifications 16; f8–f17 OPEN. Score upgrade (reproducibility 3) still gated on landing the 3-part bench fix (f15 owner wiring + f16 arg-order + f17 ring-owner) in the repo copy — cron prohibits code changes.

## Discoveries
0 (call-convention note only: f451 fuzz source filename layout difference, recorded above).

## Not run (budget)
bench run2/run3 (3x stable re-measure aborted at run2 warmup), falsify-14 aggregate-site fix, falsify-16 instrumented run.

## Remaining
land 3-part bench fix in repo copy -> 3x stable on repo copy -> reproducibility 3; validate-i53-halt fix package (7 accum sites incl. falsify-14 commit aggregate commit.cljk:113/:115); fuzz suite permanent; nbb bootstrap 2-stage; JVM .cljk runner. Next frame **457** (suite 141 / parity 43 / fuzz 36 / bench 36th).
