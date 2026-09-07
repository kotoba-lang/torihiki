# falsify-12 not-run (host load gate)

Date: 2026-09-04 10:55 JST
Trigger: job spec rule — host load > 20 → not-run evidence only.

## Measured load (real `uptime` samples at run time)

- 10:55 (state.sh): 25.63 28.06 26.98
- 10:55:32 (re-check): 25.72 27.90 26.95

1-min load average 25.6–25.7 > 20 on both samples → falsify measurement
suppressed. No hypothesis was run, no code changes, no other files written.

## Hypothesis reserved for the next run (unchanged from maturity.md NEXT)

falsify-12 candidate — validate-layer fix prerequisite verification is not yet
possible (NEXT requires the validate-i53-halt fix first). The next runnable
hypothesis once load < 20 and the fix lands remains as recorded in
status/maturity.md NEXT:
- api/validate :bad-amount i53 checks (:deposit amount, :order qty)
- notional cap level×qty ≤ i53-max (falsify-7)
- balance-domain gate collateral+amount > i53-max (falsify-8)
- accum sum gate at :deficit / :funding-residue / :fees-collected and
  collateral (fnil +/- 0) sites (falsify-9/10/11)
- fx/mul-rate pre-check product bound; REDUCING branch
  (* entry-notional closed) pre-bound; rate upper bound.

No falsify-12 verdict issued this run.
