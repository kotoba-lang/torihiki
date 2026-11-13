# 2026-10-26 20:27 torihiki-fix tick — NOT LANDED (budget exhausted mid-tick); open door found: tier-shape gate

- Host load gate PASSED (20:17 pre-run 5.91/6.77/15.77, all windows < 20).
- Worktree `bot/torihiki-fix-20261026-2027b` off origin/main **b0bfbd1** (PR #68) — scratch path
  (`~/.hermes/profiles/torihiki-fix/cache/scratch/wt-torihiki-fix-20261026-2027`), because
  `~/github/wt/fleet/*` worktree directories were being deleted within seconds of creation
  (registered but dir-gone; reproduced twice on main-2.local at 20:26–20:27). Branch/worktree
  removed unmerged; nothing pushed.

## Measured

- Baseline suite (JVM leg, in-repo `script/jvm-test.sh` at b0bfbd1): **532 tests / 3611 assertions /
  0 failures / 0 errors, JVM_EXIT=0** (/tmp/tf-base-jvm.out). nbb leg: **not run**. Parity: **not run**.
- Audit (read-only, b0bfbd1): maturity.md NEXT fix package ②③⑤ is **already landed** on current
  main via PRs #52–#68 (validate caps, notional/product saturation (`pmul`/`fmul`), accumulator
  `fx/acc`/`fx/sadd` sweep incl. `:size`, rate caps + book-opts sizing, fuzz in-suite, bench 3-part
  fix + repro3 3x n=1M, in-repo JVM/nbb/parity legs, slab-exhaustion fix, weight sums). maturity.md
  NEXT is stale relative to origin/main.

## Open door (unlanded; proposed next fix package: tier-shape gate)

`:margin-tiers` / `:fee-tiers` entries are stored raw by `apply-tx :list-market`/`:amend-market`
(state.cljk) and only range-checked by `rates-ok?`/`spec-ok?` — **completeness is never checked**.
The readers then hit nil/non-numbers mid-apply:

1. `clearing.cljk` `tier-for`: `(:max-notional %)` missing → `(<= notional nil)` throws.
2. `clearing.cljk` `rates-for` (tier branch): `(:initial-margin-rate t)`/`:maintenance-margin-rate t`
   missing → `fixed/mul-rate` `check` on nil throws, and `initial-margin-rate-for`'s
   `(max tier-im ...)` throws with a chosen leverage.
3. `clearing.cljk` `fee-rates-for`: `(:min-volume %)` missing → `(<= nil v)` throws.

Reachability: one bridge-signed `:list-market` with a tier in the shape `cl/market` itself documents
(`{:max-notional n :max-leverage l}` — rates derived at genesis but NOT at listing) passes validate
today; the first order or sweep on that market throws inside apply-block = halted chain
(falsify-6/7 class). Also: negative margin rates pass `rates-ok?` (±rate-scale for all rate keys) —
a negative requirement is solvency-breaking though deterministic.

## Proposed package (for the next tick; tests-first, REASON pins per overflow_gates_test pattern)

- `clearing.cljk`: `margin-tiers-ok?` / `fee-tiers-ok?` (each tier: `:max-notional` int-in 0..i53-max
  + both margin rates int-in 0..rate-scale; fee tier: `:min-volume` int-in 0..i53-max).
- `api.cljk`: split `rate-keys` into fee (±rate-scale) vs margin-rate keys (0..rate-scale, refusal
  `:rate-too-high`); wire tier predicates into `:list-market`/`:amend-market` as `:missing-field`.
- `state.cljk`: no-op safety net in both apply-tx methods (mirror `book-opts-ok?`), so direct
  apply-tx callers are total too.
- Tests (BROKEN run first): REASON pins `:missing-field` (incomplete tiers), `:rate-too-high`
  (negative margin rate), nil pins for complete tiers, no-op equality pins for the engine net.

Discovered by torihiki-fix tick 20261026-2027; scores untouched (measurement bots' file).
