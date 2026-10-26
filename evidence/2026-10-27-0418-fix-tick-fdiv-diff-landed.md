# 2026-10-27 04:18 torihiki-fix tick — LANDED: fdiv input class (exact `fdiv-diff`, 3 sites, `cancel-all!` folds)

Package: the 2026-10-26 16:20 designed package (`evidence/2026-10-26-1620-fix-tick-budget-notrun-fdiv-input-class.md`),
which measured the fork as raw `+`/`-` FEEDING `fx/fdiv`, not fdiv itself (falsify-8's
class, one step earlier): the true difference 2^53+3 is an odd number no f64 holds,
so JS rounds it to 2^53+4 before the division runs and the quotient lands one high
(kbb `…498` vs JVM floorDiv `…497`).

Branch `bot/torihiki-fix-20261027-0418` off origin/main `cec77c5` (PR #70).

## What landed

- `torihiki.fixed/fdiv-diff [a b c]` — floor((a-b)/c) without ever forming the
  difference: `(a-b)/c = (qa-qb) + (ra-rb)/c` with floor-division quotients and
  `mod` floorMod residues (residues via `mod`, NOT `a - qa*c` — the product can
  itself leave the exact range on JS and give a wrong residue). Pieces combine
  through `sadd`; a quotient past the ceiling clamps like `pmul`, byte-identical
  below it. Precondition `c != 0`, as `fdiv`.
- Site migrations (all raw `+`/`-` into fdiv):
  - `liq/bankruptcy-price` (liquidation.cljk) — `(fx/fdiv-diff entry-notional collateral size)`
  - ADL slice limit (state.cljk) — `(- (fx/fdiv-diff collateral entry-notional size))`
  - `mark/impact-mid` (mark.cljk) — `(fx/fdiv-diff a (- b) 2)`
- `bk/cancel-all!` (book.cljk) — both return-total folds (`+` outer, `reduce +` inner)
  go through `fx/sadd` (the item PR #68 deferred).

## Measured

- **BROKEN first** (tests added, sites unmigrated; kbb leg): `kbb -M:test` =
  541 tests / 3663 assertions / **5 failures** / 0 errors, KBB_EXIT=1
  (/tmp/tf-broken2-kbb.out this run). The 5 failures are exactly the designed RED
  pins, with measured values:
  - `bankruptcy-price` boundary twin 1: expected 4503599627370497, actual **4503599627370498**
  - `bankruptcy-price` boundary twin 2 (short): expected 4503599627370497, actual **4503599627370498**
  - `bankruptcy-price` clamp 2^53+1: expected i53-max, actual **9007199254740992**
  - `bankruptcy-price` clamp -(2^53+1): expected i53-min, actual **-9007199254740992**
  - `cancel-all!` total 2^53+1: expected i53-max, actual **9007199254740992**
  (JS-double leg values; the design's "RED on ALL legs" pins show the JS rounding
  here. A JVM BROKEN leg was not run.)
- **GREEN** after the fix, both runtimes, same counts:
  - SCI leg `kbb -M:test` = **541 tests / 3663 assertions / 0 failures / 0 errors,
    KBB_EXIT=0** (/tmp/tf-green-kbb.out this run)
  - JVM leg `script/jvm-test.sh` (renamed-copy recipe) = **541 tests / 3663
    assertions / 0 failures / 0 errors, JVM_EXIT=0** (/tmp/tf-green-jvm.out this run)
  - same-count cross-runtime: 541/3663 on both legs.
- One test pin of my own was miscalculated in the first BROKEN draft
  (`(fdiv-diff 3 4 -3)` — floor((3-4)/-3) = 0, not -1); the primitive was right,
  the pin was wrong. Corrected to hand-computed `floor, not trunc` cases
  (`(fdiv-diff 5 2 -2)` = -2, `(fdiv-diff -7 1 -3)` = 2) before the BROKEN record
  above. These pins are invariant pins of the new primitive (green from birth),
  labelled as such; the 5 red-green pins above are the fix-site claims.

## What is left (queue)

- **tier-shape gate** (proposed package: `evidence/2026-10-26-2027-torihiki-fix-tier-shape-door-notrun.md`):
  `:margin-tiers`/`:fee-tiers` entries are stored raw by `:list-market`/`:amend-market`
  and only range-checked — completeness never checked; a missing `:max-notional` /
  margin rate / `:min-volume` throws mid-apply inside apply-block (falsify-6/7 class,
  halted chain). Also negative margin rates pass `rates-ok?`.
- nbb leg (`script/nbb-test.sh`) not run this tick (needs KOTOBA_CHECKOUTS sibling
  checkouts); parity legs not run (measurement bots' gates).

Discovered/left by torihiki-fix tick 20261027-0418; maturity.md scores untouched
(measurement bots' file).
