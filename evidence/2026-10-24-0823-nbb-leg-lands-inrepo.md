# 2026-10-24 08:2x — torihiki-fix tick: nbb leg lands in-repo (script/nbb-leg.sh + script/nbb-test.sh)

Landing: PR branch `bot/torihiki-fix-20261024-0823` (base 5589b4c, origin/main).
Scope: reproducibility item — NEXT①'s "JVM/nbb 呼び出し規約の再確立", nbb half
(JVM half landed as script/jvm-leg.sh / jvm-test.sh / jvm-bench.sh in PRs
#60/#61). No engine source changed; maturity.md scoring untouched.

## BROKEN (measured before the fix, base 5589b4c in a clean worktree)

1. `ls script/run-nbb-tests.cljs` → exit 1, "No such file or directory" —
   the runner cited by script/tests-on-nbb.cljk:6 has never existed
   (first flagged evidence/2026-09-03-maturity-remeasure-2100.md).
2. The README command as written from a clean checkout → exit 1,
   `Cannot find module '@noble/hashes/sha2.js'` (package.json declares it;
   nothing in the documented flow installs it).
3. The nbb leg ran a SUBSET and declared a pass: with node_modules present,
   script/tests-on-nbb.cljk ran 26 namespaces → 468 tests / 3265
   assertions / 0 failures / "namespaces 26/26 / TESTS-ON-NBB: pass",
   exit 0 — while `kbb -M:test` ran 28 namespaces → 502 tests / 3504
   assertions. Missing namespaces: torihiki.overflow-gates-test (the i53
   gates) and torihiki.fuzz-seeded-test (the seeded-fuzz invariants). The
   file's own floor (expected-namespaces 26) blessed the subset.
4. README Test section mislabeled `kbb -M:test` as "the JVM"; measured
   2026-10-24 it runs the suite on the kbb engine with cljs.test (stderr:
   "the JVM runner named by the alias does not exist on this engine").

## GREEN (measured after the fix, same worktree)

- `script/nbb-test.sh` (KOTOBA_CHECKOUTS=…/kotoba-lang): 502 tests / 3504
  assertions / 0 failures / 0 errors / namespaces 28/28 /
  "TESTS-ON-NBB: pass", exit 0.
- `script/jvm-test.sh`: 502 tests / 3504 assertions / 0 failures / 0
  errors, exit 0.
- `kbb -M:test` (re-run on the final tree): 502 tests / 3504 assertions /
  0 failures / 0 errors, exit 0.
  → same-count across all three legs: 502/3504.
- `script/nbb-leg.sh kotoba-parity.cljk`: fixed 38 cases 0 drift /
  fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass, exit 0.
- `script/nbb-leg.sh loads-on-nbb.cljk`: LOADS-ON-NBB: pass, exit 0.

## Files

- script/nbb-leg.sh (new): the nbb recipe — npm ci for @noble/hashes when
  node_modules is missing (NODE_PATH offline fallback), pins classpath via
  script/nbb-classpath.cljk, run from repo root. KOTOBA_CHECKOUTS selects
  the sibling-checkout object store.
- script/nbb-test.sh (new): wrapper selecting tests-on-nbb.cljk.
- script/tests-on-nbb.cljk: +torihiki.overflow-gates-test,
  +torihiki.fuzz-seeded-test (require + run-tests), floor 26 → 28 with the
  subset incident recorded, run-nbb-tests.cljs citation retired.
- script/nbb-classpath.cljk: header usage fixed (the `node -e '' # nothing
  to install` claim was wrong), points at script/nbb-test.sh.
- README.md: Test section re-pointed at script/jvm-test.sh +
  script/nbb-test.sh, kbb -M:test labeled for what it is, counts citation
  refreshed (was "325 tests / 847 assertions" @ 96ac270, 2026-08-31).
