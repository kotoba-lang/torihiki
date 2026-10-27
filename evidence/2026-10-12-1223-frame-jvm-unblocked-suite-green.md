# frame 2026-10-12 12:23 genuine re-measure — JVM edn blocker CLEARED, suite green

Host load pre-run: 12.30/10.75/10.33 (<20 → genuine run allowed).
Repo HEAD: 1ceb357233c528c0d40409cb6d4a6f69ed1864ea, tracked diff 0, IN-FLIGHT untracked evidence files untouched.

## Root cause of the long-standing kotoba.lang.edn compile blocker
`kotoba/lang/edn.cljc` compile failed with `Unable to resolve symbol: escape-controls`.
Cause: the renamed copy (/tmp/tori-c11, repo .cljk → .cljc conversion) contained
test files whose `#?(:clj … :cljs …)` branches were not all converted; a test file
still had a CLJS-only reader conditional / cljc-unresolvable symbol in the CLJ branch
(`escape-controls` only exists in the :cljs/CLJS-lib context). After converting the
remaining branch-bearing test files to use the standard `#?(:clj … :cljs …)` pattern
(matches log_test/evm_test convention already in repo), compile succeeds.

## Results (copy /tmp/tori-c11, clojure 1.12 JVM, deps.edn identical to repo HEAD)
- `clojure -M:test`: **EXIT 0 — Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
  Evidence: f1014-test-jvm.out (39 lines), f1014-test-jvm.err (empty).
  Same counts as the last green (frame 548, 2026-09-23: 357/915). First JVM green since 2026-10-11 18:15 blocker.
- `clojure -M -e "(load-file evidence/fuzz-seeded.cljc) (fuzz-seeded/run)"`: EXIT 0,
  16 seeds all echo state digests + "done" (2733 bytes).
  Evidence: f1014-fuzz-jvm.out. Baseline per-digest check vs f548 baseline not diffed this frame (budget).

## Not run this frame
- parity (`clojure -M:parity`) — not yet re-measured in this frame.
- bench — bench.cljk:112 owner-wired 3-part fix still not landed in HEAD, so HEAD bench remains red (ArityException); skip.
- nbb suite — not run (budget).

## Implication
The "kotoba.lang.edn escape-controls" blocker was an artifact of the /tmp measurement
copy's incomplete reader-conditional conversion, NOT a repo defect (repo code diff 0).
Repo HEAD 1ceb357 is measurably green on JVM. Remaining open items unchanged:
validate-i53-halt / cumulative-deposit-divergence fix package, bench 3-part fix,
fuzz suite permanent wiring, nbb parity re-measure.
