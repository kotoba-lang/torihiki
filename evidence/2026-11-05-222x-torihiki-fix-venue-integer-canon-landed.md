# 2026-11-05 22:2x torihiki-fix tick — venue-layer integer canon: LANDED.

Load gate: torihiki_state.sh absent on this host (chronic-fault precedent);
uptime direct 22:18 — 1.50 / 1.48 / 1.45, all windows < 20, gate passed.

Continued the kept handoff branch bot/torihiki-fix-20261105-2020 (fix +
5 deftests + BROKEN measurements from the 20:2x tick; see
evidence/2026-11-05-202x-torihiki-fix-venue-integer-canon-not-landed-broken.md
for the finding, the 5 fix sites and the BROKEN counts). This tick ran the
three GREEN legs the handoff asked for and landed the package.

## Measured this tick (exact counts) — GREEN, all three legs

- kbb (`kbb -M:test`): **661 tests / 5582 assertions / 0 failures / 0
  errors**, KBB_EXIT=0 (/tmp/tori-green-kbb.out).
- JVM (script/jvm-test.sh, renamed-copy leg): **661 / 5582 / 0 failures /
  0 errors**, JVM_EXIT=0 (/tmp/tori-green-jvm.out).
- nbb (script/nbb-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/github/
  kotoba-lang): **661 / 5582 / 0 failures / 0 errors**, NBB_EXIT=0
  (/tmp/tori-green-nbb.out) — "TESTS-ON-NBB: pass".

Identical counters on all three legs (661/5582), matching the BROKEN-count
identity (baseline 656/5565 + 5 deftests / +17 assertions). BROKEN was run
and counted on all three legs in the 20:2x tick before the fix (kbb 2F,
nbb 2F, JVM 15F); no test in this package is unmeasured-red.

Removed script/probe-venue-integer-canon.cljk before landing (the probe
copy the handoff flagged; evidence/probe-venue-integer-canon.cljk stays as
the measured probe). maturity.md untouched.

## What landed

`torihiki.venue/int-like?` + `int-in?` (provenance api/int-like?,
kotoba.spec.int-like; re-implemented — the venue layer is a leaf) judged at
the 5 integer doors (venue.cljk string-of / point-of / ->venue-order
qty+price; venue_mt.cljk seq + sym digits), state stores the long, seq
bounded to the i53 domain. REASON literals unchanged (:bad-seq / :bad-sym
/ :bad-qty / :bad-price), no throw on any path.

## Left (queue, next tick)

- venue sums (bot-torihiki-fix-20261105-1427): 4 accumulator sites scoped,
  tests BROKEN-measured on kbb (8F) — needs BROKEN on the nbb/JVM legs,
  then the sum gates (torihiki.fixed/acc discipline), then GREEN x3.
- wire-number-canon (bot/torihiki-fix-20261103-0222): "u" digits route
  validated byte-for-byte; "d" exact expansion needs the non-BigInt route
  (SCI BigInt multiply-loop corruption, probe-nums5 unrun); decode pins
  must be WIRE-level per the 14:2x JVM =-sensitivity correction.
