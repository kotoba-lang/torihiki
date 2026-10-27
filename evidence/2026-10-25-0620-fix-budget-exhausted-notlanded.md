# 2026-10-25 06:20 — torihiki-fix tick: NOT LANDED (run budget exhausted)

- Host load gate: PASS (10.45 / 10.64 / 12.24, all windows ≤ 20).
- No PR, no merge, no RED/GREEN test runs. Nothing landed. Worktree
  ~/github/wt/fleet/bot/torihiki-fix-20261025-0620 (branch bot/torihiki-fix-20261025-0620,
  at origin/main dae7422) created but unmodified.

## What this tick established (for the next tick to pick up directly)

1. maturity.md NEXT ② (validate-i53-halt fix package) is ALREADY LANDED at
   origin/main across PRs #56 (fx/acc sweep), #58 (validate i53 caps +
   :bad-amount / notional cap / balance-domain gate), #64 (pmul/sadd totality),
   #65 (slab/:book-full). NEXT is stale relative to HEAD (updated to ~2026-10-18;
   PRs #56–#65 landed 2026-10-20..25). fuzz suite is wired (test/torihiki/fuzz_seeded_test.cljk).
   Repro-3 (bench n=1M 3x stable) landed via PR #59.

2. The remaining UNLANDED fix item = the in-flight worktree
   ~/github/wt/fleet/bot/torihiki-fix-20261023-2023 (uncommitted, base b6b4cb4):
   `fx/fmul` — exact floor(a x b / c) without forming the product (fixed.cljk +80)
   + 4 test groups (overflow_gates_test +76, validators_test +20). This is NEXT ②'s
   sub-item (f) falsify-11 secondary (REDUCING basis) and the same class at
   vault share price, ADL score, slash reward. NEXT ① asked to confirm this
   in-flight work lands or reverts first — it is NOT landed at HEAD
   (fixed.cljk at dae7422 has no fmul/halve-mul/nn-mul).

3. Complete catalogue of product-then-divide sites at dae7422 (the defect class:
   product > 2^53 = cross-runtime rounding divergence; > 2^63 = JVM bare overflow
   halt + JS silent rounding):
   - clearing.cljk:170  REDUCING basis  (fx/fdiv (fx/pmul en closed) size) — pmul-clamped
     (retires basis 4503599 instead of 360e12 for the falsify-11 numbers)
   - clearing.cljk:290  vault-deposit  (quot (* amount total) pool)
   - clearing.cljk:317  vault-withdraw (quot (* shares pool) total)
   - liquidation.cljk:152/155  ADL leverage + score (bare *)
   - validators.cljk:338  slash reward (quot (* total slash-reward-percent) 100)
   - funding.cljk:68  premium (fx/fdiv (* diff rate-scale) oracle-price) — reachable
     only via the NEGATIVE branch (oracle >> ladder prices; diff = -(oracle - ia));
     the positive branch is bounded by max-n-levels 2^20 (product < 2^53, exact)
   - oracle.cljk:131  deviation (fx/fdiv (* |price-ref| rate-scale) reference)

4. All pinned values for the in-flight tests verified EXACT with Python integer
   math (B/C/D/E/F in scratch pins.py): reducing basis 360000000000000 /
   entry 360000000000000 / collateral 361000000000000; vault minted
   4913264919471391; vault payout 5162165815587510; ADL score 1515362229667056
   (old kbb double gives ...057 — RED-able); slash reward 360287970189641,
   insurance-fund 6845471433603179 (old kbb double gives ...640 — RED-able).
   fmul unit pins all verified.

5. Old-vs-new kbb-double divergence pins for funding/premium and oracle/deviation
   still need value search (pins_search2/3.py in scratch); a robust deviation
   clamp pin exists: (deviation 5000000000000007 7) must clamp to i53-max
   (old code returns 7.14e23 out-of-domain).

## Leftover stale state (unchanged by this tick)
- worktree bot-torihiki-fix-20261023-2023: the fmul in-flight diff (land it or
  revert after landing the ported version)
- worktree bot-torihiki-fix-20261024-1819: stray one-line require in
  overflow_gates_test (already present at HEAD — fully superseded, safe to revert)
