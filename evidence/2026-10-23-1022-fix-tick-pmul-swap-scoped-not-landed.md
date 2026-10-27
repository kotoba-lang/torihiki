# 2026-10-23 10:22 torihiki-fix tick — NOT LANDED (run budget exhausted after reconnaissance + baseline + mechanical pin derivation)

Load gate: 10:18 direct 4.61 / 8.52 / 40.04, 10:22 7.59 / 7.79 / 31.38 — 1-min/5-min
well under 20 and all windows declining; SOUL gate ("all windows > 20 -> not-run")
not triggered. Tick proceeded.

## Measured this tick
- Baseline `kbb -M:test` in a fresh worktree at origin/main **b6b4cb4** (PR #60):
  **502 tests / 3504 assertions / 0 failures / 0 errors, KBB_EXIT=0**
  (/tmp/tf1022-base.out, /tmp/tf1022-base.err). Green baseline, code unchanged.
- maturity.md NEXT ② fix package, 3-part bench fix, fuzz-suite permanence, JVM test
  leg (script/jvm-test.sh) all landed (PRs #52–#60) — re-verified by direct code
  reads of fixed.cljk (sadd/acc/pmul/exact mul-rate), clearing.cljk (settle-deficit
  clamp, fx/acc sweep), api.cljk (i53 caps, notional cap level×qty, balance-domain
  gate). Remaining unlanded fix item is the falsify-7-class residual scoped by the
  0433 note (evidence/2026-10-23-0433-fix-tick-not-landed-budget-exhausted.md).

## Environment finding: fleet worktree re-shaper
A watcher moves `~/github/wt/fleet/bot-<name>` dirs to `~/github/wt/fleet/bot/<name>`
within seconds of creation (observed twice this tick: .../bot/torihiki-fix-20261023-0219
and -1022 and -1022b). The git worktree registry keeps the flat path, so the registered
path goes stale — but the moved checkout is fully functional via its absolute gitdir
(`git -C /Users/junkawasaki/github/wt/fleet/bot/<name> ...` works). Do NOT `git worktree
prune` before reuse; that orphans the moved dir. This is the "worktree-vanishing"
phenomenon from 2026-10-20 06:45 — the dir is not lost, it is reshaped.

## Correction to the 0433 scope (verified by code read)
0433 said "keep checked fx/notional at validated per-order sites (... order_margin.cljk
:125/:132/:133)". That does not hold: `:open-orders` is `{level -> qty}` with **per-level
AGGREGATED qty** (`adjust` sums per owner/level), and validate caps only per-ORDER
level×qty. Stacked orders at one level break the bound, so :125/:126/:132/:133 are the
same unbounded-product class as :127/:128. The loss-b/loss-a pair must be treated
symmetrically. Swap all of them; keep only :246 (`level × qty` per take-record, per-order
validated) checked.
Second finding: adl-ranking's raw `(* notional fx/rate-scale)` and `(* profit leverage)`
(:152/:154) must move to fx/pmul IN THE SAME patch as :150 — fixing :150 alone routes
into `fdiv`'s `(long)` cast of an out-of-long-range product on the JVM (throws) while JS
rounds through it: a NEW cross-runtime asymmetry. pmul chain keeps in-domain rankings
byte-identical (state_test adl tests stay green).

## The fix package (not landed — next tick executes)
Swap to saturating `fx/pmul` (clamp = house verdict for products; in-domain exact):
- clearing.cljk :119 unrealized, :145/:153/:155/:167 apply-fill folds, :401 fee base,
  :445 maintenance-margin, :478 initial-margin, :519 isolated-liquidatable?
- funding.cljk :115 payment
- liquidation.cljk :66 slice-size, :150/:152/:154 adl-ranking chain, :202 stage-1 fee
- order_margin.cljk :125/:126/:127/:128/:132/:133 market-requirement, :253/:254
  orders-allowed? delta
Keep checked (validate-bounded per-order): clearing.cljk:558, state.cljk:610/:711/:788/
:814/:1539/:1558/:1570, book.cljk:914, order_margin.cljk:246.

## Broken-first tests (overflow_gates_test.cljk new section; all pins derived MECHANICALLY
this tick via python3 — int-float exactness verified for every literal on JS doubles;
run each BROKEN before the edit, house rule):
- T1 cl/unrealized: mark 1e6, size 1e13, entry 12345 -> 9007199254728646; mark -1e6 ->
  -9007199254753336; exact below ceiling: (5000,2000)@1000 -> 4998000
- T2 cl/apply-fill: INCREASING size 1e13 + delta 1e13 @1e6 -> :size 20000000000000,
  :entry-notional 9007199254740991; FLIP delta -2e13 -> :size -1e13,
  :entry-notional -9007199254740991, collateral -9007199253728646 (from 1e6);
  REDUCING delta -1e12 entry 5e14 -> :size 9000000000000, :entry-notional
  500000000000000, collateral -9007199253740991 (closed-basis clamps to 0)
- T3 cl/maintenance-margin pct 5 -> 450359962737049; cl/initial-margin pct 10 ->
  900719925474099 (mark 1e6, size 1e13); isolated-liquidatable? -> false
- T4 fnd/payment 1e13 @1e6 pct 4 -> 360287970189639; exact (5000,1000) -> 200000
- T5 liq/slice-size 1e13@1e6 default-params -> 2000000000000
- T6 liq/adl-ranking size 1e13 mark 1e6 collateral 0 -> accounts [1], score 9007199
- T7 om/requirement (market im-rate = rate-scale): no-mark bids {1e6,2e10} ->
  9007199254740991 (covers :132), asks mirror (:133); mark 1e6 bids {500,2e10} ->
  9007199254740991 (:127); mark 1e6 asks {500,2e10} -> 18014398509481982 (:126+:128);
  mark 1, bids {2000000001,4999999}, sizes {1 -4999999} -> 9007199254740991 (:125)
- T8 liq/liquidate stage-1 fee door (take-fn 2-elem, cost nil): :stage :book,
  :closed -2000000000000, :insurance-fund 9007199254740
Then `kbb -M:test` green (502+new / 3504+new), then `script/jvm-test.sh` must show the
SAME counters (cross-runtime claim). PR notes both legs.

## State left for the next tick
Worktree **/Users/junkawasaki/github/wt/fleet/bot/torihiki-fix-20261023-1022b** (branch
bot/torihiki-fix-20261023-1022b @ b6b4cb4 = origin/main, clean tree) — LEFT IN PLACE,
usable via its moved path (see environment finding). Baseline already measured there.
Next tick: write the tests above, run BROKEN (record counts), apply the swaps, green,
both runtimes, PR + merge. Remaining after that: the derived-SUM class (raw `+` folds:
position :size fold clearing.cljk:137, equity/margin folds, encode-clearing-account root
preimage sums state.cljk:2321-2323, adl base `(+ collateral profit)`) = the 1835 plan's
T1/T3/T4/T5/T6/T7/T8 — separate package.
