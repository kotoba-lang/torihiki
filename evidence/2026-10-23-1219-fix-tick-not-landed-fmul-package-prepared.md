# 2026-10-23 12:1x torihiki-fix tick — NOT LANDED (budget exhausted mid-tick), fix package prepared

Load gate PASSED (12:18 direct 8.92/13.30/15.16, torihiki_state.sh 8.68/13.17/15.11 — all windows < 20).
No code landed this tick. Worktree bot/torihiki-fix-20261023-1219 created at origin/main b6b4cb4 and removed unmodified;
local branch `bot/torihiki-fix-20261023-1219` still exists (branch deletion blocked by cron approval policy — next tick: `git branch -D bot/torihiki-fix-20261023-1219`).

## Survey (measured): maturity.md NEXT is stale; the fix fleet landed PRs #52–#60

PR #52 i53 overflow fix package (falsify-6..13), #53 fuzz suite in-suite, #54 merkle-sum saturation (falsify-14),
#55 rate caps + book-opts + funding sample sum, #56 accumulator sweep via fx/acc, #58 residual i53 caps,
#59 repro3 bench n=1M 3x (cancelled=153,767), #60 script/jvm-test.sh (JVM leg one command).
Notional cap (api.cljk:126-131 `level x qty <= i53-max`), balance-domain gate, settle-deficit clamp, pmul REDUCING
bound, bench 3-arg cancel! — all verified present at b6b4cb4.

## The one remaining package: `fx/fmul` — exact floor(a*b/c), replacing every raw-product-then-divide site

Explicitly flagged as follow-ups by PR #52 ("exact two-limb basis division") and PR #58 ("Known leftovers #1:
vault share products... Needs an exact floor(a*b/c) primitive (pmul clamping would misprice payouts)").

Sites (all apply-path; old code = bare JVM overflow throw and/or silent JS rounding):
1. clearing.cljk:166 REDUCING `closed-basis (fx/fdiv (fx/pmul entry-notional closed) size)` -> `(fx/fmul entry-notional closed size)`
2. clearing.cljk:286 vault-deposit `(quot (* amount total) pool)` -> fmul
3. clearing.cljk:313 vault-withdraw `(quot (* shares pool) total)` -> fmul
4. liquidation.cljk:152 adl leverage `(fx/fdiv (* notional fx/rate-scale) base)` -> fmul (product 1e24+; JVM throws for
   any notional > 9.2e9 -> liquidation halt, falsify-7 class)
5. liquidation.cljk:155 adl score `(fx/fdiv (* profit leverage) fx/rate-scale)` -> fmul
6. validators.cljk:338 slash reward `(quot (* total slash-reward-percent) 100)` -> fmul (percent = 5)

Primitive: `(fmul a b c)` = floor(a*b/c) exact for i53 inputs, c != 0; result clamped to [i53-min, i53-max] (pmul
philosophy: deterministic, never throws); floor semantics on signs. Core: recursive halving of b —
a*b = 2*(a*floor(b/2)) + bit*a; remainder r < c tracked with carry trick `k = (>= r (- c r))`, remainder computed as
`(- r (- c r))` or `(+ r r)` so EVERY intermediate stays in-domain/exact on both runtimes (no bit ops — cljs bit ops
are int32!). q saturated at i53-max per step; branch decisions identical on both runtimes (same argument as fx/sadd).

## Pinned test values (computed exactly; each DIVERGES from the old code on the JS/kbb leg)

- fmul unit: (fmul 3 4 5)=2, (fmul 7 5 4)=8, (fmul 1e12 1e12 1e12)=1000000000000,
  (fmul M M M)=M (M=9007199254740991), (fmul -3 2 4)=-2, (fmul 3 -2 4)=-2, (fmul -3 -2 4)=1,
  clamp (fmul M M 1)=M, (fmul -M M 1)=-M. Odd-carry core case: (fmul 4503599627370497 4503599627370495 4503599627370496)=4503599627370495.
- vault-deposit (divergent): amount=3807572383388538 total=2503842113501302 pool=1940371675451696
  -> minted 4913264919471391 (old JS gives ...392). All sums in-domain.
- vault-withdraw (divergent): shares=5056245340839859 pool=6450328040642089 total=6317976265679753
  -> payout 5162165815587510 (old ...511). All sums in-domain.
- REDUCING: size=2e12 entry-notional=7.2e14 closed=1e12 price=720 -> closed-basis 360000000000000,
  realized 360000000000000, remaining entry 360000000000000 (old pmul clamp gives basis 4503599).
- adl-ranking: mkt 3, mark=400273, pos {:size 9349941134 :entry-notional 615628472864798}, collateral 4595685917346004,
  opposite-of -1 -> score 1515362229667056 (old JS gives ...057; old JVM throws on the 3.7e27 product).
- slash reward boundary (through validators_test harness chain/block/evidence): operator bond 3602879701896410
  (1000 from chain + bond 3602879701895410), delegator 600 bond 3602879701896410 -> total 7205759403792820,
  reward = min(own, floor(total*5/100)) = 360287970189641 (old JS gives ...640).

Deferred with analysis: funding.cljk:68 premium `(* diff rate-scale)` — unreachable past the n-levels bound
(PR #55's note stands); slash is only a measure-zero double-rounding boundary — but it is real (value above).

## Left
Full landing (impl + broken-first tests + kbb -M:test + script/jvm-test.sh + PR + merge) next tick;
also delete leftover local branch bot/torihiki-fix-20261023-1219.
