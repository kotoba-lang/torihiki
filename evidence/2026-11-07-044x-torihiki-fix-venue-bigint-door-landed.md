# 2026-11-07 04:4x torihiki-fix tick — venue BigInt door: one integer, one
# verdict. LANDED.

Load gate: torihiki_state.sh absent at ~/.hermes/scripts/ (chronic-fault
precedent); uptime direct 04:17 — 2.29 / 1.46 / 1.32, all windows < 20,
gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261107-0419, branch
bot/torihiki-fix-20261107-0419, off origin/main feb5b8a (PR #112 merge).

Package = the last named carry (evidence/2026-11-07-0234-…-no-wire-form-
door-landed.md "Left (carried)", PR #111 carry (b)): **torihiki.venue/int-like?
keeps its own re-implementation and does not accept a BigInt** — while
torihiki.api/int-like? has judged a true BigInt as its integer since the
BigInt door landed. Measured gap (evidence/probe-venue-bigint-door.cljk,
kbb pre-fix): the SAME request — a BigInt qty — was refused `:bad-qty` at
the venue boundary while the JVM sent it as volume "0.03"; a BigInt digit
count disabled the market (`:bad-venue-digits`) while the JVM bound it;
one request, two ledgers. The venue layer is a leaf and keeps its own
re-implementation — the fix does not make it require the engine surface.

## Measured (exact counts) — BROKEN, fix withheld (tests present)

Counters identical on all three legs: **706 tests / 5702 assertions**
(699/5682 baseline [PR #112 landing record] + 7 deftests / 20 assertions).

- kbb `kbb -M:test`: **14F / 0E**, KBB_EXIT=1 — exactly the designed reds:
  2 door-symmetry VALUE pins (int-like?, int-in?), 2 boundary VALUE pins
  (BigInt qty, BigInt price), 2 value-guard VALUE pins (string-of,
  check-entry digits), 2 out-of-domain REASON pins (:qty-out-of-range,
  :price-out-of-range — refused `:bad-qty`/`:bad-price` instead), and 3+3
  frame-parser VALUE pins (BigInt seq frame processed + stored as its
  long, BigInt digits stored as its long). (/tmp/vbd-broken-kbb.out)
- nbb `KOTOBA_CHECKOUTS=… script/nbb-test.sh`: **14F / 0E**, NBB_EXIT=1,
  namespaces 38/38 — same designed reds as kbb. (/tmp/vbd-broken-nbb.out)
- JVM `script/jvm-test.sh`: **0F / 0E**, JVM_EXIT=0 — the JVM's `integer?`
  answers true for its own BigInt (`(integer? 5N)`), so the venue door
  ALREADY sent/accepted the pinned verdicts there. The pins freeze that
  verdict as the cross-runtime spec; the defect is JS-leg only, and the
  14 reds above are the whole difference the fix closes. (Honesty: these
  tests are red on 2 of 3 legs before the fix, green from birth on the
  JVM leg — the BROKEN measurement IS the asymmetry.) (/tmp/vbd-broken-jvm.out)

## Measured (exact counts) — GREEN, after the fix

Counters identical on all three legs: **706 tests / 5702 assertions /
0 failures / 0 errors**, KBB_EXIT=0 / JVM_EXIT=0 / NBB_EXIT=0 (38/38
namespaces, TESTS-ON-NBB: pass) — same-count identity with the BROKEN
counters 3/3. (/tmp/vbd-green-{kbb,jvm,nbb}.out)

Probe re-run post-fix (kbb): `int-like?` BigInt → true (bounds untouched:
2^60 is still out of every door); the BigInt order is sent exactly like
its long twin; 2^60 refuses `:qty-out-of-range`/`:price-out-of-range`
(VALUE bounds); `string-of` 2^60 → nil (a verdict, not a throw);
`check-entry` with BigInt digits binds the market. Every answer equals the
JVM's pre-fix answer. (/tmp/vbd-probe-kbb-green.out)

## What landed

1. src/torihiki/venue.cljk: private `bigint-like?` — TYPE IDENTITY
   detector, verbatim `torihiki.log`/`torihiki.api`'s (the leaf cannot
   require them; `(instance? js/BigInt v)` is dead on SCI, measured
   2026-11-06); `int-like?` accepts a true BigInt — door symmetry with
   `torihiki.api/int-like?`; public `long-of` — the long of an in-bounds
   whole value, routing the measured `(long v)` SCI-BigInt throw through
   `js/Number` (exact inside i53), used by `string-of` and `point-of`.
2. src/torihiki/venue_mt.cljk: the two `int-in?`-then-`(long v)` stores
   (frame seq, sym digits) go through `v/long-of` — the door's totality:
   a value the bounded door admits can never halt the store behind it.
3. test/torihiki/venue_test.cljk: 4 deftests / 11 assertions;
   test/torihiki/venue_mt_test.cljk: 3 deftests / 9 assertions —
   overflow_gates pattern (REASON literals for refusals, VALUE pins for
   the accepted side), one `is` per line, identical counters on all three
   legs by construction (BigInt construct is `#?(:clj (bigint …) :cljs
   (js/BigInt …))`).

Op-hazard measurements riding in the probe (the fix routes around what
throws on SCI): `rem`/`quot`/`*` (mixed) and `long` THROW on a js/BigInt;
`pos?`/`neg?`/unary `-`/`<=` (mixed) work. The fix touches only `long`
sites; the magnitude guard (`abs*` + `<=`) is safe as written.

"not run" != "passed": parity / fuzz / bench legs were not run this tick
(the landing gate is the suite, green on all three legs with the new
tests — measured above; the change is confined to the venue layer's
integer door and appends no reason code, so no existing root or digest is
touched). maturity.md untouched (measurement bots' file).

## Left (carried)

- None named. The carry chain from PR #109 → #112 is closed with this
  package; the next tick opens from maturity.md NEXT or a new measured
  finding.
