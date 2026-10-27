# 2026-10-25 00:26 torihiki-fix tick — NOT LANDED (budget exhausted mid-implementation, after a measured RED run)

Gate: torihiki_state.sh pre-run load **14.96 / 14.87 / 17.96** — all windows < 20 (the tick gate is "all > 20 → stop", not triggered) → tick proceeded.

## Package: 「notional money math is total」 (resumes the 2026-10-24-1819 plan)

Worktree **~/github/wt/fleet/bot-torihiki-fix-20261025-0026**, branch `bot/torihiki-fix-20261025-0026` @ origin/main **9c0721b** (PR #63 parity leg landed since the 1819 plan — `script/jvm-test.sh` / `nbb-test.sh` / `jvm-parity.sh` / `nbb-parity.sh` are in-repo now). IN-FLIGHT fixed.cljk +36 / slab.cljk +1 (frame-561 era) confirmed gone — fx/sum seed landed as fx/sadd + fx/acc via PRs #52/#55/#56.

## Measured this tick (exact counts)

**RED run, final test file, unfixed src: `kbb -M:test` → 508 tests / 3526 assertions / 3 failures + 8 errors** (513 pass; /tmp/red2-kbb.out, exit 1, ~17 s). All 11 broken assertions are in the new `overflow_gates_test.cljk` section "notional money math is total":
- 5 ERROR — `fx/notional` clamp pins, each throws `torihiki.fixed: value escaped the i53 domain` `:where :notional` (halt door; the `:value` data shows the raw product: 1.8e16, 8.1e31, −1.44e18).
- 1 ERROR + 1 FAIL — `cl/unrealized`: product clamp throws; the 2^54−2 difference pin returns raw `18014398509481982`.
- 1 ERROR — flip fill via `cl/apply-fill` throws :notional mid-apply (clearing.cljk:153 → fixed.cljk:211 in the callstack).
- 1 FAIL + 1 ERROR — `om/requirement`: mark branch returns raw `9682739198846566`; no-mark branch throws `:where :mul-rate` on the raw 1.57e16 order-notional sum (halt door through free-collateral).
- 1 FAIL — `cl/equity` 2-position fold returns raw (falsify-8 class).

An earlier RED run (5F/8E, /tmp/red-kbb.out) caught 3 fixture bugs in my own tests (cl/equity / cl/maintenance-margin / cl/initial-margin called without the `acct` arg — they silently fold over the wrong account); fixtures fixed, then the canonical RED above was run on the SAME test file that must go green. Test pins were computed with exact integer arithmetic (python3) beforehand — loss-b = 2^53+1 (the falsify-13 crossing value) by construction.

Honest note: `a-reducing-fill-realizes-a-saturated-difference` and the in-domain maintenance/initial pins pass pre-fix too (regression guards — the state-landing sites already saturate via fx/acc); the broken-first evidence is the 11 assertions listed above.

## Fix state in the worktree (UNCOMMITTED)

Done:
1. `src/torihiki/fixed.cljk` — `notional` now `(pmul price size)` (saturating, never throws), moved below `pmul`/`abs*` (definition order).
2. `src/torihiki/clearing.cljk` — `unrealized` = `(fx/sadd (fx/notional mark size) (- entry-notional))`; FLIP and REDUCING realized differences likewise through `fx/sadd`.

Remaining (same package, mechanical):
3. `clearing.cljk` folds → `fx/acc`: equity:434-435 `(+ acc (unrealized …))`, maintenance-margin:447, initial-margin:~480, isolated-liquidatable?:518 `(+ iso (unrealized …))` → `fx/sadd`.
4. `order_margin.cljk` → `fx/acc`/`fx/sadd`: market-requirement bq/aq/loss-b/loss-a folds + `(± size bq)` sums + margin+loss sums + the two no-mark folds; `requirement` outer fold:150; `orders-allowed?` filled/cost folds + `:signed`/`:delta` updates (the `:delta` `(update :delta + A B)` becomes two `fx/acc` steps).
5. `state.cljk:1538-9` and `:1569-70` filled/cost `reduce + 0` folds → `fx/acc`.
Then: GREEN `kbb -M:test` (must be 519 tests incl. the 6 new deftests, 0F/0E), then `bash script/jvm-test.sh` and `bash script/nbb-test.sh` as budget allows (the divergence pins are only fully proven on both runtimes), commit → PR → merge → worktree remove.

Expected GREEN pins (verified exact): notional clamps M/M/−M/−M/M; unrealized M−1 and M; flip fill size 1e12, entry-notional M, collateral **−7567199254740991**; reducing fill entry-notional −4503599627370495, collateral M; om/requirement mark branch M, no-mark 900719925474099; equity fold M; maintenance 900719925474098; initial 1801439850948198. (The 1819 plan's collateral figure −7566199254740991 is an arithmetic slip — recomputed exact.)

## Left for later ticks (out of this package)

`:size` fold clearing.cljk:137 `(+ size delta)` (root-visible, the 1035 package); root-preimage/derived sums (clearing.cljk:252 stake-of, :580-581 bonded, :624 volume-of, state.cljk:2321-2323 bonds/unbonding encode folds); `fx/fmul` exact floor(a·b/c) package (vault-deposit/withdraw quotients, adl leverage/score products, slash reward — pinned values in evidence/2026-10-23-1219); `trg/valid?` trigger-price i53; funding.cljk:68 premium product (deferred, unreachable past n-levels per PR #55).

Housekeeping: stale worktrees from ticks 20261022-1835 … 20261024-2219b remain registered and ~20 stale local `bot/torihiki-fix-*` branches accumulate (branch deletion previously blocked by cron policy).
