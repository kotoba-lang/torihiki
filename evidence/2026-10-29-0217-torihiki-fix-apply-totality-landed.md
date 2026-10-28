# 2026-10-29 02:2x torihiki-fix tick — LANDED: apply-path totality package

Load gate: torihiki_state.sh 02:17 — 12.98 / 21.10 / 25.90. Not all windows
> 20 (1-min < 20 and declining — the frame-397/401/510 genuine-lite shape);
the tick ran. Host load at tick end: not re-measured.

Branch bot/torihiki-fix-20261028-1224 (worktree kept from the 12:24/14:17
ticks) off origin/main 1cdb4a2. maturity.md untouched (measurement bots'
file).

## What landed

The apply path is TOTAL: an order, amend, cancel or trigger the engine cannot
apply is dropped as a no-op — never thrown with, never truncated, never
half-executed. Two doors, one shape rule (torihiki.api/reasons at both):

- api.cljk: four public shape gates over the existing private pieces —
  order-tx-shape (market + account + validate-order-shape, which already
  carries the falsify-7 notional cap), amend-tx-shape (nat-int oid — the
  (long 2.5) truncation that amended an order never named — then the new
  resting shape), cancel-tx-shape (market + account + nat-int oid), and
  trigger-tx-shape (validate's :trigger clause exactly, including the
  :bad-trigger-order report mapping). n-levels defaults to 0 (a bookless
  market answered a shape question with (dec nil) — a throw at the door).
  validate :scale and :twap gained their MIRROR DOORS (:bad-amount /
  :bad-flags per in-bounds rung; :bad-amount at the slice level for bids),
  so the gate never refuses what validate passed — internal dispatchers
  (fire-twaps:885, scale rungs:1119, amend re-dispatch:1374) pass the gate
  unchanged (audited).
- state.cljk: no-op gates at the TOP of apply-tx :order (before bit-and
  computes on flags) / :amend (before bk/order-of truncates the id and
  before the cancel half can run) / :cancel (before (long nil) throws) /
  :trigger (before trg/valid? computes on the embedded order), plus the
  FIRE-TIME gate in fire-triggers on the RAW embedded order before
  cl/reducing-qty — pre-fix state can carry triggers this engine never
  validated. Refusals are no-ops with reasons from api/reasons; no throw on
  the apply path was introduced.
- test/torihiki/apply_totality_test.cljk (new, 9 deftests): VALUE pins at
  the engine (nothing rests, nothing shrinks, nothing cancels) + REASON pins
  at both doors + the closed-set taxonomy pins. Every throw-carrying pin
  routes through `applied` so a throw FAILS the pin.
- script/tests-on-nbb.cljk: torihiki.apply-totality-test registered;
  floor 26 -> 27 with provenance.

## What was measured (exact counts)

- BROKEN (previous tick, 2026-10-28 14:17, stubs = doors absent):
  525 tests / 3709 assertions / 43 failures / 0 errors, exit 1 — all 43 in
  torihiki.apply-totality-test; the 516-test baseline stayed green. Raw:
  2026-10-28-1417-torihiki-fix-broken4.{out,err} (copied in-tree here).
- GREEN (this tick), three legs, same count on all three:
  - kbb -M:test (SCI):        525 tests / 3721 assertions, 0F/0E, exit 0
  - script/nbb-test.sh (nbb): 525 tests / 3721 assertions, 0F/0E, exit 0,
    namespaces 27/27, TESTS-ON-NBB: pass
  - script/jvm-test.sh (JVM renamed-copy leg): 525 tests / 3721 assertions,
    0F/0E, exit 0
  Raw: 2026-10-29-0217-torihiki-fix-green-{kbb,nbb,jvm}.out.
  Assertion count note: the 14:17 tick predicted 3709 (the BROKEN-run
  count); measured 3721 because the `when (not= ::threw ...)` guards now
  reach the 12 assertions the throws used to skip. Nothing is claimed here
  that was not run.

## Known scope residual (documented, not fixed here)

apply-tx :twap/:scale take raw garbage via direct apply-tx ((range count*)
on a string throws) — the user path is validate-gated and the engine gate
covers the :order dispatch they produce. Pre-fix :twaps state with string
:remaining throws in fire-twaps arithmetic — same class, same residual.
