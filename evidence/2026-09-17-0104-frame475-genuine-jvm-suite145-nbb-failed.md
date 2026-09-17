# frame 475 genuine run (2026-09-17 01:0x, cron, no code changes)

- Load gate: 01:04 pre-run 12.86/15.12/16.62, 01:05 direct 11.06/14.33/16.25 — all windows <20, gate passed (first gate-passed frame since 464).
- HEAD 366321a direct-measured 01:05; working tree = maturity.md (M) + frame 467–474 evidence only; /tmp/tori-f451 measurement copy present (bench/torihiki/bench.cljc f15 owner-wired confirmed by direct read).
- suite 145 JVM PASS: /tmp/tori-f451 `clojure -M:test` → **357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0** (/tmp/t475-test-jvm.out; tail copied below).
- suite 145 nbb **FAILED attempt (not measured)**: `CP=$(kbb --backend sci script/nbb-classpath.cljk) && kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` → NBB_EXIT=1, /tmp/t475-test-nbb.out empty tail; .err file not captured (budget exhausted). Known prior failure mode to retry next frame: stray output contaminating $() classpath (frame-445 operator error) vs genuine regression — rerun with `wc -c` on CP and both streams captured. **Same-count claim for frame 475 is NOT established; canonical suite ref remains frame-464 (144, both runtimes PASS).**
- parity / fuzz / bench: not-run (budget exhausted mid-frame).
- Scores 7 axes unchanged: 3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN. NEXT unmeasured 0 (fix 未着手, cron code-change 禁止).
- Next runner (frame 476): first re-run nbb suite with captured .out/.err + CP byte count; on PASS record suite 145 same-count (51st), then parity 46 / fuzz 39 / bench 37th on the same gate.

JVM suite tail: `Ran 357 tests containing 915 assertions. / 0 failures, 0 errors.`
