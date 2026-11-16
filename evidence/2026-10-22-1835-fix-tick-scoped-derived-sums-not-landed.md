# 2026-10-22 18:35 torihiki-fix tick — NOT LANDED (run budget exhausted after reconnaissance + baseline)

Load gate: pre-run 18:18 5.23/5.07/5.19 — all windows < 20, gate passed. Tick proceeded.

## Finding 1 — maturity.md NEXT is stale vs origin/main (all code items landed)
fetched origin/main = b9c1ab7 (PR #57 anchor-tx). PR #52 (i53 overflow fix package,
falsify-6..13), #53 (fuzz suite 常設化), #54 (falsify-14 merkle aggregate), #55
(rate caps + book-opts), #56 (accumulator sweep: every residual (fnil + 0) fold
through fx/acc) all MERGED. bench cancel! 3-arg at all 3 sites. Grep of src/ finds
zero raw `(fnil +` folds. NEXT's remaining unlanded item is only the reproducibility
measurement (HEAD bench n>=1M 3x low-load stable, cancelled>0 -> 再現性 2->3).

## Finding 2 — the residual class PR #56's sweep missed: RAW `+`/`-` derived sums and
accumulated-size products (not `(fnil + 0)`), several root-visible or throw-on-apply:
- clearing.cljk:137 `new-size (+ size delta)` — position :size is ROOT-ENCODED
  (state.cljk:2291-2292) and folded per fill with raw + — the falsify-8..13 class at
  a root-visible accumulator the sweep missed.
- clearing.cljk:119 `unrealized` `(- (fx/notional mark size) entry-notional)` —
  `mark x ACCUMULATED size` goes through `fx/notional` = `check` = THROW mid-apply
  (falsify-6/7 halt class) once size accumulates past i53/mark.
- clearing.cljk:434-435 equity fold `(+ acc (unrealized ...))`, :445-447
  maintenance-margin fold, :478-480 initial-margin fold, :526 balance `(- ...)`,
  :580-581 bonded `(reduce + 0 ...)` x2 + outer `+`, :252 stake-of `(reduce + 0 ...)`,
  :624 volume-of `(+ cur prev)`, :518 isolated `(+ iso (unrealized ...))` — derived
  sums of in-domain values crossing 2^53 = cross-runtime decision/root divergence
  (falsify-14 aggregate class; contrast state.cljk derived-spot-commitment sums,
  already fixed + pinned in overflow_gates_test).
- state.cljk:2321-2323 encode-clearing-account folds bonds/unbonding sums with raw
  `(reduce + 0 ...)` — ROOT PREIMAGE sums (falsify-14's exact mechanism).
- liquidation.cljk:66 slice-size and :150 adl `fx/notional mark size` (accumulated
  size -> throw inside the apply-block sweep); :152/:154 raw `(* ... fx/rate-scale)`
  products (falsify-9 mul-rate class); :202 `fx/notional avg-price filled`.
- funding.cljk:115 `fx/notional oracle-price position-size` (accumulated size ->
  throw in the funding-settle path).

Fix pattern (established by PR #52/#56, byte-identical below 2^53): folds ->
`fx/sadd`/`fx/acc`; mark×accumulated-size and unbounded products -> `fx/pmul`
(saturating, in-domain products exact); root sums -> `fx/sadd` folds.

## Red-green test plan (overflow_gates_test.cljk new section, VALUE pins)
T1 position-size fold saturates (cl/apply-fill, size i53-max-1 + delta 10 -> :size
   i53-max); T2 cl/unrealized on mark=1e6,size=1e13: pre-fix THROW :notional,
   post-fix i53-max + exact below-ceiling pin; T3 equity fold saturates; T4
   maintenance-margin/initial-margin folds saturate (2 markets x 6e15, rates
   rate-scale); T5 cl/bonded + cl/stake-of aggregates saturate; T6 volume-of
   `(+ cur prev)` saturates; T7 cl/balance saturates (balances i53-max, committed
   i53-min); T8 state-root equality pin: bonds sum crossing 2^53 vs pre-clamped
   sums -> same root (encode-clearing-account :2321 fold via st/state-root);
   T9 liq/slice-size on size=1e13,mark=1e6 pre-fix THROW -> post-fix
   2e12 (= mul-rate 1e13 (pct 20)); T10 liquidatable? convergence pin: pre-fix
   JVM liquidatable at the ceiling, post-fix both runtimes say false (e and mm both
   clamp to i53-max); T11 fnd/apply-funding on size=1e13, price=1e6 pre-fix THROW.
Every new test must run once BROKEN before the fix (house rule).

## Baseline measured this tick
Worktree ~/github/wt/fleet/bot-torihiki-fix-20261022-1835 (branch
bot/torihiki-fix-20261022-1835 @ origin/main b9c1ab7) — LEFT IN PLACE for the next
tick (visible and git-valid at creation; cf. the 2026-10-20 06:45 worktree-vanishing
note — re-verify with `git worktree list` before reuse).
`kbb -M:test` at b9c1ab7: **500 tests / 3484 assertions / 0 failures / 0 errors,
KBB_EXIT=0** (green baseline). No code changed, no tests added, nothing landed.

Next tick: execute the plan above in this worktree (tests RED first, then the
clearing/state/liquidation/funding edits), `kbb -M:test` green, PR + merge,
then the reproducibility item (3x bench n=1M) while load stays low.
