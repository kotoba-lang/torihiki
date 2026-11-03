# 2026-11-03 02:22 torihiki-fix tick — NOT LANDED (run budget exhausted during
# reconnaissance). Package scoped from direct code reads; NO probe, NO suite,
# NO BROKEN run executed — "not run" != "passed". Everything below marked
# [code-read] is predicted from source + language semantics, not runtime-measured.

Load gate: torihiki_state.sh 02:17 — 9.66 / 13.68 / 13.39, all windows < 20,
gate passed. Worktree ~/github/wt/fleet/bot-torihiki-fix-20261103-0222, branch
bot/torihiki-fix-20261103-0222 off origin/main bf36fee (PR #88 merge,
integral-number canon). KEPT as handoff — next tick starts here.
maturity.md untouched (measurement bots' file).

## Package chosen: wire-number-canon — every real number a transaction can
carry has ONE canonical wire form, identical bytes on both runtimes

PR #88 ("an in-bounds integral double IS its integer, at every door on both
runtimes") fixed the DOORS and coerced in-domain int-like doubles in
enc-val/signing-payload. The residual is the number space OUTSIDE the i53
domain — the same type-vs-value split the canon killed, still alive at the
log door. Direct code reads at bf36fee (src/torihiki/log.cljk:78-113,
:197-215; src/torihiki/api.cljk:75-90; test/torihiki/log_test.cljk:82-100):

### Gap 1 (asymmetric commit halt) [code-read]: out-of-domain whole doubles

enc-val's numeric ladder is `(int-like? v) + in-domain? -> (long v)`, then
`(integer? v) v` (bare), then tagged types, else THROW "no wire form".
For v = 1e17 (whole, out-of-domain — a legal NAME the doors deliberately
admit: "the :id and :claim sites devigate to int-like? (NOT bounded), a name
is a name", PR #88): JVM `(integer? 1e17)` is a TYPE test -> false -> falls
through every branch -> throws out of commit-block!/block->wire; nbb
`(integer? 1e17)` is a VALUE test (Number.isInteger) -> true -> encodes
bare. One signed block containing `{:tx :withdraw-invalidate-attest :claim
1e17 ...}` (or any out-of-domain whole double in any field — the codec
encodes EVERYTHING submitted) halts the JVM/SCI sequencer at the wire while
nbb commits it. falsify-6/7 class, one layer up. The round-trip law in the
codec's own docstring ("= tx (wire->tx (tx->wire tx)) for every transaction,
including the ones the engine will reject") is broken on the JVM leg.

### Gap 2 (symmetric commit halt) [code-read]: fractional doubles

v = 5.5 fails int-like? and `(integer? v)` on BOTH runtimes -> throw. But
5.5 is EDN-shaped and the engine answers it gracefully (`:bad-amount`,
recorded `:rejected` into the root — log_test's own
"a-malformed-tx-is-rejected-into-the-root-not-thrown" pin). commit-block!
calls block->wire AFTER apply-block, so a block the engine handled cannot be
committed: one submitted `{:tx :deposit :amount 5.5}` halts the log on every
runtime. The pinned loud refusal
("wire-rejects-what-it-cannot-validate") is correct only for non-EDN values
(the pin's `(fn [x] x)`); the docstring's "everything EDN-shaped round-trips"
is currently false for every non-integral double (and for every out-of-domain
whole double on the JVM leg).

### Gap 3 (signing-payload divergence) [code-read]

signing-payload (log.cljk:197-215) prints `(name (:tx t))` and ten numeric
slots via `str`: `{:account 1e17}` renders "1.0E17" on the JVM and
"100000000000000000" on nbb — the same block's canonical bytes-as-string
differs by runtime, so a signature produced on one runtime cannot verify
against the other's payload computation. Also `(name (:tx t))` throws on a
non-keyword/string kind (`{:tx 5}`) — the same reject-not-throw contract
broken on the sign path.

### Verified NOT a gap (do not re-litigate)

State-root name keys cannot reach the encoders raw: every admitted "name"
(:claim, cancel ids) is looked up before storage (`(nil? w) ex` / `(nil? c)
ex` in :withdraw-attest/:withdraw-invalidate-attest), and :trigger/:twap ids
are engine-minted. The `(integer? k)`/`(str k)`/`pad-id` sites in
validator-leaves* (09:06/09:10) therefore see only engine-minted or bounded
keys. int-like? excludes NaN/infinities (its rem/== test), so the int-like
branches never see them.

## Scoped design (ready to implement next tick, TDD BROKEN-first)

log.cljk only. A canonical number rendering shared by enc-val, dec-val and
signing-payload:

- int-like + in-domain -> bare long (UNCHANGED — old logs keep decoding).
- int-like otherwise (whole finite double; Long/BigInt; js/BigInt) -> tagged
  `["u" <exact-decimal-digits>]`: JVM `(str (.toBigIntegerExact (BigDecimal.
  (double v))))` for doubles (the BigDecimal(double) CONSTRUCTOR, exact —
  `bigdec` is valueOf/shortest and disagrees with JS), `(str v)` for
  integer types; nbb `(.toString (js/BigInt v))`. Decode: JVM `(bigint s)`,
  nbb `(js/Number (js/BigInt s))` — both `=`-equal to the submitted value
  (the exact-double digit string round-trips through Number exactly).
- fractional finite double -> tagged `["d" <exact-decimal-expansion>]`:
  JVM `(.toPlainString (BigDecimal. (double v)))` (exact, plain — toString
  goes scientific at exponents <-6); nbb via BigInt binary decomposition
  v = m*2^e (DataView/BigUint64Array bit extract, m odd canonical) then
  m*5^(-e) over 10^(-e) as a plain digit string with the point placed. Both
  compute the same exact expansion. Decode Double/parseDouble /
  js/parseFloat (nearest-double parse returns the original exactly).
- +/-Inf -> `["d" "Infinity"]` / `["d" "-Infinity"]`; NaN stays a loud
  symmetric refusal (no wire form — `(= ##NaN ##NaN)` is false so the
  round-trip law cannot hold; pinned as refused).
- signing-payload: render kind via a total helper (keyword -> name, string
  as-is, anything else through the number canon / str fallback) and the ten
  slots through the same canon — same bytes on both runtimes for any
  submitted garbage.
- decode tags "u"/"d" in dec-val; wire->tx round-trip pins ride along.

Tests (log_test.cljk, VALUE + wire-bytes pins): the literal wire form of
`{:claim 1e17}` (["u" "100000000000000000"]) and of 5.5 (["d" "5.5"]);
round-trip `=` for 1e17 / -2.5e18 / 5.5 / 0.1 / -0.0 / 1e-300 / 1.5e300;
in-domain 5.0 stays bare 5 (over-tightening guard); the signing-payload
literal for a tx with :account 1e17; NaN refused; the existing
no-wire-form fn pin unchanged. Each new pin run BROKEN first (Gaps 1-3 red
on the legs where the throw/divergence fires: Gap 1 red on kbb+JVM legs,
Gap 2 red on all three, Gap 3 red wherever str diverges — record the actual
observed red mode per leg, do not predict it).

## Not run / not claimed

Baseline suite (expected 600 tests / 5216 assertions per PR #88's green),
BROKEN runs, and all three legs: NOT RUN (budget). No PR, no src change.
Branch kept for handoff.
