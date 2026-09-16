# frame 455 genuine run (2026-09-15 13:17)

load 13:16/13:20 direct: 1-min 12.73/12.65, 5-min 12.47/12.98, 15-min 12.03/12.35 — all <20, gate passed.
HEAD 0a99c9c2 unchanged; git diff HEAD -- src/ script/ deps.edn = 0 lines; src/ unchanged.

## Measurements (this frame, both new)
- JVM suite (rename-copy /tmp/tori-f451, `clojure -M:test`): **357 tests / 915 assertions, 0F/0E, EXIT=0** — evidence/test-jvm-455.out. Suite 139, same-count 45th.
- nbb suite (repo copy, 2-stage classpath: `kbb --backend sci --classpath "$($CP from script/nbb-classpath.cljk with '../text/src')" script/tests-on-nbb.cljk`): **357/915 0F/0E, TESTS-ON-NBB pass, EXIT=0** — evidence/test-nbb-455.out.
- bench n=1,000,000 on f451 copy ×3: **BENCH_EXIT=0 ×3, cancelled 153,767 identical 3/3**, placed 450,908, resting 223,196, ~85–88k ops/sec — evidence/bench-f455-run{1,2,3}.out. Matches frame-451/453 exactly (deterministic tape). Reproducibility-3 precondition (cancel runs at n≥1M, 3× stable, exit 0) now measured **twice independently** on the measurement copy. Score stays 2: upgrade gated on landing the 3-part bench fix (f15 owner wiring + f16 arg-order + f17 ring-owner) in the repo copy.

## Discovery (copy-layout only)
/tmp/tori-f451 has **no script/** dir → nbb-classpath.cljk 2-stage bootstrap ENOENT when run from the copy; run bootstrap from the repo copy (done this frame). Measurement-copy defect, engine unaffected.

## Not run (budget)
parity (41 cited), fuzz digest (34 cited), falsify-14 aggregate-site fix, falsify-16 instrumented.

## Scores
7 axes unchanged **3/3/3/3/2/1/1**; falsifications 16; f8–f17 OPEN.

## Remaining
land 3-part bench fix → 3× stable on repo copy → reproducibility 3; validate-i53-halt fix package (7 accum sites incl. falsify-14 commit aggregate commit.cljk:115/:113); fuzz permanent; nbb bootstrap 2-stage; JVM .cljk runner. Next frame 456.
