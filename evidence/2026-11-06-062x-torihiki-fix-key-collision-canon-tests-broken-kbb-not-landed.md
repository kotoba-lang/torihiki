# 2026-11-06 06:2x torihiki-fix tick — key-collision canon: tests written +
# BROKEN-measured on kbb ONLY, NOT landed (run budget exhausted). "not run"
# != "passed": nothing is claimed green this tick; the nbb/JVM BROKEN legs
# and all three GREEN legs are NOT run. maturity.md untouched (measurement
# bots' file).

Load gate: torihiki_state.sh ABSENT (chronic-fault precedent); uptime direct
06:17 — 1.16 / 1.19 / 1.25, all windows < 20, gate passed.

Continuation of the KEPT handoff worktree ~/github/wt/fleet/
bot-torihiki-fix-20261106-0422 (branch bot/torihiki-fix-20261106-0422, tip
bb928e0 = the 04:2x evidence, off origin/main 324a9ec = PR #107 merge).
Branch KEPT again for the next tick. Env notes re-measured: JAVA_HOME=
/opt/homebrew/opt/openjdk (openjdk 27; bare `java` is the macOS stub —
"Unable to locate a Java Runtime"), PATH needs /Users/junkawasaki/github/
kotoba-lang/kotoba/bin for kbb.

## Queue check

Unchanged from the 04:2x record: maturity.md NEXT is STALE (fully landed);
queue head is PR #107's carry-forward — "Map keys that collide under the
canon (5 vs 5.0 → both bare 5) break the round-trip law — refuse loudly per
the NaN precedent" — the 04:2x handoff's package.

## Implemented this tick: the tests (5 deftests / 13 assertions appended to
## test/torihiki/log_test.cljk), per the 04:2x handoff's test design

- collide-map / collide-set: runtime-conditional collision pairs (JVM
  5/5.0, SCI 5/(js/BigInt 5)), `into`-constructed, never literals.
- ex-msg helper pins REASON literals as VALUES (kotoba.test has no
  thrown-with-msg? — confirmed in its docstring; ex-message measured
  working on kbb/SCI).
- Refusal pins (9): enc nested map / enc tx-level keys / enc set → the
  "map keys collide under the wire canon" / "set elements collide under the
  wire canon" literals; wire same-form / cross-form (["u" "5"]) / re-encode
  (["d" "5.0"]) twins + crafted _pairs ["k" "tx"] kind-slot collision +
  flat/pairs "a" collision → "wire map keys collide"; ["set" [5 5]] →
  "wire set elements collide".
- Over-refusal guards (4): {5 :a "5" :b} round-trips; wire idempotence for
  {1 :a 2 :b} and #{1 2} wires; single-key {5.0 :a} keeps its wire.

## Measured (exact counts) — BROKEN, kbb leg ONLY

- kbb (`kbb -M:test`): **670 tests / 5608 assertions / 10 failures /
  0 errors**, KBB_EXIT=1 (/tmp/tori-kc-broken-kbb.{out,err,exit}). Baseline
  before this package = **665 / 5595** (derived: 670/5608 minus 5 deftests
  / 13 assertions — note the 04:2x record's 661/5582 baseline is stale; PR
  #107 moved it).
- 10 failures = 9 refusal pins red (as designed: nothing throws the pinned
  literal today) + 1 over-refusal guard red for a TEST SHAPE BUG (see
  below). NOT run: nbb (script/nbb-test.sh) and JVM (script/jvm-test.sh)
  BROKEN legs — the 04:2x handoff asked for BROKEN on all three legs; only
  kbb was reached before budget.

## Test shape bug found and corrected in this commit

The {5 :a "5" :b} guard compared `m` against the whole round-tripped TX
instead of its :flags — red for the wrong reason (the measured output shows
the round trip preserved both entries). Corrected to (:flags ...). The
BROKEN counts above are against the pre-correction file (10F); with the
correction the kbb BROKEN count is 9F (all 9 refusal pins). The next tick
must re-run BROKEN on all three legs against this file before the fix.

## NEW FINDING (measured this tick): `(instance? js/BigInt v)` is FALSE on
## SCI — a js/BigInt value has no wire form on the JS legs

The enc-side pins came back with the WRONG literal on kbb:
"torihiki.log: value has no wire form" instead of a silent fold. Root
cause measured by probe (evidence/probe-key-collision-canon.cljk, run
`kbb <probe>`): `(instance? js/BigInt (js/BigInt 5))` = **false** on
kbb/SCI. enc-val's branch 2 (`#?(:cljs (instance? js/BigInt v) ...)`),
slot-str's twin, and the handoff's "enc-val's instance? js/BigInt branch is
what encodes it" inference are all dead on the JS legs: a js/BigInt —
however whole — falls to `:else` and is refused "value has no wire form",
while the JVM encodes the same transaction bare (in-domain) or ["u"
digits] (out-of-domain). One transaction, two verdicts — falsify-6/7 class
at the codec. It ALSO blocks the key-collision criterion at the enc door on
the JS legs: the criterion's canon form is `(dec-val (enc-val k))`, which
throws on a BigInt key before the collision comparison can run. So the
detector fix is a PREREQUISITE of this package's enc side.

Probe ground truth also measured this tick (probe-key-collision-canon.cljk):
kbb reads .cljk with the **:cljs** reader feature; `(into {} [[5 :a]
[5.0 :b]])` = `{5 :b}` (JS merges 5/5.0 — the :clj pair is useless on SCI);
`(into {} [[5 :a] [(js/BigInt 5) :b]])` keeps 2 entries (the SCI pair);
the SCI READER rejects `{5 :a 5.0 :b}` and `#{5 5.0}` literals with "Map/
Set literal contains duplicate key: 5" **even inside a SKIPPED
reader-conditional branch** (the first kbb run this tick died at READ
time on the map literal) — collision pairs must be `into`-constructed.
`js*` is NOT available on SCI ("Unable to resolve symbol: js*"), so a
typeof-based detector is out; unprobed candidates for the fix:
`(identical? (type v) js/BigInt)` / `(= v (js/BigInt v))` in try. Also
recorded in the wire-number? docstring: `(long v)` throws on a SCI
BigInt — the bare in-domain form needs a safe conversion (js/Number or
equivalent) if wire-form parity with the JVM (in-domain BigInt → bare) is
tohold.

## What is NOT done (next tick, in this order)

1. Portable BigInt detector in torihiki.log (wire-number? / enc-val
   branch 1+2 / slot-str sites), wire-form parity: in-domain BigInt →
   bare long as on the JVM, out-of-domain → ["u" exact digits. Then the
   collision criterion computes at the enc door on all legs.
2. Re-run BROKEN on all three legs (kbb -M:test, script/nbb-test.sh with
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang,
   script/jvm-test.sh) with the corrected pins — kbb expectation 9F.
3. Implement the 6-site collision fix exactly as designed in the 04:2x
   handoff (criterion `(= (dec-val (enc-val a)) (dec-val (enc-val b)))`,
   `(declare dec-val)`, 4 REASON literals, lazy pairwise so a singleton
   NaN key still decodes as today).
4. GREEN x3 with identical counters (expect 670/5621 with the corrected
   pin — recompute from the actual test file), then land: commit, push,
   PR, merge.

Carried findings (separate packages): api/canon-val silent merge (04:2x);
api/int-like? BigInt door asymmetry — the JVM door accepts an in-domain
BigInt (integer? is a TYPE test that covers it) while the JS door refuses
:bad-quantity (integer?/number? are false for BigInt) — same class as the
venue-integer-canon finding, one later tick.

Not run / not claimed: the fix, nbb+JVM BROKEN legs, all GREEN legs, the
same-count claim, and the landing (PR/merge): NOT RUN this tick.
