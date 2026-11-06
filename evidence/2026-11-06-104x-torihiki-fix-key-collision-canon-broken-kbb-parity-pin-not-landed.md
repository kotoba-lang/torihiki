# 2026-11-06 10:4x torihiki-fix tick — key-collision canon: BigInt detector
# probe-validated + wire-form parity VALUE pin added + BROKEN measured on the
# kbb leg (9F+2E, exactly the designed reds), NOT landed (run budget
# exhausted with the JVM/nbb BROKEN legs still in flight). "not run" !=
# "passed": nothing is claimed green this tick except the probe and kbb
# BROKEN outputs quoted below. maturity.md untouched (measurement bots' file).

Load gate: torihiki_state.sh ABSENT (chronic-fault precedent); uptime direct
10:17 — 2.23 / 1.82 / 1.52, all windows < 20, gate passed.

Continuation of the KEPT handoff worktree ~/github/wt/fleet/
bot-torihiki-fix-20261106-0422 (branch bot/torihiki-fix-20261106-0422, tip
2127523 = the 062x tests+BROKEN-kbb commit, off origin/main 324a9ec = PR
#107 merge). Branch KEPT again for the next tick.

## Queue check

Unchanged from the 042x/062x records: maturity.md NEXT is STALE (fully
landed); queue head is PR #107's carry-forward — the key-collision canon
package (refuse loudly when two map keys / set elements share one decoded
canon form). The 062x record's "next tick, in this order" is this tick's
work plan.

## Probe ground truth (measured this tick — the 062x record's unprobed
## candidates, now decided)

JVM (clojure -M /tmp/tori-kc-probe1.clj, openjdk 27) and kbb/SCI
(kbb /tmp/tori-kc-probe2.cljk + /tmp/tori-kc-probe3.cljk with torihiki.fixed
/ torihiki.api on the project classpath):
- `(identical? (type (js/BigInt 5)) js/BigInt)` = **true on SCI** — the
  portable BigInt detector is DECIDED (the 062x record's first unprobed
  candidate works; `js*` is not needed).
- `(identical? (js/BigInt 5) (js/BigInt (js/BigInt 5)))` = true (fallback
  candidate also works, not needed).
- JVM `(= 5 (bigint 5))` = **true** (so the JVM round-trip law already holds
  for BigInt values); SCI `(= 5 (js/BigInt 5))` = **false** (as the 062x
  record measured) — the collision criterion must compare CANON forms
  (`(= (dec-val (enc-val a)) (dec-val (enc-val b)))`), never raw values.
- `(fx/in-domain? (js/BigInt 5))` = true / 2^53+1 = false / -5n = true on SCI
  (torihiki.fixed's `<=` bounds are BigInt-safe in JS semantics) — the
  in-domain BigInt -> bare-long parity path is implementable without a
  hand-rolled bounds check.
- `(api/int-like? (js/BigInt 5))` = false on SCI (the 062x finding holds) —
  the BigInt path must not depend on int-like?.
- `(js/Number (js/BigInt "9007199254740991"))` = 9007199254740991 exact;
  `(.toString (js/BigInt "-5"))` = "-5"; SCI `(long 5.0)` = 5.

## Implemented this tick: the wire-form parity VALUE pin (1 deftest /
## 2 assertions appended to test/torihiki/log_test.cljk)

`a-bigint-carries-exact-digits-under-the-number-canon`: an in-domain BigInt
encodes BARE byte-identical to the JVM long form; an out-of-domain BigInt
carries its exact digits under "u". This pins the 062x step-1 contract
(the detector + parity) as a VALUE claim, so the detector fix cannot land
unmeasured. Runtime-conditional values (#?(:clj (bigint 5) :cljs (js/BigInt
5))), wire-level comparison per the log_test convention.

## Measured (exact counts) — BROKEN, kbb leg

- kbb (`kbb -M:test`): **671 tests / 5610 assertions / 9 failures /
  2 errors**, KBB_EXIT=1 (/tmp/tori-kc-broken-kbb.{out,err}). Counters =
  062x baseline 665/5595 + the 062x 5 deftests / 13 assertions + this
  tick's 1 deftest / 2 assertions.
- Failure identities (measured, exactly the designed reds):
  9 refusal pins red = 3 enc-side pins answering the WRONG literal
  "torihiki.log: value has no wire form" (the dead BigInt detector — a
  BigInt key cannot even reach the collision comparison) + 6 dec-side pins
  answering nil (silent fold today). 2 errors = the new parity deftest
  (tx->wire throws "value has no wire form" on a BigInt on the JS legs).
  The corrected over-refusal guard from the 062x commit is green (its red
  was the pin shape bug) — 9F is the expected BROKEN count, reconfirmed.

## NOT run / not claimed

- BROKEN JVM leg (script/jvm-test.sh) and BROKEN nbb leg
  (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
  script/nbb-test.sh): LAUNCHED at 10:3x against this exact tree (tests
  present, fix absent) but STILL IN FLIGHT at budget exhaustion — their
  outputs land in /tmp/tori-kc-broken-jvm.out and /tmp/tori-kc-broken-nbb.out
  (JVM_EXIT/NBB_EXIT appended at exit). Expected: JVM 9F (the parity pin is
  green on the JVM leg — the JVM already has parity; the pin is red on the
  JS legs) and nbb 9F+2E; both MUST be read and counted by the next tick,
  not assumed.
- The fix (062x steps 1+3): NOT implemented — no src/ change this tick.

## What remains (next tick, in this order)

1. Implement the portable BigInt detector (decided this tick:
   `(identical? (type v) js/BigInt)`) at the 062x step-1 sites —
   wire-number?/enc-val branch 1+2/slot-str — with wire-form parity:
   in-domain BigInt -> bare (JVM `(long v)` / JS `(js/Number v)`, exact
   inside the domain), out-of-domain -> ["u" exact digits (u-digits already
   takes a BigInt on both legs))`.
2. Read/count the in-flight JVM+nbb BROKEN outputs (or re-run) — BROKEN is
   established on kbb already; every new pin is red before its fix.
3. Implement the 6-site collision fix exactly as designed in the 042x
   handoff (criterion `(= (dec-val (enc-val a)) (dec-val (enc-val b)))`,
   `(declare dec-val)`, 4 REASON literals, pairwise/lazy so a singleton NaN
   key still decodes as today — canon form computed with a fresh token on
   failure, so values with no wire form collide with nothing).
4. GREEN x3 with identical counters on kbb -M:test / script/jvm-test.sh /
   script/nbb-test.sh (expected 671 tests / 5610 assertions — recompute
   from the test file), then land: commit, push, PR, merge.

Carried findings (separate packages, unchanged): api/canon-val silent merge
(042x); api/int-like? BigInt door asymmetry (062x).

Not run / not claimed: the fix, the JVM/nbb BROKEN counts, all GREEN legs,
the same-count claim, and the landing (PR/merge): NOT RUN this tick.
