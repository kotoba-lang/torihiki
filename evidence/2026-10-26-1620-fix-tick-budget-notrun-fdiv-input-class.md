# 2026-10-26 16:20 torihiki-fix tick — NOT LANDED (runtime budget exhausted mid-tick)

Host load 16:17 pre-run 10.55/15.64/18.41 all <20 — gate PASSED. Worktree
bot/torihiki-fix-20261026-1618 created at origin/main b0bfbd1 (moved by the
fleet manager to ~/github/wt/fleet/bot/torihiki-fix-20261026-1618; git's
registration path is stale for all fleet worktrees).

Baseline measured: `kbb -M:test` on b0bfbd1 = **532 tests / 3611 assertions /
0 failures / 0 errors, EXIT=0** (evidence carrier: /tmp/base-test.out this run).

## Finding (measured, kbb = JS-double leg): fdiv's INPUTS fork, not fdiv

maturity-note hypothesis "cljs fdiv rounds for quotients >= 2^52" is
**exonerated in-domain**: q >= 2^52 needs divisor 1 (exact) or 2 (exact .5);
probe: (fx/fdiv 9007199254740991 2) = 4503599627370495 exact on kbb.

The real fork is raw +/- feeding fdiv (falsify-8 class at a decision):

- kbb probe: (fx/fdiv (- 9007199254740991 -4) 2) = **4503599627370498**;
  exact floor((2^53+3)/2) = **4503599627370497** (JVM floorDiv). The
  difference 2^53+3 rounds to 2^53+4 on doubles before the division.
- kbb probe: (fx/fdiv (+ 9007199254740991 9007199254740988) 2) = **…990**;
  exact floor((2^54-5)/2) = **…989**. (Note: kbb prints sums past 2^53 with
  its own mangling; equality probes confirm the rounding.)

Unfixed sites at b0bfbd1 (grep-verified):
- `liq/bankruptcy-price` (liquidation.cljk:57): `(fx/fdiv (- entry-notional collateral) size)`
- state.cljk:1540 ADL slice limit: `(- (fx/fdiv (- collateral entry-notional) size))`
- `mark/impact-mid` (mark.cljk:58): `(fx/fdiv (+ a b) 2)` — unreachable at the
  boundary (impact prices < n-levels) but same class.
- `bk/cancel-all!` (book.cljk:883): raw `(+ total …)` / `(reduce (fn [s oid] (+ s …)))`
  return-total fold (the item PR #68 deferred).

## Designed package (next tick: red-green then land)

1. `fx/fdiv-diff [a b c]` — exact floor((a-b)/c) without forming the
   difference: qa=(fdiv a c), qb=(fdiv b c), ra,rb = floorMod residues in
   [0,|c|), result (sadd qa (sadd (- qb) (fdiv (- ra rb) c))) — saturated
   result like pmul, byte-identical below the ceiling, c != 0 precondition.
2. Migrate the three fdiv sites (impact-mid = (fdiv-diff a (- b) 2)).
3. Saturate cancel-all!'s two folds via fx/sadd.
4. Tests (overflow_gates_test.cljk VALUE pins), expected RED before fix:
   - (= 4503599627370497 (liq/bankruptcy-price {:size 2 :entry-notional 9007199254740991} -4)) — RED on kbb/nbb (…498), GREEN pre-fix on JVM
   - short twin {:size -2 :entry-notional -9007199254740991} 4 — same
   - (= fx/i53-max (liq/bankruptcy-price {:size 1 :entry-notional 9007199254740991} -2)) — RED on ALL legs (true quotient 2^53+1)
   - (= fx/i53-min (liq/bankruptcy-price {:size 1 :entry-notional -9007199254740991} 2)) — RED on ALL legs
   - (= fx/i53-max (bk/cancel-all! b 7)) with two bids 4503599627370496 + 4503599627370497 — RED on ALL legs (total 2^53+1)
   - small exact cases + impact-mid shape pin + fdiv-exoneration pins (green always, labelled invariant pins, not red-green claims).

Nothing merged; branch bot/torihiki-fix-20261026-1618 has no commits. Scores
untouched (measurement bots' lane).
