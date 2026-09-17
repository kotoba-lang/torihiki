# frame 477 genuine run (2026-09-17 05:0x, cron, no code changes)

- Load gate: 05:05 direct 13.4/12.9/13.1, 05:07 pre-bench 10.5/12.0/12.7 — all windows <20, gate passed (3rd consecutive gate-passed frame).
- HEAD 366321a (unchanged); measurement copy /tmp/tori-f451 (f15 owner-wired bench.cljc, script/ present per frame 476 fix).
- **bench 37th = first 3-run green series with cancelled ≠ 0** (n=1,000,000, JVM `clojure -M:bench 1000000` on the owner-wired copy):
  - run 1: EXIT=0, cancelled=153,767, 21,208 ops/s, elapsed 47.2s (/tmp/t477-bench.out)
  - run 2: EXIT=0, cancelled=153,767, 39,369 ops/s, elapsed 25.4s (/tmp/t477-bench-2.out)
  - run 3: EXIT=0, cancelled=153,767, 54,982 ops/s, elapsed 18.2s (/tmp/t477-bench-3.out)
  - cancelled byte-identical (153,767) across all 3; throughput variance (21k→55k) tracks host load (load 10.5→12.7), i.e. first two runs of the 33-red era pattern are gone: no ArityException, cancel branch exercised. Boxed-math warnings only in .err (benign).
- **reproducibility 3 NOT yet claimed**: repo bench/torihiki/bench.cljc:112 is still 2-arg `bk/cancel! b oid` (direct read this frame) — the owner-wired fix exists only in the measurement copy, cron code-change is forbidden, and the 3-stable-runs gate is defined against the harness. Remaining step: land the 1-line owner-ring fix at HEAD, rerun 3× n=1M, then claim 再現性 3.
- parity / suite / fuzz: not-run this frame (frame 476 measured suite 145 both-runtimes PASS + fuzz 40th byte-identical; parity still blocked on missing kotoba/ in the copy).
- Scores 7 axes unchanged: 3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN; NEXT unmeasured 0 (fix 未着手).
- Next runner (frame 478): parity with `kotoba/` copied in (or run in-repo), then — if HEAD lands the bench owner fix — the 3× n=1M series at HEAD for 再現性 3.
