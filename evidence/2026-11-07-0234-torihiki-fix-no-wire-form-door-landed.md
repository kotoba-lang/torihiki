# 2026-11-07 02:3x torihiki-fix tick — no-wire-form door: the wire's loud
# refusal is a validate verdict. LANDED.

Load gate: torihiki_state.sh absent at ~/.hermes/scripts/ (chronic-fault
precedent); uptime direct 02:17 — 2.81 / 2.49 / 2.01, all windows < 20,
gate passed.

Worktree ~/github/wt/fleet/bot/torihiki-fix-20261107-0219, branch
bot/torihiki-fix-20261107-0219, off origin/main 2bc7a6b (PR #111 merge).

Package = the PR #111 carry, "Out of this package (carried) (a)":
**no-wire-form values (Ratio, BigDecimal, NaN-in-a-value) still surface as
the wire's loud refusal, not a validate verdict — the wire-number? note
calls that a value decision the canon has not made.** Measured gap: a
transaction carrying a value `torihiki.log/enc-val` can never encode — NaN,
a function, a symbol, a list, and on the JVM a Ratio or a BigDecimal
outside the canon's routes — passed `api/validate` (the shape gates judge
their own slots only) and threw `"value has no wire form"` AT THE WIRE: one
signed transaction from a halted commit. The pre-fix apply-path probe is
pinned in the BROKEN run below: the fn-carrying deposit was APPLIED
(collateral 100000005, not the refused 100000000).

## Measured (exact counts) — BROKEN, fix withheld (tests present)

Counters identical on all three legs: **699 tests / 5682 assertions**
(694/5661 baseline [PR #111 landing record] + 5 deftests / 22 assertions,
minus the 1 NaN VALUE pin moved out of clean-collections-still-validate).

- kbb `kbb -M:test`: **12F / 0E**, KBB_EXIT=1 — exactly the designed reds:
  4 portable REASON pins (NaN, fn, symbol, list), 4 depth pins (nested
  list, fn KEY, symbol set element, NaN key pair), the closed-set pin, and
  3 apply-path pins (reasons vector, index, refused-deposit-credits-
  nothing). (/tmp/nwf-broken-kbb.out)
- JVM `script/jvm-test.sh`: **15F / 0E**, JVM_EXIT=1 — the 12 above plus
  the 3 JVM-only constructs (Ratio `(/ 2 3)`, fractional BigDecimal,
  out-of-i53 whole BigDecimal — the int-like? trap). (/tmp/nwf-broken-jvm.out)
- nbb `KOTOBA_CHECKOUTS=… script/nbb-test.sh`: **12F / 0E**, NBB_EXIT=1 —
  same designed reds as kbb. (/tmp/nwf-broken-nbb.out)

The per-runtime pins are honest, not conditional tricks: `(/ 2 3)` is a
JVM Ratio (no wire form) and a fractional double on the JS legs (the
wire's "d" route) — the SAME source text, answered per runtime
(`#?(:clj :no-wire-form :cljs nil)`), one `is` per line, identical
counters on all three legs by construction.

## Measured (exact counts) — GREEN, after the fix

Counters identical on all three legs: **699 tests / 5682 assertions /
0 failures / 0 errors**, KBB_EXIT=0 / JVM_EXIT=0 / NBB_EXIT=0 (TESTS-ON-NBB:
pass) — same-count identity with the BROKEN counters 3/3.
(/tmp/nwf-green-{kbb,jvm,nbb}.out)

## What landed

1. src/torihiki/api.cljk: `:no-wire-form` joins `reasons`; private
   `wire-number?` + `wire-able?` (verbatim `torihiki.log/enc-val`'s
   acceptance, duplicated like `exact-digits` — the wire requires this
   namespace and cannot be required back) + `first-no-wire-form` (deep
   walk: map keys and values, set elements, vector items; `{:hit v}` /
   nil, total, no throw). `validate` refuses `:no-wire-form` AFTER the
   shape gates, on the CANONIZED transaction — exactly the values the wire
   will judge — so a shape refusal keeps its pinned reason and the canon's
   declared normalisations survive (an in-domain whole value however typed
   — 5.0, BigDecimal 5, BigInt 5 — becomes its long and passes; pinned
   green).
2. src/torihiki/state.cljk: `:no-wire-form 24` appended to `reason-codes`
   (append-only; every existing root unchanged).
3. test/torihiki/api_test.cljk: 5 deftests / 22 assertions in the
   overflow_gates pattern (REASON literals for refusals, VALUE pins for
   the clean side), + 1 pin flipped (the NaN key pair in
   clean-collections-still-validate: it collided with nothing and passed
   validate while the wire threw — moved to the new door as a REASON pin;
   it is refused `:no-wire-form`, NOT `:canon-collision`).

Door order note: collision door (raw tx) → shape gates → no-wire-form door
(canon). The two wire-alignment doors' domains are disjoint in reachable
outcomes (two no-wire-form values that would collide cannot coexist in one
map/set — `=`-equal keys merge at construction; NaN collides with
nothing), so no verdict depends on the order.

## Left (carried)

- PR #111 carry (b), unchanged here: `torihiki.venue/int-like?` keeps its
  own re-implementation and does not accept a BigInt — the venue layer is
  a leaf judging its own inputs; no path from validate carries a BigInt
  there (the canon stores longs).

"not run" != "passed": parity / fuzz / bench legs were not run this tick
(the landing gate is the suite, green on all three legs with the new
tests — measured above; the change appends a reason code and refuses a
class of transactions that previously threw at the wire, so no existing
root or digest is touched). maturity.md untouched (measurement bots'
file).
