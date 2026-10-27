# 2026-10-28 06:3x torihiki-fix tick — loadability fix package LANDED (branch `bot/torihiki-fix-20261028-0019`)

Continues the 00:19 and 04:15 ticks' handoff worktree (`~/github/wt/fleet/bot/torihiki-fix-20261028-0019`,
branch off origin/main a0add53). Host load at tick start: 9.79/9.35/9.94 (all <20, gate passed).

## What the package fixes (three measured defects at HEAD a0add53)

1. **Engine fails to LOAD** (00:19 tick's finding): PR #74 extracted `torihiki.clearing`
   to the kotoba monorepo from a tree predating PR #73, so `cl/flat-spec-ok?` and the
   tier `:max-leverage` clause vanished from under the call sites. Fixed by RE-HOMING
   the lost gates as public `api/flat-spec-ok?` and `api/tier-leverage-ok?`
   (provenance docstrings) and wiring all 4 call sites (api.cljk :amend-market/
   :list-market doors; state.cljk's 2 apply-tx no-op nets).
2. **kbb/nbb classpath rot** (00:19 tick): `nbb.edn` was not updated when PR #75 moved
   text/chain/merkle-sum to git deps; plus the missing `io-multiformats` coordinate
   (`chain.core` → `ipld.core` → `multiformats.core`, unnamed by the monorepo's
   lib/kotoba.ioIpld deps.edn — every leg died with "Could not find namespace:
   multiformats.core"). Fixed in nbb.edn + deps.edn (same kotoba sha as ioIpld).
3. **nbb-classpath.cljk could not resolve monorepo pins** (this tick, measured):
   the script mapped each dep NAME to a standalone sibling checkout and archived the
   whole repo — both assumptions died with the monorepo move ("unresolvable: pprint @
   b94158fd … — commit not in …/pprint", exit 2). Fixed: the object store is the repo
   named by `:git/url` (basename minus `.git`), and the extraction is the `:deps/root`
   subtree (`git archive <sha>:<deps-root>`), so `<dest>/src` and `<dest>/deps.edn` are
   the library's own.
4. **tests-on-nbb.cljk floor/list disagreement from PR #74** (this tick, measured):
   PR #74 moved `torihiki.address-test`/`clearing-test`/`thorchain-test` into kotoba
   `lib/kotoba.torihikiCore` (registered there in `lib/test-suites.json`) and trimmed
   the require list to 26, but left `expected-namespaces` at 29 and merged with
   "Not run: this repository's build and tests". First nbb leg after the move refused:
   "26 of 29 namespaces ran" (exit 2) with 516/3605 clean otherwise. Floor re-anchored
   to 26 with provenance (the constant stays hard; it is the drift guard, not the list
   length).

## Tests — BROKEN-first, every stage measured

- BROKEN, tier pins (engine fix MINUS the tier clause), `kbb -M:test`:
  **516 tests / 3605 assertions, 4 failures, 0 errors, exit 1** — exactly the designed
  RED pins (3 tier REASON pins `:max-leverage 2.5`/`0`/`-5` + the tier engine-net VALUE
  pin). Evidence: `2026-10-28-loadability-broken-pins-kbb.out` (trimmed from the 16.9 MB
  full run; counts unmodified).
- BROKEN, classpath fix (this tick's nbb-classpath change, before it): exit 2,
  "unresolvable: pprint @ b94158fd48ec541b70d3c455912a06f407537ddc — commit not in
  /Users/junkawasaki/github/kotoba-lang/pprint" (recorded in this file; no output file
  retained — 2-line stderr).
- BROKEN, floor fix (before it): nbb leg 516/3605 0F/0E but "REFUSING to report a pass:
  26 of 29 namespaces ran", exit 2. Evidence: `2026-10-28-loadability-broken-floor29-nbb.out`.
- **GREEN, identical counts on all three legs — 516 tests / 3605 assertions,
  0 failures, 0 errors, 26 namespaces, exit 0 each:**
  - `kbb -M:test` (SCI) — evidence: `2026-10-28-loadability-green-kbb.out` (26 `Testing` lines)
  - `bash script/jvm-test.sh` (renamed-copy JVM leg via the rewritten script/jvm-leg.sh —
    the recipe's first measured run since the monorepo move) —
    evidence: `2026-10-28-loadability-green-jvm.out` (26 `Testing` lines, byte-equal ns set to kbb)
  - `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang bash script/nbb-test.sh`
    (the runtime that ships) — evidence: `2026-10-28-loadability-green-nbb.out`
    ("namespaces 26/26", "TESTS-ON-NBB: pass")

Cross-runtime claim: 516/3605/0F/0E/26ns on kbb, JVM and nbb — identical.

## Not run / not claimed

- Parity legs, fuzz digest, bench: measurement bots' gates (maturity.md NEXT ①
  re-anchoring), not run here.
- JVM BROKEN leg (the PR #73 convention notes it as optional): not run.
- The 3 moved core test namespaces run in kotoba.git (lib/test-suites.json, jvm-only
  section) — the deployed runtime (nbb) no longer runs them anywhere; recorded here so
  the measurement bots can weigh it.

maturity.md untouched (measurement bots' file).
