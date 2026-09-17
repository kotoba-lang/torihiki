# frame 476 genuine run (2026-09-17 02:1x, cron, no code changes)

- Load gate: 02:10 pre-run 13.77/15.91/16.05 — all windows <20, gate passed (2nd consecutive gate-passed frame).
- HEAD 366321a; measurement copy /tmp/tori-f451 unchanged from frame 475. Working tree additions this frame: this file + evidence/f476-*.out copies only.
- **suite 145 same-count BOTH RUNTIMES PASS on the same copy (frame 475's JVM run + frame 476 nbb run + fresh frame 476 JVM run)**:
  - JVM `clojure -M:test` → **357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0** (evidence/f476-test-jvm.out) — reproduces frame 475.
  - nbb `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` → **357 tests / 915 assertions, 0 failures, 0 errors, NBB_EXIT=0** (evidence/f476-test-nbb.out).
- **frame 475 nbb failure root cause CONFIRMED environmental, not a regression** (3 layers, all fixed in-run on the measurement copy):
  1. `/tmp/tori-f451` lacked `script/` — `nbb-classpath.cljk` ENOENT. Copied `tests-on-nbb.cljk`, `nbb-classpath.cljk`, `loads-on-nbb.cljk` from repo (no code change; script-only).
  2. Bare `kbb --backend sci script/nbb-classpath.cljk` now fails **even in the repo** with `Could not find namespace: kotoba.lang.text` — the engine's default resolver no longer reaches the sibling checkout. Workaround: prepend `~/.gitlibs/libs/io.github.kotoba-lang/text/73bdb13a…/src` to the classpath (pinned sha = pinned dep code). With `KOTOBA_CHECKOUTS=<orgs dir>` the classpath script then extracts all 7 pins into the copy's `.nbb-deps/` and prints a full CP (CP_EXIT=0).
  3. Node module resolution: `@noble/hashes/sha2.js` not found from /tmp — fixed with `NODE_PATH=/Users/junkawasaki/github/com-junkawasaki/node_modules` (superproject root node_modules).
  ⇒ **nbb suite recipe update**: `cd <copy> && KOTOBA_CHECKOUTS=<orgs/kotoba-lang> NODE_PATH=<superproject>/node_modules kbb --backend sci --classpath "<gitlibs-text-src>:$(kbb … nbb-classpath.cljk-with-that-prefix)" script/tests-on-nbb.cljk`. Recorded here because script/nbb-classpath.cljk's own header invocation is stale under the current engine build.
- **seeded fuzz reproducibility (第 40 実測)**: JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run))'` vs nbb driver (pre-require + load-string, `evidence/fuzz-nbb-driver.cljk`) on the same copy → both EXIT=0, 2715 bytes each, **`diff` empty — byte-identical** (evidence/f476-fuzz-{jvm,nbb}.out). Note: `kbb -M -e 'load-file'` does NOT work (kbb -M rejects load-file; must be `clojure -M`).
- parity: **NOT MEASURED, exit 2 could-not-measure** (evidence/f476-parity.err): the /tmp copy lacks `kotoba/torihiki/fixed.kotoba`, amu compile fails with :project-link-failed "project path is not readable". Environmental incompleteness of the copy, not a runtime drift. Next runner: copy `kotoba/` into the measurement copy (or run parity in-repo) before the parity leg.
- bench: not-run (budget exhausted after fuzz/parity legs).
- Scores 7 axes unchanged: 3/3/3/3/2/1/1; falsifications 16, f8–f17 OPEN. NEXT unmeasured 0 (fix 未着手, cron code-change 禁止).
- Next runner (frame 477): bench 37th on the same gate (n=1M, /tmp copy has f15 owner-wired bench.cljc; expect cancelled≠0 per frame-451 stored-owner finding), then parity with `kotoba/` present in the copy.

nbb suite tail: `Ran 357 tests containing 915 assertions. / 0 failures, 0 errors.` (JVM tail identical.)
