# 2026-11-07 10:4x torihiki-fix tick — om decision sums: PROBE MEASURED, NOT LANDED (budget)

Load gate: torihiki_state.sh absent (chronic-fault precedent); uptime direct
10:17 — 1.35 / 1.54 / 1.60, all windows < 20, gate passed.

Worktree bot/torihiki-fix-20261107-1033 (~/github/wt/fleet/bot-torihiki-fix-om-dec-1033)
off origin/main 4749b42 (PR #114 merge). The frame budget ended after the
probe and the tests; the BROKEN/GREEN suite legs and the fix did NOT run.
NOTHING landed; no PR. Branch pushed as WIP for the next tick.

## MEASURED — the 08:1x tick's model is now a runtime measurement

`evidence/probe-om-decision-sums.cljk` run through PRODUCTION
`torihiki.order-margin/orders-allowed?` on all three legs (synthetic-seed,
labelled; every engine path untouched). outputs:
omdec-probe-{kbb,jvm,nbb}.out. The divergence is CONFIRMED in BOTH
directions, one signed order each, no throw anywhere:

| seed | true verdict | kbb | JVM | nbb |
|---|---|---|---|---|
| A forward (equity i53-min, bonded 0, committed 1; resting bid 20 @ lvl 1, mark 2; req0 0 / req1 1 / delta 0; before = 2^53, after = 2^53+1) | REFUSE | **ALLOW** | REFUSE | **ALLOW** |
| B reverse (equity i53-min, bonded 1, committed 3, cross position 20; bid 21 takes 1 @ lvl 1 and rests 20; req0 1 / req1 2 / delta 1; before == after) | ALLOW | **REFUSE** | ALLOW | **REFUSE** |

The raw `available = equity - bonded - committed` itself forks on seed B:
JVM -9007199254740995 vs kbb/nbb -9007199254740996 (f64 rounding —
runtime fingerprint in the probe: kbb/nbb `(+ 9007199254740992 1)` rounds
to …992, the JVM holds …993; `integer? 3.0` is true on the JS legs).
kbb and nbb agree with each other and fork from the JVM in both directions.

## Tests WRITTEN, NOT run (BROKEN-first is the next tick's first action)

test/torihiki/overflow_gates_test.cljk += 2 deftests / 6 assertions,
overflow_gates pattern: VERDICT pins per seed (A: false; B: true), REASON
pins through api/validate (A: :insufficient-margin; B: nil) and VALUE pins
for the clamp (om/available = fx/i53-min on both seeds; -135 below the
ceiling, byte-identical). Expected BROKEN: kbb/nbb red on the 4
verdict/REASON pins + both VALUE pins; JVM red on the VALUE pins only.

## Fix shape (agreed, NOT applied — apply only after BROKEN is measured)

The naive "fold every raw diff through fx/sadd" shape from 08:1x CANNOT
distinguish the two seeds: both before/after clamp to i53-max and the
verdict would become ALLOW for seed A — flipping the true REFUSE. The
verdict is instead decided WITHOUT the two differences:

  `after <= before`  ==  `(<= (- req1 req0) delta)`   (avail cancels exactly;
      req1, req0 in [0, i53-max] so the difference is in-domain and exact
      on f64 and longs alike)
  `after <= 0`       ==  `(<= req1 (fx/sadd avail delta))` (the saturated
      right side can never flip a comparison against a non-negative req1)

sites in src/torihiki/order_margin.cljk:
1. `available` (line 281): fold through fx/sadd — `(-> equity (fx/sadd (- bonded)) (fx/sadd (- committed)))`.
2. `shortfall` (line 336): DELETE (the raw diff is gone by the identity).
3. `orders-allowed?` line 388 `(+ (cross-size …) signed)` → `fx/sadd`.
4. line 388 `(+ avail delta)` → inside the identity as `fx/sadd`.

Below the ceiling the identity is the same arithmetic — the existing
order-margin suite (28 tests) stays green as the regression pin.

"not run" != "passed": no suite/parity/bench leg ran this frame. maturity.md
untouched (measurement bots' file).
