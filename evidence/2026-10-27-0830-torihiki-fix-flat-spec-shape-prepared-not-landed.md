# 2026-10-27 08:2x torihiki-fix tick — NOT LANDED (run budget exhausted); package prepared + BROKEN measured: flat spec-shape gate

- Host load gate PASSED (08:17 pre-run 13.08 / 11.00 / 12.57, all windows < 20).
- Worktree `~/github/wt/fleet/torihiki-fix-0820`, branch `bot/torihiki-fix-20261027-0821`
  off origin/main **c5095d3** (PR #72). The branch has NO commits — the package is
  UNCOMMITTED in the worktree (like bot/torihiki-fix-20261023-2023 was), for the next
  tick to pick up and finish (GREEN legs + commit + PR + merge).

## Baseline (measured, c5095d3)

`kbb -M:test` = **544 tests / 3680 assertions / 0 failures / 0 errors, KBB_EXIT=0**
(/tmp/tf0820-base-kbb.out).

## Finding (audit at c5095d3): the flat market spec beside the tier gate

PR #72 gated the SCHEDULES (`:margin-tiers`/`:fee-tiers`); the FLAT spec is judged only
by `spec-ok?`, which caps integers to the i53 domain and waves non-integers through
("judged where they are used"). They are used in two places that cannot judge them:

1. `encode-market-spec` (state.cljk) folds `:tick` `:lot` `:max-leverage`
   `:open-interest-cap` and every tier's `:max-leverage` RAW into the STATE ROOT via
   `enc-int` ("one i53 as 8 bytes"): a fraction folds to bytes no runtime reproduces,
   a string dies in `(neg? v)` — falsify-14's class in the authenticated encoding.
   A spot `:asset` is worse: it becomes a KEY in `:balances`/`:committed` and
   `encode-clearing-account` (state.cljk:2380) folds the keys the same way.
2. Two readers COMPARE them: `cl/set-leverage` `(> lev (:max-leverage market 1))` —
   a string = ClassCastException on the JVM, silent coercion on JS — one bridge-signed
   `:list-market`, then the first `:set-leverage` (falsify-6/7 class) — and
   `api/validate`'s open-interest-cap `>`, which runs INSIDE apply-block (state.cljk:1724).

Also measured in passing: `spec-ok?`'s `(integer? 4.5e17)` is TRUE on kbb (integral
double) and FALSE on the JVM (a Double) — the two runtimes answered the SAME
transaction differently at the door (one refused, one stored and would fork later).
The new gate's `integer?` rule refuses on both.

## Package (tests-first, in the worktree now)

- `test/torihiki/overflow_gates_test.cljk` +2 sections: `flat-spec-shape-gate-refuses-
  unstorable-values` (REASON pins `:missing-field` ×10 + VALUE invariant pins ×3) and
  `flat-spec-shape-gate-is-a-no-op-in-the-engine-too` (net VALUE pins ×2 + store pin).
- `src/torihiki/clearing.cljk`: `margin-tiers-ok?` also shapes a tier's optional
  `:max-leverage` (int 1..i53-max); new `flat-spec-ok?` — `:tick`/`:lot`/`:max-leverage`
  int 1..i53-max, `:open-interest-cap`/`:asset` int 0..i53-max, all optional.
- `src/torihiki/api.cljk`: `cl/flat-spec-ok?` wired into `:list-market` and
  `:amend-market` beside the tier checks (`:missing-field`, after `rates-ok?`).
- `src/torihiki/state.cljk`: same clause added to both apply-tx no-op nets.

## Measured: BROKEN first (kbb leg, tests added, gates wired but… )

**BROKEN run at the tests-only stage**: `kbb -M:test` = **546 tests / 3697 assertions /
11 failures / 0 errors, KBB_EXIT=1** (/tmp/tf0820-broken-kbb.out). The 11 failures are
exactly the designed RED pins: 9 REASON pins (`:max-leverage` "20"/2.5/0/amend -5;
`:open-interest-cap` amend "abc"; `:asset` "XAU"/7.5; tier `:max-leverage` 2.5; amend
`:tick` 2.5) + 2 engine-net pins (state-identical no-op for `:list-market` with
`:max-leverage "20"` and `:amend-market` with `:open-interest-cap "abc"` — each was a
store before). The 10th REASON pin (`:open-interest-cap 4.5e17`) was green on the kbb
leg (already `:missing-field` there via spec-ok?'s JS `integer?` quirk) — it is a
cross-runtime agreement pin, expected RED on a JVM BROKEN leg (not run).

## NOT RUN (budget)

- GREEN leg (kbb / script/jvm-test.sh / script/nbb-test.sh) — not run.
- JVM BROKEN leg — not run. Parity legs — not run.
- No commit, no push, no PR, no merge. Branch `bot/torihiki-fix-20261027-0821` and
  worktree `~/github/wt/fleet/torihiki-fix-0820` hold the uncommitted package.

## Next tick

Run GREEN on all three legs (kbb -M:test, script/jvm-test.sh, KOTOBA_CHECKOUTS=…
script/nbb-test.sh) — expected 546 tests / 3697 assertions 0F/0E on each; fix any
pin I mispredicted (the 4.5e17 pin's leg asymmetry is documented above), then
commit + PR + merge + worktree removal. maturity.md scores untouched (measurement
bots' file). Queue note: maturity.md NEXT ②–⑤ remain landed (PRs #52–#72); this
package is the next door found by audit, same class as PR #72.
