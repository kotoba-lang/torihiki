# frame 489 genuine run (2026-09-18 09:0x–09:1x JST, cron, no code changes)

- Load gate: pre-run (script) 21.84/16.22/14.83 at 09:06 — 1-min window ≥20; re-checked 09:08 (13.48/15.05/14.55) and 09:09 (11.79/14.19/14.26) — all windows <20 at run start, gate passed. Note 1-min spike at first check decayed before JVM leg.
- HEAD **366321a** direct (git rev-parse; same as frames 478–488). Working tree = maturity.md (M) + evidence only. repo bench/torihiki/bench.cljk:112 re-read 09:09: still 2-arg `(bk/cancel! b oid)` — 3-part bench fix (f15+f16+f17) unlanded, cron code-change prohibited.
- Measurement copy /tmp/tori-f451: full-tree `diff -rq` src/ and test/ vs repo = **cljk→cljc extension rename only, zero content diffs**; deps.edn + script/ byte-identical. (repo .cljk → copy .cljc is the known frame-483 root-cause convention.)
- terminal stdout capture still broken this session (every command returns empty output, exit 0) → all output via redirect + read_file (same fault as the 08:10 skip note; that run skipped measurement — this run worked around it fully).
- **suite 148 both runtimes PASS on /tmp/tori-f451** (same-count **51st** consecutive measurement):
  - JVM `clojure -M:test` → 357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0, err 0 bytes (evidence/f489-test-jvm.{out,err,exit}).
  - nbb 2-stage classpath (gitlibs text-src 73bdb13a… prepended to CPGEN output; CPGEN_EXIT=0 first attempt; KOTOBA_CHECKOUTS + NODE_PATH per frame-476 recipe) → 357/915, 0 failures, 0 errors, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0, err 0 bytes (evidence/f489-test-nbb.{out,err,exit}).
- **fuzz digest 43rd byte-identical**: JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run))'` vs nbb driver on the same copy → both EXIT=0, 2715 bytes each, `diff` empty (evidence/f489-fuzz-{jvm,nbb}.{out,err,exit}, evidence/f489-fuzz-diff.txt). 1st JVM fuzz attempt used copy-root path `evidence-fuzz-seeded.cljk` → FileNotFoundException exit 1 (the copy keeps the file at evidence/fuzz-seeded.cljk, same layout as repo); retried with correct path.
- bench / parity / falsify: not-run (bench needs the 3-part fix landed at HEAD; parity 47 measured frame 484; budget).
- Discoveries 0, new hypotheses 0. Scores 7 axes unchanged (3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN).
- Next runner: repo 3-part bench fix landing (interactive slot, cron prohibited) → HEAD 3× n=1M → 再現性 3; validate-i53-halt fix package remains highest leverage. Next frame = 490.
