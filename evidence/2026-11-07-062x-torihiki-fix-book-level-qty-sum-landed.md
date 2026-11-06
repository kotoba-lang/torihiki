# 2026-11-07 06:2x torihiki-fix tick — book level sums: the level-qty
# accumulator folds through fx/acc. LANDED.

Load gate: torihiki_state.sh absent at ~/.hermes/scripts/ (chronic-fault
precedent); uptime direct 06:17 — 1.34 / 1.43 / 1.49, all windows < 20,
gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261107-0621, branch
bot/torihiki-fix-20261107-0621, off origin/main 6c4f9ea (PR #113 merge).

Package = a NEW measured finding (the named carry chain closed with PR #113;
maturity.md NEXT is stale against the tree — its fix ② landed as PRs #52-#77 +
the sums/totality series, its reproducibility items landed 2026-10-19..10-24).
Surveyed the in-scope surface for an un-swept accumulator: `torihiki.book`'s
`lvl-qty` — the per-level resting-quantity sum — was the one root-feeding fold
the 2026-10-26 raw-sums/weight-sums series never reached. It is accumulated
with raw `slab/add!` (a JVM `long[]` add / a JS `Float64Array` add) and read
back by `encode-book` (state.cljk:2128 folds `bk/level-qty` into the STATE
ROOT preimage) and by `impact-price` (the mark/funding decision input).

Finding (measured, evidence/probe-book-lvlqty.cljk + the BROKEN legs): two
honest resting orders at ONE level — each passing every door, the per-order
notional cap included (`level x qty <= i53-max` is per-order and says nothing
about the level SUM) — take `lvl-qty` past 2^53 with no single write wrong:
qty1 = i53-max - 1 and qty2 = 3 at (bid, level 1) sum to 2^53 + 1. The JVM's
`long[]` holds 9007199254740993 exactly; the JS legs round to
9007199254740992 (Float64Array). One transaction list, one book, two values —
and the value is folded into the state root: one root per runtime, no throw
anywhere (falsify-8's mechanism, one layer down, through fully validated
traffic). The slab's own contract ("torihiki.fixed/check keeps every stored
value inside that shared domain") was broken at the store.

## Measured (exact counts) — BROKEN, fix withheld (tests present)

Counters identical on all three legs: **707 tests / 5708 assertions**
(706/5702 baseline [PR #113 landing record] + 1 deftest / 6 assertions).

- kbb `kbb -M:test`: **4F / 0E**, KBB_EXIT=1 (evidence/lq-broken-kbb.out).
  Exactly the designed VALUE pins, with the JS actuals:
  9007199254740992 (ceiling), in-domain? false, 9007199254740989 (decrement),
  9007199254740987 (crossing fill).
- JVM `script/jvm-test.sh`: **4F / 0E**, JVM_EXIT=1 (evidence/lq-broken-jvm.out).
  The same four pins with the JVM actuals: **9007199254740993** (the exact
  long), 9007199254740990, 9007199254740988.
- nbb `KOTOBA_CHECKOUTS=… script/nbb-test.sh`: **4F / 0E**, NBB_EXIT=1
  (evidence/lq-broken-nbb.out). Same values as kbb.

**The failure-count shape is uniform (4/4/4) and the ACTUAL VALUES are the
fork**: the same pin reads 9007199254740993 on the JVM and 9007199254740992
on both JS legs — one level sum, two runtime answers, carried into the state
root by encode-book. The decrement pins show the rounded residue OUTLIVING
the cancel (JS ...989 vs JVM ...990 after removing the same 3 lots).

## Measured (exact counts) — GREEN, after the fix

Counters identical on all three legs: **707 tests / 5708 assertions /
0 failures / 0 errors**, KBB_EXIT=0 / JVM_EXIT=0 / NBB_EXIT=0 — same-count
identity with the BROKEN counters 3/3. (evidence/lq-green-{kbb,jvm,nbb}.out)

Parity, both legs, byte-identical (diff empty, both exit 0): FLAT ROOT
b4322bedd406112e6… / STATE ROOT d1ebb9d30cd51516… / PROOF a 10 verifies true
— the canonical roots, unchanged: below the ceiling nothing moved.
(evidence/lq-parity-{jvm,nbb}.out)

Probe re-run post-fix (evidence/probe-book-lvlqty-kbb-green.out): every
level-qty answer is the sated fold 9007199254740991, in-domain, and the
cancel folds to 9007199254740988 — the same value the JVM pins read.

## What landed

1. src/torihiki/book.cljk: all four `lvl-qty` mutation sites (place!'s
   crossing decrement, place!'s rest increment, cancel!'s decrement,
   reduce-to!'s decrement) fold through `torihiki.fixed/acc` — the saturating
   accumulator, the same clamp as every aggregate in the sums series. Below
   the ceiling folding is byte-identical to before (pinned); past it the
   accumulator reads a SATED value (stated precision loss), which is one
   value on every runtime and stays inside the slab's domain. Decrement
   sites go through the accumulator too: that is where the rounded residue
   used to outlive a cancel and keep the runtimes apart.
2. test/torihiki/overflow_gates_test.cljk: 1 deftest / 6 assertions,
   overflow_gates pattern (VALUE pins for the clamp, the in-domain pin for
   the root preimage, below-ceiling regression pins, the crossing-fill pin).

Not scoped here (argument in the record, not a measurement):
`impact-price`'s local `filled` can reach i53-max + impact-notional only via
a level-0 full-level take, and only the returned quotient escapes the
function (a tiny floor quotient, same on both runtimes); left if a probe
ever reproduces a decision difference.

"not run" != "passed": fuzz digest and bench legs were not run this tick (the
landing gate is the suite, green on all three legs with the new tests —
measured above — plus parity measured identical; the change clamps one
accumulator and touches no wire encoding). maturity.md untouched (measurement
bots' file).

## Left (carried)

- None named. The next tick opens from maturity.md NEXT or a new measured
  finding.
