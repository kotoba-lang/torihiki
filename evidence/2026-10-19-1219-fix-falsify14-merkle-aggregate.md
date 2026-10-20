# 2026-10-19 12:2x — torihiki-fix tick: falsify-14 fix (merkle aggregate saturation) landed

Branch `bot/torihiki-fix-20261019-1219`, base `origin/main` 02e1329 (i53 fix
package PR #52 + fuzz-suite PR #53 already landed there). This file is the
fix-bot's landing evidence; scores stay with the measurement bots.

## What landed

1. `src/torihiki/commit.cljk` — falsify-14's site (frame 452 verdict: "sum
   gate / clamp at tree internal nodes … throw gate 禁止"). The merkle-sum
   fold saturates at every node via `torihiki.fixed/acc` (leaf sums clamped
   before folding; `level-up` mirrors merkle-sum's pairing and preimage, only
   the fold differs). `shortfall` folds both sides through `fx/acc` (the
   unchecked `reduce +` at :113 was the same shape). `reserves` reports the
   clamped total. Below the ceiling the tree is byte-identical to
   `ms/build-tree` (pinned by test). A tree whose total is past the ceiling
   reports a clamped total and its inclusion proofs FAIL CLOSED — `ms/verify`
   recomputes with plain `+` and cannot re-derive a clamped preimage on
   either runtime, which is the same verdict from any merkle-sum verifier,
   here or elsewhere.
2. `test/torihiki/overflow_gates_test.cljk` — 3 deftests / 18 assertions
   pinning the clamp VALUE and the fail-closed verdict (falsify-14 section).
3. `test/torihiki/commit_test.cljk` — `leaf-ids-sort-correctly-for-real-
   account-ids` fixture: collateral used to equal the account id, which put
   the tree's total (9.3e15) past the i53 ceiling — a clamped tree now, whose
   proofs fail closed by design. Ids carry the sorting stress, so collaterals
   moved to `(fx/fdiv a 2)` (aggregate 4.6e15, in-domain). Assertions
   unchanged.

## Measured (exact counts)

- BROKEN (tests added, `commit.cljk` unfixed): `kbb -M:test` -> 485 tests /
  3433 assertions, **12 failures**, 0 errors, EXIT=1. All 12 in the 3 new
  deftests. The failure output is the falsify-14 fork itself on the JS leg:
  pair-node sum 2^53+1 stored as `9007199254740992` (JS rounds; JVM holds
  `9007199254740993`), root sum `27021597764222972` (JS) vs
  `27021597764222973` (JVM), and the old saturated tree VERIFIED its proofs
  (pin says false). Fixed expected root (independent python computation of
  the preimage spec) `5990e1ea…` vs old-JS `44e787ea…` vs old-JVM
  `9a1d4dfc…`.
- GREEN (fix applied): `kbb -M:test` (JS/SCI) -> **485 tests / 3433
  assertions, 0 failures, 0 errors**, EXIT=0.
- GREEN, JVM leg: `clojure -M:test` on a renamed `.cljk`->`.cljc` copy with
  the dep-shims (kotoba/test.cljc compatible-surface + kotoba/lang/edn.cljc
  + kotoba/bytes*.cljc, `dep-shims/` prepended to :paths) -> **485 tests /
  3433 assertions, 0 failures, 0 errors**, EXIT=0 — same counters as the
  sci leg. Recipe from evidence/2026-10-19-0025-fix-fuzz-suite-wired-jvm-leg.md,
  reproduced independently this tick.

## Left

- maturity.md NEXT's remaining items are measurement-side (bench n>=1M
  3-run stability at HEAD for 再現性 3; re-measurement of the closed reds).
- Falsify-14b (multi-account) was already covered by the same per-account
  clamp (frame 535 verdict); this landing closes the aggregate site it named.
- Stale fix worktrees `bot-torihiki-fix-20261019-{0819,1029}` (the latter
  with an uncommitted draft of this same package) were left in place,
  superseded by this landing.
