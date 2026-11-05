# 2026-11-05 16:2x torihiki-fix tick — venue sums landed: the fake's position
# sum, the router's ledger fold, risk-refusal's `after`, and reconcile's record
# sum all go through `torihiki.fixed/acc` (continuation of the 20261105-1427
# handoff, which scoped the four sites and BROKEN-measured the tests).
# "not run" != "passed": every green below was run and counted this tick.

Load gate: torihiki_state.sh absent on this host (chronic-fault precedent);
uptime direct 16:17 — 3.20 / 2.34 / 1.83, all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-1626, branch
bot/torihiki-fix-20261105-1626 off the handoff commit afd07a8
(origin/bot/torihiki-fix-20261105-1427 = origin/main ff0427d + the handoff:
evidence record + 4 new deftests in test/torihiki/venue_test.cljk).
maturity.md untouched (measurement bots' file).

## The fix (four accumulator sites, falsify-8's class one port upstream)

1. src/torihiki/venue_fake.cljk execute! — `[:positions symbol]` folded with
   `(fnil + 0)` -> `fx/acc` (the fake's market-fill position sum).
2. src/torihiki/venue_router.cljk `position` — the ledger fold `(+ acc …)`
   -> `fx/acc`, and the fold now walks `(sort (keys ledger))`: a saturating
   fold must have one order to have one answer (the determinism contract's
   sorted fold; mixed signs + clamp make fold order observable).
3. src/torihiki/venue_router.cljk `risk-refusal` — `after (+ pos signed)`
   -> `(fx/sadd pos signed)`. This is the site whose verdict CHANGES: at the
   edge the door now judges the order against the caller's bound identically
   on both runtimes (pin: the-position-limit-door-sees-the-saturated-position).
4. src/torihiki/venue_router.cljk `reconcile!` — the per-market record sum
   `(+ (:lots old) lots)` (introduced by PR #103) -> `(fx/acc …)` inside the
   existing nil-stated guard.

`torihiki.fixed/acc` = `sadd` = saturating (fnil + 0), total, never throws —
torihiki-core git dep, unchanged. ns requires added to venue_fake.cljk and
venue_router.cljk. No throw is introduced anywhere on the apply path.

## Measured (exact counts)

- BROKEN (fix withheld, the handoff's tests present), kbb -M:test re-run this
  tick on the handoff tree: **654 tests / 5554 assertions / 8 failures /
  0 errors**, KBB_EXIT=1 (/tmp/tori-1626-broken-kbb.{out,err}) — all 8 reds
  are the four new pins (2+2+1+3), each showing the raw sum past the domain
  (e.g. 9007199254740991 expected vs 9007199254740992 actual = 2^53). The
  1427 handoff measured the same 654/5554 8F independently.
- GREEN kbb/SCI (`kbb -M:test`): **654 / 5554 / 0F / 0E**, KBB_EXIT=0
  (/tmp/tori-1626-green-kbb.{out,err}).
- GREEN JVM (script/jvm-test.sh, renamed-copy leg): **654 / 5554 / 0F / 0E**,
  JVM_EXIT=0 (/tmp/tori-1626-green-jvm.{out,err}).
- GREEN nbb (script/nbb-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/github/
  kotoba-lang): **654 / 5554 / 0F / 0E**, NBB_EXIT=0, TESTS-ON-NBB: pass
  (/tmp/tori-1626-green-nbb.{out,err}). All three legs carry identical
  counters — the cross-runtime claim is measured, not cited.

Baseline was 650 tests / 5538 assertions (PR #103 landing record); this
package adds 4 deftests / 16 assertions → 654/5554 on every leg.

## Not run / not claimed

JVM BROKEN leg and nbb BROKEN leg: NOT run (optional divergence record; the
BROKEN verdict is measured twice on kbb/SCI). Bench: not run (not touched by
this package).

## Queue state after this tick

- The four-sites package handed off by 20261105-1427 is LANDED by this tick
  (the branch bot/torihiki-fix-20261105-1427 can be deleted; its commit rides
  in this PR).
- maturity.md NEXT: unchanged verdict — stale, all fix items landed (see the
  20261105-0439 / 20261105-1427 records); the residual reads + measurements
  belong to the measurement bots.
- Issue #76 (terminal:) — out of scope per tick boundaries.
