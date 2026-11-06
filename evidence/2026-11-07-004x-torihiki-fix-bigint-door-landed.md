# 2026-11-07 00:4x torihiki-fix tick — api/int-like? BigInt door: LANDED

Load gate: torihiki_state.sh absent at ~/.hermes/scripts/ (chronic-fault
precedent); uptime direct 00:18 — 1.90 / 1.69 / 1.66, all windows < 20,
gate passed.

Package = the last named carry (evidence/2026-11-06-162x-…-key-collision-
canon-landed.md "Left (carried)" + evidence/2026-11-06-222x-…-canon-
collision-door-landed.md "the only PR #109 carry left"):
**api/int-like? BigInt door asymmetry — validate refuses a BigInt
:bad-quantity on JS while the engine door now accepts it**, and with it the
validate/wire domain alignment the 182x scoping note deferred to this carry.

## Measured (exact counts) — the asymmetry, before the fix

Probe both runtimes (evidence/probe-bigint-door.cljk; kbb directly,
`clojure -M` over the jvm-leg renamed copy) — pre-fix answers:

| probe | kbb/SCI | JVM |
|---|---|---|
| `int-like?` BigInt 5 | **false** | true (`(integer? 5N)` is true) |
| `validate` :deposit amount BigInt 10 | **:bad-amount** | **nil (admitted)** |
| `validate` :order qty BigInt 5 | **:bad-quantity** | **nil (admitted)** |
| `canon-tx {:qty BigInt 5}` | `{:qty #object[BigInt 5]}` (untouched) | `{:qty 5}` (already the long) |
| `log/tx->wire` :deposit amount BigInt 10 | accepted (bare 10) | accepted |
| `validate` `{5 :a, BigInt5 :b}` (2 entries) | **nil** | (collapses at construction) |
| `log/tx->wire` same map | **THROW: map keys collide under the wire canon** | — |
| `validate` `{1e17 :a, BigInt-1e17 :b}` | **nil** | **nil** |
| `log/tx->wire` same map | **THROW: map keys collide** | **THROW: map keys collide** |

One transaction, two verdicts at validate (JS refuses what the JVM admits),
and validate passing exactly the pairs the wire refuses — a signed tx away
from an apply-path throw. `long` THROWS on a SCI BigInt (measured); `(= 5
BigInt5)` is false while `(= BigInt5 BigInt5)` is true (measured).

## Measured (exact counts) — BROKEN, fix withheld (tests present)

Counters identical on all three legs: **694 tests / 5661 assertions**
(688/5648 baseline [PR #110 landing record] + 6 deftests / 13 assertions).

- kbb `kbb -M:test`: **12F / 0E**, KBB_EXIT=1 — exactly the designed reds:
  5 flipped canon-collision pins (map keys ×3, set, liveness rejection —
  BigInt twins now designed to collide), 2 door-symmetry VALUE pins, 1 canon
  VALUE pin, 2 numeric-twin (out-of-i53) REASON pins, liveness reasons +
  collateral VALUE pins. (/tmp/bd-broken-kbb2.out)
- JVM `script/jvm-test.sh`: **2F / 0E**, JVM_EXIT=1 — exactly the designed
  JVM reds: the two out-of-i53 numeric-twin pins (the BigInt door already
  works on the JVM — `(integer? 5N)` is true — so only the wire-alignment
  class is red there). (/tmp/bd-broken-jvm2.out)
- nbb `KOTOBA_CHECKOUTS=… script/nbb-test.sh`: **12F / 0E**, NBB_EXIT=1 —
  same designed reds as kbb. (/tmp/bd-broken-nbb.out)

Honesty note: an earlier BROKEN run (kbb 12F / JVM 3F) carried a liveness
VALUE pin that missed the taker fee (measured actual 100000009, not
100000010 — the block crossed orders, floor(5005×350000/1e9)=1). The test
was restructured fee-free (deposits only) and RE-MEASURED BROKEN above
before the fix — the numbers above are the final measured reds.

## Measured (exact counts) — GREEN, after the fix

Counters identical on all three legs: **694 tests / 5661 assertions /
0 failures / 0 errors**, KBB_EXIT=0 / JVM_EXIT=0 / NBB_EXIT=0 — same-count
identity with the BROKEN counters 3/3. (/tmp/bd-green-{kbb,jvm,nbb}.out)

Probe re-run post-fix (kbb): `int-like?` BigInt → true; the BigInt
deposit/order validate → nil (the JVM's verdict, on every leg); out-of-
domain BigInt 2^60 → :bad-amount/:bad-quantity (bounds are value bounds);
`canon-tx` stores `{:qty 5}`; both numeric-twin maps → `:canon-collision`
at validate, aligned with the wire's refusal; `apply-block` applies the
in-domain BigInt deposit (rejected = []). (/tmp/bd-probe2-kbb.out)

## What landed (src/torihiki/api.cljk only + its tests)

1. private `bigint-like?` — type identity (`(instance? js/BigInt v)` is
   false on SCI, measured 2026-11-06), verbatim `torihiki.log`'s detector;
   the wire requires this namespace and cannot be required back.
2. `int-like?` accepts a true BigInt as an integer however typed — the door
   symmetry. Every bounded door (`int-in?`) and `account-ok?` inherits it.
3. `int-canon` — the long of an in-domain whole value; a BigInt converts
   through `js/Number` (`(long v)` throws on a SCI BigInt, measured), exact
   inside i53. `canon-val` stores through it: no BigInt reaches a slab.
4. `collision-form` + `exact-digits` — the collision door's identity made
   total over the values the wire judges: in-domain whole → its long (the
   canon's merge identity), out-of-domain whole → its exact "u" digits (the
   wire's identity; `torihiki.log/u-digits` semantics duplicated). The
   digits ride a symbol-carrying LIST (no wire form — a user value can never
   `=` one). `first-canon-collision` compares by it: the validate domain and
   the wire domain now refuse the same pairs, and no tx can pass validate
   and throw at the wire.
5. test/torihiki/api_test.cljk: 6 deftests / 13 assertions (overflow_gates
   pattern: REASON literals pinned for refusals, VALUE pins for the clean
   side, one `is` per line — identical counters on all three legs by
   construction) + the 5 BigInt-twin pins flipped from their pre-door
   answers (`#?(:clj :canon-collision :cljs nil)` → `:canon-collision`;
   liveness `[:canon-collision]` on every leg). The JVM-only constructs
   (bare-vs-bare twins) keep their measured per-runtime answers.

Out of this package (carried): (a) no-wire-form values (Ratio, BigDecimal,
NaN-in-a-value) still surface as the wire's loud refusal, not a validate
verdict — the wire-number? note calls that a value decision the canon has
not made, unchanged here; (b) `torihiki.venue/int-like?` keeps its own
re-implementation and does not accept a BigInt — the venue layer is a leaf
judging its own inputs; no path from validate carries a BigInt there (the
canon stores longs), noted for its own tick if one ever opens.

"not run" != "passed": parity / fuzz / bench legs were not run this tick
(the landing gate is the suite, green on all three legs with the new tests
— measured above). maturity.md untouched (measurement bots' file).
