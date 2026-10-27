# 2026-10-23 20:23 torihiki-fix tick — NOT LANDED (run budget exhausted mid-implementation), fmul package in progress

Load gate PASSED (20:18 direct 8.00 / 9.76 / 11.29, all windows < 20; torihiki_state.sh
ran clean this time via workdir workaround — its first failure was the terminal default
cwd pointing at a vanished worktree path, not the script).

## Survey (measured): maturity.md NEXT ② is fully landed (PRs #52–#60); the two prepared
packages (evidence/2026-10-23-1022 pmul-swap, 2026-10-23-1219 fmul) are the remaining
queue. This tick took the **fmul package** (newest scope, PR #52/#58 flagged follow-up,
closes a JVM-halt path at liquidation.cljk:152). All pins from the 1219 note re-verified
exactly with python3 integer arithmetic this tick (vault-deposit 4913264919471391,
vault-withdraw 5162165815587510, REDUCING basis/realized/entry-remain 360000000000000,
slash reward 360287970189641, adl score 1515362229667056, carry case 4503599627370495).

## State left for the next tick

Worktree **/Users/junkawasaki/github/wt/fleet/bot/torihiki-fix-20261023-2023** (branch
bot/torihiki-fix-20261023-2023 @ b6b4cb4 = origin/main) — LEFT IN PLACE with WIP:

- tests WRITTEN (broken-first satisfied, both broken states measured):
  overflow_gates_test.cljk +4 deftests (`fmul-divides-exactly-without-forming-the-product`,
  `the-reducing-basis-divides-exactly`, `the-vault-share-price-divides-exactly`,
  `the-adl-score-divides-exactly`) and validators_test.cljk +1 deftest
  (`the-slash-reward-divides-exactly`, through the chain/block/evidence harness with
  op-extra 3602879701895410 / del-bond 3602879701896410).
- BROKEN run 1 (fx/fmul absent): KBB_EXIT=1 "Unable to resolve symbol: fx/fmul"
  (/tmp/fmul-broken.{out,err}).
- BROKEN run 2 (primitive added, sites unwired): KBB_EXIT=1, **507 tests / 3529 assertions,
  12 FAILs** (/tmp/fmul-broken2.out) — site pins fail on values (REDUCING basis 4503599,
  slash ...640, adl ...057-class, vault rounding) as predicted.
- fixed.cljk: `nn-mul`, `halve-mul`, `fmul` ADDED (fmul doc'd: floor on signs, clamp at
  bound, zero-factor 0, zero-divisor signed bound; check :fmul on inputs like mul-rate).
  **KNOWN BUG: `halve-mul` tail returns [0 0] for every input** — (fmul 3 4 5) gives 0,
  (fmul 7 5 4) gives 5 (head only). Paren imbalance in fmul was found and fixed (form
  balance verified). Scratch debug file `dbg.cljk` written in the worktree root (prints
  halve-mul step trace) but NOT YET RUN — run `kbb dbg.cljk` from the worktree first.
  Likely suspect: the inner bit-extraction loop or `empty?`/`first` on the cons list
  behaving differently under kbb's sci — the head path (nn-mul/fdiv) is proven correct
  by the passing small-head cases.

## Remaining after the fix (exact)

1. Fix halve-mul so the unit pins pass (pins are in the test file; the carry case
   (fmul 4503599627370497 4503599627370495 4503599627370496) = 4503599627370495 is the
   discriminating one).
2. Wire the 6 sites (current code at b6b4cb4):
   - clearing.cljk:166 `closed-basis (fx/fdiv (fx/pmul entry-notional closed) size)` -> `(fx/fmul entry-notional closed size)`
   - clearing.cljk vault-deposit `(quot (* amount total) pool)` -> `(fx/fmul amount total pool)`
   - clearing.cljk vault-withdraw `(quot (* shares pool) total)` -> `(fx/fmul shares pool total)`
   - liquidation.cljk:152 `leverage (fx/fdiv (* notional fx/rate-scale) base)` -> `(fx/fmul notional fx/rate-scale base)`
   - liquidation.cljk:155 `score (fx/fdiv (* profit leverage) fx/rate-scale)` -> `(fx/fmul profit leverage fx/rate-scale)`
   - validators.cljk:338 `(min own (quot (* total slash-reward-percent) 100))` -> `(min own (fx/fmul total slash-reward-percent 100))`
   Note: vault paths move quot (trunc) -> floor — identical for the non-negative operands
   the guards enforce; consistent with the single floor-division rule.
3. `kbb -M:test` green (expect 507 tests / 3529 assertions 0F/0E), then
   `script/jvm-test.sh` SAME counters (cross-runtime claim), PR + merge, remove worktree,
   delete local branch. Also still owed from the 1219 note: `git branch -D
   bot/torihiki-fix-20261023-1219`.

Deferred (unchanged): the 1022 pmul-swap package (derived-SUM class = its own tick).
