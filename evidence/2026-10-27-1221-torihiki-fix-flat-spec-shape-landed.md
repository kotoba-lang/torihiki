# 2026-10-27 12:2x torihiki-fix tick — LANDED: flat spec-shape gate (re-lands the 08:2x prepared package with the GREEN legs that tick never got)

- Host load gate PASSED (12:18 direct uptime 7.76 / 10.74 / 10.76, all windows < 20;
  torihiki_state.sh pre-run 11.46 / 11.89 / 11.14).
- Worktree `~/github/wt/fleet/bot-torihiki-fix-20261027-1221`, branch
  `bot/torihiki-fix-20261027-1221` off origin/main **c5095d3** (PR #72).
- Provenance: the package was designed and BROKEN-measured by the 2026-10-27 08:2x
  tick (evidence/2026-10-27-0830-torihiki-fix-flat-spec-shape-prepared-not-landed.md,
  uncommitted in worktree `torihiki-fix-0820`, branch `bot/torihiki-fix-20261027-0821`).
  This tick ported the diff to a fresh worktree and re-ran every leg — the
  verification the preparing tick's budget cut. Same class as PR #65's re-landing.

## The door (audit at c5095d3)

PR #72 gated the tier SCHEDULES; the FLAT spec beside them was judged only by
`spec-ok?`, which caps integers to the i53 domain and waves non-integers through.
Two consumers cannot judge them:

1. `encode-market-spec` (state.cljk) folds `:tick` `:lot` `:max-leverage`
   `:open-interest-cap` and every tier's `:max-leverage` RAW into the STATE ROOT via
   `enc-int`; a spot `:asset` becomes a KEY in `:balances`/`:committed`, folded the
   same way by `encode-clearing-account` — falsify-14's class in the authenticated
   encoding (a fraction folds to bytes no runtime reproduces; a string dies in
   `(neg? v)`).
2. Two readers COMPARE them: `cl/set-leverage` `(> lev (:max-leverage market 1))` —
   a string is a ClassCastException on the JVM and a silent coercion on JS, one
   bridge-signed `:list-market` then the first `:set-leverage` (falsify-6/7 class) —
   and `api/validate`'s open-interest-cap `>`, which runs INSIDE apply-block.

Also measured in passing (kept in the pin): `spec-ok?`'s `(integer? 4.5e17)` is TRUE
on kbb (integral double) and FALSE on the JVM (a Double) — the two runtimes answered
the SAME transaction differently at the door. The new gate's `integer?` rule refuses
on both.

## What landed

- `clearing.cljk`: `margin-tiers-ok?` also shapes a tier's optional `:max-leverage`
  (int 1..i53-max); new `cl/flat-spec-ok?` — `:tick`/`:lot`/`:max-leverage` int
  1..i53-max, `:open-interest-cap`/`:asset` int 0..i53-max, all optional.
- `api.cljk`: `cl/flat-spec-ok?` wired into `:list-market` and `:amend-market` beside
  the tier checks (`:missing-field`, after `rates-ok?` — one fault, one reason).
- `state.cljk`: the same clause added to both `apply-tx` no-op safety nets (a direct
  caller meets a no-op, never a stored junk field).
- `test/torihiki/overflow_gates_test.cljk` +2 deftests / +17 assertions:
  `flat-spec-shape-gate-refuses-unstorable-values` (9 REASON pins `:missing-field` +
  3 VALUE invariant pins) and `flat-spec-shape-gate-is-a-no-op-in-the-engine-too`
  (2 net VALUE pins + store pins).

## Measured (this worktree, this tick)

**BROKEN first** (tests-only stage, src at c5095d3): `kbb -M:test` = **546 tests /
3697 assertions / 11 failures / 0 errors, KBB_EXIT=1** (/tmp/tf1221-broken-kbb.out) —
exactly the 11 designed RED pins (9 REASON + 2 engine-net), all inside the two new
deftests. The 10th REASON pin (`:open-interest-cap 4.5e17`) was green at BROKEN on
the kbb leg (already `:missing-field` there via spec-ok?'s JS `integer?` quirk) — it
is a cross-runtime agreement pin, expected RED on a JVM BROKEN leg (not run).

**GREEN**, identical counts on all three legs (/tmp/tf1221-green-{kbb,jvm,nbb}.out):

| leg | counts | exit |
|---|---|---|
| `kbb -M:test` (SCI) | 546 / 3697 / 0F / 0E | 0 |
| `bash script/jvm-test.sh` (renamed-copy JVM leg) | 546 / 3697 / 0F / 0E | 0 |
| `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang bash script/nbb-test.sh` | 546 / 3697 / 0F / 0E | 0 |

Baseline at PR #72 was 544 / 3680. NOT RUN: JVM BROKEN leg, parity legs (measurement
bots' gates). maturity.md untouched.
