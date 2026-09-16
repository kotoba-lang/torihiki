# falsify-14 verdict — cross-account merkle aggregate overflow (CONFIRMED)

Frame 452, 2026-09-15 ~05:06 JST. No code changes. HEAD 0a99c9c2 (src/ diff 0 lines).
Load gate passed (direct 15.92/16.95/16.42, all windows <20). Suite 139 measured first
(JVM + nbb 357/915 0F/0E EXIT=0, evidence/f452-* in /tmp, mirrored below).

## Question (registered 2026-09-14-0222-falsify14-hypothesis.md)

Does the CROSS-ACCOUNT aggregation (merkle-sum tree root sum over canonical leaves)
diverge JVM/nbb when every per-account collateral is double-reversible but the SUM is not?
This is the last unmeasured accum-class site — per-account accumulation is settled by
falsify-8/9/10/11/12/13.

## Measurement

Harness: evidence/falsify14-multi-aggregate.cljk (unmodified src; collateral seeded via
assoc-in on [:clearing :accounts N :collateral], real st/canonical-leaves + cm/reserves +
st/state-root). Drivers: falsify14-{jvm,nbb}-driver.cljk. Outputs: falsify14-{jvm,nbb}.out
(err both 0 bytes, both EXIT=0, throw 皆無).

- probe aggregate-sum 3 × 2^53+2 (per-leaf even, reversible; sum = 27021597764222982,
  ≡2 mod 4, in [2^54,2^55) → double-irreversible):
  - JVM reserves **27021597764222982** root **48ca7e61**3c72e3ad…
  - nbb reserves **27021597764222984** (rounded) root **55a6cf06**6f177cff…
  - → root divergence, zero throws, every per-account value in-domain.
- control 3 × 2^53 (aggregate 3·2^53 ≡ 0 mod 4, reversible):
  reserves 27021597764222976, root **8b1ba1bd**… identical on both runtimes —
  isolates the divergence to irreversibility of the aggregate, not the tree path.

## Site

`torihiki.commit/reserves` (commit.cljk:115 → `(:sum (:root (tree leaves)))`) and the
merkle-sum tree internal-node sums fold with `reduce +` and no domain check; the same
un-checked sum appears in `shortfall` (:113). Not a `(fnil + 0)` accumulator — a NEW
shape: **hierarchical aggregation**. Root hash (not just a scalar field) diverges, so
the signed sequencer log itself forks across runtimes. Fix package gains a 7th site:
sum gate / clamp at tree internal nodes (validate 層か no-op, throw gate 禁止 — same rule).

## Verdict

CONFIRMED — 発覚 8 件目, 反証 16 件目, OPEN 赤 8 件目. Score 反証 stays 3
(常設化未達). Reproduces falsify-8 divergence rule (i) with all leaves reversible —
irreversibility born at the aggregate node, not imported from an account.

Suite 139 (both runtimes) measured this frame; parity/fuzz/bench/falsify-16-instrumented
not-run (budget). Next frame = 453.
