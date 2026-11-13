# 2026-10-24 02:19 torihiki-fix tick — package scoped, NOT LANDED (budget exhausted)

Load gate: pre-run `sysctl vm.loadavg` 13.42/13.74/13.53 + `torihiki_state.sh` uptime
12.68/13.56/13.48 — all windows < 20, gate open. (torihiki_state.sh and bare terminal
calls fail with a shell-startup `cd` to a stale worktree path / exit 126; workaround:
explicit `workdir` on every terminal call.)

## Finding: maturity.md NEXT ②'s fix package is already fully landed

Verified at origin/main b6b4cb4 (worktree bot/torihiki-fix-20261024-0219 created and
removed this tick; no code change landed):

- PR 52 i53 fix package (fx/acc, fx/mul-rate exact, validate caps)
- PR 53 fuzz suite 常設化
- PR 54 falsify-14 merkle-sum saturation
- PR 55 rate caps + book-opts sizing + funding sample sum
- PR 56 accumulator sweep (every (fnil + 0) through fx/acc)
- PR 58 residual i53 caps (every integer that lands in state)
- PR 59 repro3 bench n=1M 3x stable (cancelled=153,767)
- PR 60 JVM test leg in-repo (script/jvm-test.sh)

Measured this tick: `bash script/jvm-test.sh` on b6b4cb4 = **502 tests / 3504
assertions / 0 failures / 0 errors** (JVM leg only; nbb leg not run — budget).

## Next fix package, scoped but unmeasured (candidate for the next tick)

"The mark-to-market path: products and folds are bounded before they are computed" —
the falsify-9/11-secondary product class and the falsify-8..13 fold class surviving
on the VALUATION path (not (fnil + 0), not stored fields, so the earlier sweeps
missed them):

1. `torihiki.fixed/notional` (fixed.cljk:211): `(check :notional (* price size))`
   computes the product BEFORE the check — products > 2^63 are a bare JVM overflow
   vs silent JS rounding. Pre-bound the product; refuse identically with
   `:where :notional` on both runtimes.
2. `torihiki.clearing/unrealized` (clearing.cljk:119): `(- (fx/notional mark size)
   entry-notional)` — mark x size is unbounded (accumulated position size); a
   valuation THROWS mid-margin/sweep. Route through fx/pmul + saturate the result.
3. `torihiki.clearing/apply-fill-to-position` + `apply-fill`: FLIP realized
   `(fx/notional price size)` and the fee's `(fx/notional price delta)` see
   liquidation-slice deltas (slice-size caps at |size|, not at the validate cap) —
   pmul + sadd on realized/deltas.
4. `torihiki.clearing/equity` / `maintenance-margin` / `initial-margin` and
   `torihiki.order-margin/requirement` + `market-requirement` loss folds:
   `reduce + 0` over in-domain terms crosses 2^53 — fx/acc (and pmul on
   `(mark - l) x q` loss products, order_margin.cljk:125-128).
5. `torihiki.liquidation/adl-candidates` (liquidation.cljk:150-155) and
   `slice-size` (:66): `(* notional fx/rate-scale)` and `(* profit leverage)` raw
   products — bare JVM overflow mid-sweep (any position with notional > ~9.2e9) vs
   silent JS rounding in the ranking. pmul + result clamps; ranking falls back to
   the account-id tiebreak past the clamp (stated precision loss, like pmul).
6. `torihiki.funding/premium` (funding.cljk:68) and `torihiki.oracle/deviation`
   (oracle.cljk:131): `(* diff fx/rate-scale)` raw — oracle i53-max vs book
   level 1 gives a 9e24 product: JVM halt mid-:funding-settle, JS rounds to a
   different premium. pmul + fdiv.

Tests must follow test/torihiki/overflow_gates_test.cljk: REASON literal for the
fx/notional refusal, VALUE pins for every clamp, run BROKEN before fixed, both
runtimes (script/jvm-test.sh + script/tests-on-nbb.cljk 2-stage classpath).

Not run this tick: nbb leg, any new test, any fix implementation — budget exhausted
mid-discovery. Nothing landed; worktree and branch removed.
