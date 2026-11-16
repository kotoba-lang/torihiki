# 2026-10-24 10:35 torihiki-fix tick — NOT-RUN (budget exhausted), fix package identified

Host load 10:17 7.71/8.53/9.82 — all windows < 20, gate passed. No test/fix landed:
runtime budget exhausted mid-implementation. No PR, no code change on main.

## Finding: maturity.md NEXT queue is fully landed (PRs #52–#62)

Every named sub-item of NEXT ② and the reproducibility items is on origin/main
(7f32214): validate i53/notional/balance-domain gates (#52/#58, api.cljk:124/:131/:325/:367-375),
accumulator sweep via fx/acc (#56), exact mul-rate + pmul (#52), rate caps (#55),
settle-deficit delta clamp (clearing.cljk:731), REDUCING product bound via fx/pmul
(clearing.cljk:166), bench 3-part fix (66c206f), repro3 bench 3x n=1M (#59),
fuzz in-suite (#53), JVM/nbb/bench legs in-repo (#60-62). NEXT ① re-anchor is a
measurement-bot item.

## Highest-leverage UNLANDED fix package (identified, ready to implement)

"apply-path totality + the missed :size accumulator" — three throw doors reachable
with VALIDATED input plus one missed fold:

1. `apply-tx :order` (state.cljk:567) gates nothing but `pos? qty`. Out-of-range
   qty hits `bk/place!`'s `(fx/check :place-qty qty)` (book.cljk:535) and its raw
   level throw (book.cljk:536-537); out-of-domain account/side/flags hit `(long …)`
   casts (JVM throw / JS wrap — asymmetric); non-integer qty silently truncates.
   All re-dispatch paths (trigger fire state.cljk:885, TWAP slices :885, :amend
   "becomes an order" :1333) funnel through here. Fix: mirror validate-order-shape
   as a NO-OP gate at the top of apply-tx :order (and :amend), same bounds as
   api.cljk:122-133 (int-in? qty 1..i53-max, level 0..n-levels-1, level×qty ≤
   i53-max via quot, account in-domain, side 0/1, flags 0..7, builder/builder-fee).
2. `apply-fill-to-position` :size fold is plain `(+ size delta)` (clearing.cljk:137)
   — the #56 sweep covered `(fnil + 0)` folds only. Two in-domain fills walk :size
   past 2^53 (falsify-8 class: divergence, and liquidation slices then feed
   out-of-domain qty to place!). Fix: `(fx/sadd size delta)` at the top-level
   new-size (clamp-safe for flip/reduce branches since those reduce magnitude).
3. `fx/notional` still `(check :notional (* price size))` (fixed.cljk:211) — THROWS
   on out-of-domain product. `mark × size` is unbounded on the honest path
   (liquidation.cljk:66/150, clearing.cljk:119/445/478/519, funding.cljk:115,
   order_margin.cljk:125-133): a large position vs a high mark halts the sweep
   (falsify-12 secondary class). Fix: notional = pmul (saturating; in-domain
   products byte-identical, pmul's own docstring already argues this).
4. Minor, same class, left for the next tick: `trg/valid?` (trigger.cljk:80) is
   shape-only — a raw :trigger stores an out-of-domain :trigger-price in state
   (validate caps it, apply does not); `reduce + 0` transient folds at
   state.cljk:1538-1539/1569-1570 and order_margin.cljk:245-246 feed fdiv
   quotients (fork-risks); raw :cancel oid/account casts unexamined.

Tests planned (overflow_gates_test.cljk pattern: REASON for validate refusals,
VALUE for apply no-ops/clamps): out-of-domain qty/level/account/flags orders are
state-identical no-ops (currently throw), qty=i53-max at level 1 and level
=n-levels-1 still place (VALUE pins via bk/level-orders), notional door mirrors
the :bad-amount pin, :size clamps to i53-max/i53-min (VALUE pins, was i53±4),
fx/notional saturates (VALUE pins, was throw :notional), trg/valid? i53 check.

Broken-first run NOT done. Suite NOT run this tick — nothing is green or red.

Housekeeping: worktree bot/torihiki-fix-20261024-1035 removed; local branch of the
same name remains (branch deletion blocked by cron command policy) — delete or
rebase it on the next tick before reuse. One stray edit existed only inside the
removed worktree (trg require line); nothing in the repo tree was touched.
