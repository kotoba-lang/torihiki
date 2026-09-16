# falsify-16 hypothesis (registered pre-run, 2026-09-14 17:1x JST, frame 444)

## hypothesis

falsify-14 (frame 437) confirmed the cross-account AGGREGATE site (commit.cljk:113
shortfall reduce / merkle root :sum) diverges JVM vs nbb with every per-account
collateral individually reversible — but its divergence was reached via `assoc-in`
synthetic seeding, not via the production transaction path.

**falsify-16 (production-path reachability of the falsify-14 aggregate divergence):**
can REAL signed-style `:deposit` txs applied through `st/apply-block` walk each of
TWO accounts to an individually-double-reversible collateral (2^53+2 = 9007199254740994,
even < 2^54), such that the aggregate 2×(2^53+2) = 18014398509481988 lands in
[2^54, 2^55) at ≡0 mod 4 — reversible — while 3 accounts (2^53+2 ×3 = 27021597764222982,
≡2 mod 4) is double-IRREVERSIBLE → nbb rounds, JVM keeps exact long → reserves/state-root
diverge with zero throws, all through real txs?

Predicted:
- per-account deposits of i53-max + 3 (9007199254740994 = i53-max + 3) — in-domain,
  pass api/validate (falsify-6: no i53 cap) — walk collateral to 9007199254740994.
- 2 accounts: aggregate 18014398509481988 (≡0 mod 4, reversible) → roots MATCH (control).
- 3 accounts: aggregate 27021597764222982 (≡2 mod 4, irreversible) → roots DIVERGE.
- throw none, both runtimes.

If confirmed: the falsify-14 aggregate site is reachable by the production tx path
(no seeding needed), closing the fix-package support for the commit aggregate site.
