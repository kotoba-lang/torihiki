# 2026-10-26 06:21 torihiki-fix tick — weight sums saturate, 3x thresholds read as exact 2x forms

Branch `bot/torihiki-fix-20261026-0621` (worktree `~/github/wt/fleet/bot/torihiki-fix-20261026-0621`, base `origin/main` 2c5ec14 = PR #67 merge).

## Scope (PR #67's deferred package: falsify-14's class at a DECISION)

Raw weight/stake folds that feed verdicts — the light client's 2/3 root
threshold, the validator set's `supermajority?`, the oracle's weighted-median
walk + `aggregate-weighted` freshness threshold, the slash path's taken total
(recorded in `:slashed`, debited from `:insurance-fund`) — plus the `(* 3 x)`
threshold products themselves.

Fix: every sum folds through `fx/sadd`; every 3x threshold reads as its exact
2x form (`3h > 2t` -> `h > 2(t − h)`; `3w > t` -> `2w > t − w`), all operands
in-domain (weights non-negative, partial sum ≤ total), so every product is
twice an in-domain integer — exactly representable on both runtimes.

Sites: `src/torihiki/light.cljk` (total, weigh, 2 thresholds), `src/torihiki/oracle.cljk`
(weighted-median total + walk acc; aggregate-weighted total/have + threshold),
`src/torihiki/validators.cljk` (supermajority? 3 folds + threshold,
publisher-weights staked?, slash-account 3 folds, slash own).

## Tests — 6 deftests in test/torihiki/overflow_gates_test.cljk (VALUE pins)

RED first on the SAME final test file, all three legs measured:

- `kbb -M:test`: **532 tests / 3611 assertions / 9 failures / 0 errors** (EXIT=1)
- `bash script/jvm-test.sh`: **532 / 3611 / 7 failures / 0 errors** (EXIT=1)
- `bash script/nbb-test.sh` (KOTOBA_CHECKOUTS set): **532 / 3611 / 9 failures / 0 errors** (EXIT=1)

The 2-assertion failure-count gap IS the measured cross-runtime fork: the
`(* 3 h)` boundary assertions (`light-threshold-decides-identically-at-the-double-boundary`,
`supermajority-reads-saturated-stakes` boundary) passed on the JVM (exact longs)
and failed on the JS engines — `3 × 3002399751580331 = 2^53 + 1` rounded to
`2^53` on JS, flipping `3h > 2t` with `2t = 2^53` from true to false. The raw
sum divergence measured live on kbb: `(:total a)` = 9007199254740992 (= 2^53,
rounded) where the JVM held 2^53 + 1.

GREEN measured: `kbb -M:test` **532 tests / 3611 assertions / 0 failures / 0
errors** (EXIT=0).

NOT RUN this tick (runtime budget exhausted at GREEN): post-fix
`script/jvm-test.sh`, post-fix `script/nbb-test.sh`, parity diff
(`jvm-parity.sh` vs `nbb-parity.sh`). The cross-runtime claim is therefore
incomplete until those are re-measured — "merged" is not "both runtimes green".

## Honest notes

- Below the ceiling every value is byte-identical to before (the identities
  return the exact-arithmetic verdict; JVM pre-fix verdicts already equaled it).
- Past the ceiling verdicts read SATURATED sums — stated precision loss, the
  same clamp as every aggregate in this series (pinned as VALUEs in the tests).
- The below-ceiling guard assertions pass pre-fix; the broken-first evidence is
  the 9/9/7 failures above.
- Out of scope: `book.cljk` `cancel-all!` return-total fold (both callers
  discard the return value — informational, not root- or decision-feeding);
  `slash`'s `own` saturation is unobservable through current outputs (`min`
  against an already-saturated `fx/fmul`), folded for class consistency only.
- Unmeasured hypothesis (NOT a finding): the cljs `fdiv` leg
  (`Math/floor (/ a b)`) may round for quotients ≥ 2^52 (exact .333/.666
  fractional parts at double spacing 0.5/1) — derived analytically while
  eliminating the 3x products, never probed. Separate package if it reproduces.
