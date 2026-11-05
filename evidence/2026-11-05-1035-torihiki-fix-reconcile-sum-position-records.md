# 2026-11-05 10:3x torihiki-fix tick — reconcile! position records: the SUM is
# the position (fresh falsify-style measurement; maturity.md NEXT measured
# stale by ticks 20261104-1439 / 20261105-0439 — all items landed, queue is
# residual reads + new measurements). "not run" != "passed": only the kbb leg
# is claimed green below.

Load gate: torihiki_state.sh absent (chronic-fault precedent); uptime direct
10:17 — 1.36 / 1.48 / 1.49, all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-1035, branch
bot/torihiki-fix-20261105-1035 off origin/main 13d156d (PR #102 merge).

## Finding (measured): venue-router reconcile! kept the LAST position record,
## not the sum

`torihiki.venue-router/reconcile!` folded the venue's `:positions` answer
with `assoc` per record: when a venue reports SEVERAL records for one symbol
(one per position — a hedging account), only the last survived. Measured
modes on kbb/SCI with a stubbed `:positions` returning records
[0.30, -0.10] against a held +20 lots:

- agreement misread as disagreement: `{:market 1 :ours 20 :venue -10}`, halt
- disagreement carried by the sum MISSED (ours +10, venue sum +20): the
  buggy fold saw -10 = ours-adjacent shape and reported `:venue -10`
- off-grid record (0.305) exactness judged from the last record only

The fake venue aggregates to one record per symbol, so no existing test
exercised the multi-record shape.

## Fix

venue_router.cljk `reconcile!`: sum every record per market (lots cut toward
zero per record, nil from an unstateable record propagates — the venue side
stays unstated); `:exact?` is true only if EVERY record is a whole number of
lots. test/torihiki/venue_test.cljk `reconcile-sums-every-position-record-in-a-symbol`
pins the values (0F block pins `[]`, the diff vectors `[{.. :venue 20}]` /
`[{.. :venue nil}]`, and the halt flags) per the overflow_gates pattern.

## Measured (exact counts)

- BROKEN (fix reverted, new deftest present), kbb -M:test: 650 tests / 5538
  assertions / 5 failures / 0 errors, exit 1 (/tmp/rec-broken-kbb.out) — all
  5 reds are the new pins, each showing `:venue -10` (last-record fold).
- GREEN (fix applied), kbb -M:test: 650 tests / 5538 assertions / 0 failures
  / 0 errors, exit 0 (/tmp/rec-green-kbb.out). Baseline before this package
  was 649/5526 (PR #102 landing record): +1 deftest / +12 assertions.

## Not run / not claimed

JVM leg (script/jvm-test.sh) and nbb leg (script/nbb-test.sh): NOT RUN
(budget). The same-count three-leg claim is NOT made here — the JVM/nbb
counter equality for this package is an open measurement, not a pass.

## Queue state after this tick

- Issue #50 (order-margin running sums): acceptance satisfied by PR #102
  (landing record evidence/2026-11-05-0830-...; 3 legs 649/5526; bench
  2,000-level flatness 0.98x) — closeable with that reference.
- Issue #76 (terminal market-scale / test runner): lives in
  lib/kotoba.torihikiTerminal (retired repo) — out of scope per tick
  boundaries.
