# frame 487 genuine run (2026-09-18 05:09–05:17 JST, cron, no code changes)

- Load gate: 05:10 direct 1-min 10.61 / 5-min 13.63 / 15-min 19.42 — all windows <20, gate passed (skip-check-0918-0510.txt). Pre-run 12.59/14.39/19.99. Frames 485/486 (0210/0505) were load-gate skips, so this is the first genuine frame after 484.
- HEAD **366321a** direct (same as frames 478–484). `git diff HEAD -- src/ script/ deps.edn` = 0 lines; working tree = maturity.md (M) + evidence only. repo bench/torihiki/bench.cljk:112 re-read at 05:10: still 2-arg `(bk/cancel! b oid)` — 3-part bench fix (f15+f16+f17) unlanded, cron code-change prohibited.
- terminal stdout capture broken (known fault) → all output via redirect + read_file.
- **suite 146 both runtimes PASS on /tmp/tori-f451**:
  - JVM `clojure -M:test` → 357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0 (evidence/f487-test-jvm.{out,err,exit}; err 0 bytes).
  - nbb 2-stage classpath (gitlibs text-src 73bdb13a…/src prepended to generated CP; KOTOBA_CHECKOUTS + NODE_PATH per frame-476 recipe) → 357/915, 0 failures, 0 errors, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0 (evidence/f487-test-nbb.{out,err,exit}). 1st attempt with polluted CP (cp file had appended probe lines) failed `Could not find namespace: kotoba.bytes.sha256` — operator error, not environmental; clean CPGEN file passes. f487-test-nbb.exit line 1 is from that discarded attempt.
- **fuzz digest 41st byte-identical**: JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run))'` vs nbb `kbb --backend sci --classpath "$CP" evidence/fuzz-nbb-driver.cljk` → both EXIT=0, 2715 bytes each, `diff` empty (evidence/f487-fuzz-{jvm,nbb}.{out,err,exit}); also byte-identical to frame-476 baseline (f476-fuzz-{jvm,nbb}.out, both diffs empty).
- bench / parity / falsify: not-run (bench needs the 3-part fix landed at HEAD; parity 47 measured frame 484; canonical bench ref = frame-477 3-run green series on the f451 owner-wired copy).
- Discoveries 0, new hypotheses 0. Scores 7 axes unchanged (3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN).
- Next runner: repo 3-part bench fix landing (interactive slot, cron prohibited) → HEAD 3× n=1M → 再現性 3; validate-i53-halt fix package remains highest leverage. Next frame = 488.
