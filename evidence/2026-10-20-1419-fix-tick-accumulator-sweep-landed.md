# 2026-10-20 14:19 torihiki-fix tick — accumulator sweep fix package LANDED

Load gate: 14:17 pre-run 15.72/16.64/13.46, 14:18 direct 18.07/17.20/13.85 —
not all windows > 20 (stop rule not triggered). Tick proceeded.

## Fix package: residual `(fnil + 0)` accumulators routed through torihiki.fixed/acc

PR #52 gated the six measured falsify-8..13 sites. This package closes the
remaining raw numeric folds found by sweep at f487463 — every accumulator now
goes through `fx/acc` (saturating, total, never throws on the apply path):

- clearing.cljk: :bonds (bond/unbond), vault :shares/:total-shares
  (vault-deposit/vault-withdraw), :open-interest (apply-fill),
  :committed (commit-spot/release-spot), :balances (spot-fill both sides),
  volume :cur (add-volume, fold + first writes), :deficit pay-down (deposit)
- validators.cljk: slash-account delegator collateral, and :insurance-fund's
  SECOND writer (the slash path folds `total - reward` raw — falsify-13's own
  field; the liquidation stage-1 writer was gated in PR #52, this one was not)
- state.cljk: the two derived spot-commitment sums (spot-resting-backing and
  rebuild-spot-commitments — the latter WRITES :clearing :committed, so it
  enters the state root)
- funding.cljk: the sample count :n (a counter is an accumulator too)

## Measurements (exact counts)

- BROKEN run (new tests against unfixed code): kbb -M:test → 498 tests /
  3476 assertions, **16 fail, 0 error** (EXIT=1) — all 16 failures are the 10
  new overflow_gates_test deftests; every pre-existing test stayed green.
- FIXED run, nbb/sci leg: `kbb -M:test` → **498 tests / 3476 assertions,
  0 failures, 0 errors** (EXIT=0).
- FIXED run, JVM leg: renamed .cljk→.cljc copy + dep-shims (recipe from the
  1218 tick) `clojure -M:test` → **498 tests / 3476 assertions, 0 failures,
  0 errors** (JVM_EXIT=0). Same count on both runtimes.

## Notes

- Worktree deviation: worked in ~/github/wt/torihiki/fix-1419 (branch
  bot/torihiki-fix-20261020-1419b) instead of ~/github/wt/fleet/* — the fleet
  worktree dirs again vanished from the tool's filesystem view within a call
  of `git worktree add` (reproduced 14:19: `git worktree list` registers the
  dir while `ls`/`stat`/`cd` say it does not exist; same inconsistency as
  evidence/2026-10-20-0645-fix-tick-not-landed-worktree-vanishing.md).
- Gate-protected decrements (unbond, vault-withdraw, deficit pay-down,
  slash take!) route through fx/acc for the invariant but their clamps are
  unreachable through their own bounds checks — covered by the unchanged-
  behaviour pins plus the existing suite, not by clamp pins.
- maturity.md NOT touched (measurement bots own it). NEXT's stale code items
  were already landed (PRs #52–#55); what remains there is the reproducibility
  measurement (HEAD bench n≥1M 3× low-load, cancelled>0) plus this sweep,
  now landed.
