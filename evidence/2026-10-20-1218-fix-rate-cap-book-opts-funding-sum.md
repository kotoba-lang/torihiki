# 2026-10-20 12:1x — fix landing: validate-layer rate caps + book-opts sizing + funding sample sum

Tick: torihiki-fix (one fix package). Load gate: 12:17 pre-run 15.74/13.59/13.50
— all windows < 20, gate passed. Base: origin/main faab00a (PR #54), worktree
bot/torihiki-fix-20261020-1218b.

## What landed (NEXT ②'s unlanded clause: rate caps for the fee path)

The i53 fix package (PR #52) bounded the accumulators and made `fx/mul-rate`
exact, but three doors into the falsify-6/7 class (a throw inside apply-block
halts every validator at the same place) were still open:

1. **Rates above 100% passed validate.** A `:list-market`/`:amend-market` spec
   could carry `:taker-fee-rate`/`:maker-fee-rate`/
   `:initial-margin-rate`/`:maintenance-margin-rate` (flat or inside
   `:fee-tiers`) up to i53-max. Fee/margin paths multiply rates against
   in-domain amounts (`fx/mul-rate`), and `amount x rate / rate-scale` for a
   rate past `fx/rate-scale` leaves the i53 domain — where mul-rate throws,
   mid-fill, inside the block. Now refused at the validate layer with
   `:rate-too-high` (new keyword in `torihiki.api/reasons`, code 20 appended
   to `torihiki.state/reason-codes`): every rate must be a fraction of
   `fx/rate-scale` — `|rate| <= rate-scale`, rebates included. With amount
   capped at i53-max, `mul-rate` results can no longer escape the domain from
   a validated transaction.
2. **`:book-opts` reached `bk/new-book` unvalidated.** new-book enforces its
   n-levels/cap constraints by THROWING and `ev-cap` reached no bound at all;
   a `:list-market` with `:book-opts {:n-levels -32}` threw
   `RangeError: Invalid typed array length: -64` inside apply-tx (measured in
   the broken run below) — one signed tx halting the chain through a door
   validate had already passed. Now: `bk/book-opts-ok?` (positive integers,
   n-levels/cap multiples of 32 within the ladder bounds, ev-cap bounded by
   `bk/max-slots`), refused at validate with `:missing-field`, and
   `apply-tx :list-market` no-ops on the same predicate as the engine safety
   net (new-book keeps throwing for direct callers).
3. **`funding/sample` folded `:sum` with `(fnil + 0)`** — the falsify-8..13
   accumulator class in the funding path. Now `fx/acc` (saturating).

## Measured

- BROKEN leg (tests added, fix absent): `kbb -M:test` = 488 tests / 3453
  assertions, **13 failures + 1 error** (exit 1) — inventory exactly:
  `rate-caps-refuse-above-the-ceiling` 4 FAIL,
  `book-opts-the-engine-would-throw-on-are-refused-at-the-door` 7 FAIL + 1
  ERROR (the RangeError above), `the-funding-sample-sum-is-an-accumulator-
  like-every-other` 2 FAIL. No other test red (/tmp/tori-fix-broken.out).
- FIXED leg: `kbb -M:test` = **488 tests / 3453 assertions, 0 failures,
  0 errors** (exit 0). Mid-fix, the taxonomy test
  `every-refusal-reason-has-its-own-code` was red once (`:rate-too-high` had
  no code) and green after appending code 20 — red before fixed, as required.
- **JVM leg reproduced** (renamed `.cljk`→`.cljc` copy + kotoba-lang dep
  shims, `/Users/junkawasaki/.hermes/profiles/torihiki-fix/cache/scratch/
  tori-jvm-1218`, `clojure -M:test`): **488 tests / 3453 assertions, 0
  failures, 0 errors** (exit 0) — same counts as the kbb leg
  (/tmp/tori-jvm-leg.out).

Counts vs base faab00a: 485 tests / 3433 assertions → 488 / 3453 (+3 tests,
+20 assertions).

## Left (observed this tick, not in this package)

- `funding/premium` computes `(* diff fx/rate-scale)` raw — the falsify-9
  secondary product shape — but with book prices level-indexed the product is
  bounded by `n-levels x rate-scale` and does not overflow in reachable
  books; unmeasured, left alone.
- `apply-fill-to-position` computes `realized` as a raw difference of two
  in-domain values (can reach 2^54 before `fx/acc`); `mark-price` adds
  `oracle + clamp(...)` raw; position `:size` accumulates with raw `+`. Same
  class as the accumulators, unmeasured by falsify, candidates for the next
  package.
- NEXT ① (re-measurement of HEAD) and the bench 3×n=1M stability measurement
  are measurement-bot work, unchanged by this landing.
