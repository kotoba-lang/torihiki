# 2026-11-05 20:2x torihiki-fix tick — venue-layer integer canon: SCOPE +
# BROKEN-measured, NOT landed (budget). "not run" != "passed": every number
# below was run and counted this tick; GREEN is NOT run and NOT claimed.

Load gate: torihiki_state.sh absent on this host (chronic-fault precedent);
uptime direct 20:17 — 1.49 / 1.47 / 1.39, all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-2020, branch
bot/torihiki-fix-20261105-2020 off origin/main d433258 (PR #105 tip).
maturity.md untouched (measurement bots' file).

## Queue check (measured by code read at d433258)

Same verdict as the 1821 record: maturity.md NEXT fully LANDED; carried
findings "None" in the last three landing records. Queue head = NEW probe
target. This tick's probe: the venue layer's integer doors.

## Finding (measured this tick): `integer?` judged the venue doors —
## a TYPE test on the JVM, a VALUE test on JS

`integer?` is a TYPE test on the JVM (a Double is never an integer, however
whole) and a VALUE test on JS (every whole number is one) — the engine fixed
this at the api door with `int-like?` (api.cljk:75, provenance
kotoba.spec.int-like), but the venue layer (ported 2026-11-04, after the
canon) never got it. Measured 2026-11-05 by evidence/probe-venue-integer-
canon.cljk on the nbb leg (kbb --backend sci via script/nbb-leg.sh) and the
JVM leg (renamed copy, clojure -M load-file). Same input, two verdicts, no
throw anywhere:

| input                        | JVM                    | nbb (JS)                |
|------------------------------|------------------------|-------------------------|
| `(integer? 3.0)`             | false                  | true                    |
| frame seq "n" 3.0            | :bad-seq rejected      | accepted, :n 3          |
| sym "digits" 3.0             | :bad-sym rejected      | accepted, :digits 3     |
| ->venue-order qty 3.0        | {:error :bad-qty}      | sent, volume "0.03"     |
| ->venue-order price 3.0      | {:error :bad-price}    | sent, price "0.00003"   |
| (string-of 3.0 "0.01")       | nil                    | "0.03"                  |
| check-entry digits 3.0       | :bad-venue-digits      | computed                |
| frame seq "n" 2^53           | accepted, :n 2^53 stored raw (past i53) | same    |

The last row is the falsify-8 class at the frame door: 2^53 is stored raw,
one past i53-max = 2^53 − 1 — a value state must never hold.

## The fix (implemented, NOT verified green)

One canon at the boundary — `torihiki.venue/int-like?` + `int-in?`
(provenance: api/int-like?, kotoba.spec.int-like; re-implemented because the
venue layer is a leaf). Sites:

1. venue.cljk `string-of` guard -> int-like?, computes on (long n).
2. venue.cljk `point-of` guard -> int-in? digits 0..max-scale, (long digits).
3. venue.cljk `->venue-order` qty/price guards -> int-like?.
4. venue_mt.cljk seq gate -> (v/int-in? n 0 v/i53-max), stores (long n).
5. venue_mt.cljk sym digits gate -> (v/int-in? d 0 v/max-scale), stores long.

REASON literals unchanged: :bad-seq / :bad-sym / :bad-qty / :bad-price.
No throw introduced on any path.

## Measured (exact counts) — BROKEN first, all three legs

Tests written first (5 deftests, 17 assertions, overflow_gates pattern —
REASON literals for refusals, VALUE pins for kept state; each new deftest
carries at least one pre-fix-red assertion):

- kbb (`kbb -M:test`) BROKEN: **661 tests / 5582 assertions / 2 failures /
  0 errors**, KBB_EXIT=1 (/tmp/tori-2020-broken-kbb.out) — kbb's integer?
  behaves like JS, so only the domain pin is red there.
- nbb (script/nbb-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/github/
  kotoba-lang) BROKEN: **661 / 5582 / 2 failures / 0 errors**,
  NBB_EXIT=1 (/tmp/tori-2020-broken-nbb.out).
- JVM (script/jvm-test.sh) BROKEN: **661 / 5582 / 15 failures / 0 errors**,
  JVM_EXIT=1 (/tmp/tori-2020-broken-jvm.out) — all 5 new deftests red
  (6+3+2+2+2 assertion failures).

Baseline: 656 / 5565 (PR #105 landing record). +5 deftests / +17 assertions
= 661 / 5582 on every leg.

## Not run / not claimed

- GREEN on all three legs: NOT run (budget). The fix is in the tree of this
  branch but UNVERIFIED; do not merge without the three-leg green run
  (identical counters on kbb / script/jvm-test.sh / script/nbb-test.sh).
- PR create/merge: NOT run (budget).
- The probe raw outputs: /tmp/probe-nbb.out, /tmp/probe-jvm.out (also
  re-runnable from evidence/probe-venue-integer-canon.cljk).

## Handoff (next tick)

Branch bot/torihiki-fix-20261105-2020 carries: the fix (venue.cljk,
venue_mt.cljk), the 5 deftests (venue_test.cljk, venue_mt_test.cljk), the
probe (evidence/probe-venue-integer-canon.cljk; a copy at script/ — remove
before landing). Next: run the three legs green (expect 661/5582 0F/0E on
each), delete script/probe-venue-integer-canon.cljk, then PR + merge.
