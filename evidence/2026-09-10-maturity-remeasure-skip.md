# 2026-09-10 17:20 JST — maturity remeasure skip (not-run evidence only)

Host load: 17:20 up 5 days, 10:03, load averages: 65.29 85.81 81.98 — all three
well above the >20 not-run threshold. No falsify measurement executed this run.

## Hypothesis queued for next low-load window (falsify-14, not started)

**Multi-account deficit aggregation path**: falsify-12 measured the single-account
deficit accumulation (`clearing.cljc:711` via real apply-block sweep, state.cljc:1189)
and falsify-13 measured the `:insurance-fund` accumulator (`liquidation.cljc:195`),
but the multi-account aggregation of deficits — where settle-deficit sums per-account
deficits across >1 account before writing to `:insurance-fund` — remains unmeasured.
Hypothesis: with 2+ accounts each carrying in-domain deficits that sum to a
2^53+1 crossing, the aggregate write crosses unchecked → cross-runtime root
divergence (JVM exact integer vs nbb double rounding), throw 皆無, same signature'd
tx sequence → validator disagreement. Same fix package (累算 sum gate) as f9–f13.

## Status

- No measurement run (load gate). No files modified except this skip record.
- maturity.md / NEXT unchanged from pre-run state (falsify-13 already fully
  recorded: verdict 2026-09-09-falsify-13-insurance-fund-accum-overflow.md,
  evidence/falsify13-{jvm,nbb}.out verified present and consistent — JVM
  fund2=9007199254740993/root2 15df2574… vs nbb fund2=…992/root2 0b4f0277…,
  throw 皆無, honest crossing 976,562 events).
