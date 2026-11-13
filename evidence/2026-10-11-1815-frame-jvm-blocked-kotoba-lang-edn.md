# 2026-10-11 18:1x frame — loadgate PASSED, JVM re-baseline blocked on kotoba.lang.edn compile error (NOT RUN)

- Script error to report upstream: `torihiki_state.sh` line 3 `cd` target
  `/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/torihiki` does not exist;
  the repo is at `/Users/junkawasaki/github/kotoba-lang/torihiki`. Script needs its path fixed.
- Gate: load 12.09/10.28/9.59 (all windows < 20) → genuine window existed.
- HEAD: 1ceb357233c528c0d40409cb6d4a6f69ed1864ea (unchanged). Repo `git status --porcelain`:
  only untracked evidence/*.md visible; code diff 0.
- Procedure: fresh copy of repo to /tmp/tori-c11 (previous /tmp/tori-f451 was wiped by reboot),
  39 src/test .cljk → .cljc copies. Also created .cljc siblings in cached gitlibs
  io.github.kotoba-lang/{io,pprint,test,bytes} (10 files, originals kept).
- Suite result: **blocked**. `clojure -M:test` fails at
  `Syntax error compiling at (kotoba/lang/edn.cljc:1:1): Unable to resolve symbol: escape-controls in this context`
  (pinned edn sha 79dba32; file is balanced and defines escape-controls at :537 — cause
  unresolved within budget; requires kotoba.lang.edn compile trace). Same error via plain
  `(require 'kotoba.lang.edn)`. Kotoba.test FileNotFound fixed en route (needed .cljc siblings).
- JVM suite / parity / fuzz / bench: NOT RUN (first failure is upstream of all of them).
- NEXT: resolve the kotoba.lang.edn escape-controls compile failure (this is the concrete
  content of "re-establish JVM invocation conventions post kotoba.test migration"),
  then rerun the /tmp/tori-c11 copy: `clojure -M:test`, then parity/fuzz/bench.
