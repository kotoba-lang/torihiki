# 2026-10-27 06:25 torihiki-fix tick — LANDED: tier-shape gate (complete schedules only)

Package: the 2026-10-26 20:27 designed package
(`evidence/2026-10-26-2027-torihiki-fix-tier-shape-door-notrun.md`), which measured the
door: `:margin-tiers` / `:fee-tiers` entries were stored RAW by `:list-market` /
`:amend-market` and only range-checked — completeness never checked, so a tier in the
shape `cl/market` itself documents (`{:max-notional n :max-leverage l}` — rates derived
at genesis but NOT at listing) passed validate and the readers threw mid-apply inside
apply-block: `tier-for` `(<= notional nil)`, `rates-for` → `mul-rate`'s `check` on nil /
`initial-margin-rate-for`'s `(max nil …)`, `fee-rates-for` `(<= nil v)`. One bridge-signed
`:list-market`, every validator halts at the same place (falsify-6/7 class). Also:
negative margin rates passed `rates-ok?`'s symmetric ±100% range — a negative requirement
is free leverage, solvency-breaking though deterministic.

Branch `bot/torihiki-fix-20261027-0625` off origin/main `e3f31c0` (PR #71), worktree
`~/github/wt/fleet/torihiki-fix-tier-shape` (the fleet worktree path was stable this run).

## What landed

- `torihiki.clearing/margin-tiers-ok?` / `fee-tiers-ok?` — the shape the readers deref:
  every margin tier a map with `:max-notional` (int 0..i53-max) and BOTH margin rates
  (int 0..rate-scale); every fee tier a map with `:min-volume` (int 0..i53-max), tier
  rates optional but ranged when present (fees ±rate-scale, rebates still legal). nil /
  empty vector is the flat schedule. Ranges as well as presence: a non-integer
  `:max-notional` compares fine and stores badly.
- `torihiki.api`: `rate-keys` split into `fee-rate-keys` (±rate-scale — a rebate is a fee
  with a sign) and `margin-rate-keys` (0..rate-scale — a negative requirement is not a
  requirement); `rates-ok?` refuses a negative margin rate with `:rate-too-high`
  wherever it nests. Tier predicates wired into `:list-market` / `:amend-market` as
  `:missing-field`, consulted AFTER `rates-ok?` so a bad rate reports `:rate-too-high`
  and an incomplete tier reports `:missing-field` — one fault, one reason.
- `torihiki.state`: no-op safety nets in `apply-tx :list-market` and `:amend-market`
  (mirroring the `book-opts-ok?` door), so a direct `apply-tx` caller stores nothing —
  the malformed schedule can never be read back as a throw mid-apply.

Note on the genesis shape: `{:max-notional n :max-leverage l}` (rates derived by
`cl/market`) remains a GENESIS shape and is refused at listing with `:missing-field`.
At listing the rates are stated, not derived — a derivation at the door would silently
pick a floor division the operator never saw, and the stored state would differ from
the signed transaction.

## Measured

- **BROKEN first** (tests added, gates unmigrated; kbb leg): `kbb -M:test` = 544 tests /
  3680 assertions / **9 failures** / 0 errors, KBB_EXIT=1 (/tmp/tf-tier-broken2-kbb.out).
  The 9 failures are exactly the designed RED pins: 5 REASON pins (`:missing-field` —
  the documented tier shape, missing `:maintenance-margin-rate`, missing `:max-notional`,
  missing `:min-volume`, `:margin-tiers 5`), 2 REASON pins (`:rate-too-high` — negative
  tier margin rate, negative top-level `:maintenance-margin-rate`), 2 engine-net VALUE
  pins (state-identical no-op for malformed `:list-market` and `:amend-market` — each was
  a store before). An earlier BROKEN draft (8F+1E, /tmp/tf-tier-broken-kbb.out) errored
  on a fixture artifact (fake book map in `om/rebuild`); the fixture now carries a real
  book and the RED is a clean value mismatch. Green-from-birth invariant pins are
  labelled as such in the test file (complete tiers pass; rebate/zero boundaries).
- **GREEN** after the fix — all three legs, identical counts:
  - SCI leg `kbb -M:test` = **544 tests / 3680 assertions / 0 failures / 0 errors,
    KBB_EXIT=0** (/tmp/tf-tier-green-kbb.out)
  - JVM leg `script/jvm-test.sh` (renamed-copy recipe) = **544 / 3680 / 0F / 0E,
    JVM_EXIT=0** (/tmp/tf-tier-green-jvm.out) — same-count cross-runtime claim.
  - nbb leg `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh`
    = **544 / 3680 / 0F / 0E, NBB_EXIT=0**, `TESTS-ON-NBB: pass` (/tmp/tf-tier-green-nbb.out)
    — the runtime that ships. (The worktree path breaks the `<repo>/../<name>` checkout
    default; KOTOBA_CHECKOUTS is required from `~/github/wt/fleet/*`.)
  - Baseline at PR #71 was 541 tests / 3663 assertions (evidence
    2026-10-27-0418-fix-tick-fdiv-diff-landed.md): +3 tests / +17 assertions.
  - A JVM BROKEN leg was not run (the RED record is the kbb leg).

## What is left (queue)

- maturity.md NEXT's fix packages ②–⑤ are all landed on current main (PRs #52–#72); the
  tier-shape door above was the last open item the fix-tick queue carried. NEXT in
  maturity.md is stale relative to origin/main — the measurement bots re-measure.
- Parity legs not run (measurement bots' gates).

Discovered/left by torihiki-fix tick 20261027-0625; maturity.md scores untouched
(measurement bots' file).
