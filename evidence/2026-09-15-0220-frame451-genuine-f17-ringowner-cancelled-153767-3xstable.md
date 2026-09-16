# 2026-09-15 ~02:2x frame 451 — falsify-17 measured: bench ring-owner harness defect CONFIRMED — cancelled 172 → 153,767, 3× stable n=1M

## Load gate

02:10 uptime direct 1-min **10.28** / 5-min **12.09** / 15-min **12.96** — all <20, gate PASSED (via redirect + read_file workaround; terminal empty-output fault, known).

## falsify-17 (hypothesis registered + measured this frame, per frame-449/450 directive)

- **Hypothesis**: frame-449 residual (cancelled=172, far below ~1/1024 ceiling) is a HARNESS defect: the tape ring stores oids only; cancels present the cancel op's own owner `(bit-and i 1023)` while the order was placed at an earlier index i' with owner `(bit-and i' 1023)` — agreement only when i ≡ i' (mod 1024).
- **Method**: measurement copy /tmp/tori-f451 (cp -R of /tmp/tori-falsify15, zero repo changes): ring → ring + ring-owner arrays; place stores owner; cancel passes stored owner. 1-line-class diff on bench.cljc:98/108/114.
- **Result**: `clojure -M:bench 1000000` → **cancelled = 153,767** (frame-449: 172), placed 450,908, resting at end 223,196, BENCH_EXIT=0, 84,214 ops/sec, 11,874 ns/op (/tmp/torihiki-451-bench.out).
- **3× stability**: RUN 2 and RUN 3 → cancelled = **153,767 identical both runs** (deterministic tape), placed 450,908 identical, BENCH_EXIT=0, 85,332 / 85,114 ops/sec (/tmp/torihiki-451-bench-2runs.out, 2 runs 11.719s / 11.749s).

## Verdict

- **CONFIRMED, harness-side**: owner mismatch between the cancel op's owner and the placing op's owner explains the 172 residual. Engine cancel path is sound; with stored-owner cancels the steady-state mix reaches cancelled ≈ 153.8k per 1M ops (placed 450.9k, resting hovers at 223k).
- **Reproducibility-3 precondition (cancel actually runs at n ≥ 1M) now met: 3× stable cancelled > 0.** Reproducibility score upgrade to 3 remains gated on landing the fix (owner wiring + arg order + ring-owner storage) in the repo copy — no code changes in this frame per job rules.
- 2 boxed-math warnings at bench.cljc:106/116 in the harness (pre-existing aget reflective-warning shape, not engine).

## Status

- HEAD **0a99c9c2** direct-measured 02:10, git diff HEAD -- src/ script/ deps.edn = 0 lines; measurement copies under /tmp only.
- Canonical ref frame-441/449 base maintained (suite 138 / parity 40 / fuzz 33). suite/parity/fuzz/falsify-14 not-run (budget; terminal fault consumed part of the frame).
- Discoveries 1 (falsify-17), new hypotheses 0. Scores 7 axes unchanged (**3/3/3/3/2/1/1**; falsifications effectively 16 measured + f17; f8–f16 OPEN).
- Remaining: land bench fix (3 parts: owner wiring f15 + arg order f16 + ring-owner f17) → reproducibility 3; falsify-14; JVM .cljk runner; fuzz permanent; validate-i53-halt fix package; nbb bootstrap 2-stage.
- Next frame = **452** (new-measurement suite 139 / parity 41 / bench 35th; falsify-14 priority).
