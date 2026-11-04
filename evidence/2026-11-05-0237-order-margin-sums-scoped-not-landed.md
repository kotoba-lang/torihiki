# order-margin #50 scoped — running sums at the mark, NOT landed (budget)

Tick 2026-11-05 02:3x. Load gate passed (1.34/1.50/1.40 at 02:23, all < 20). Queue check at
origin/main 8f4a81d (PR #101 landed 00:4x): maturity.md NEXT verifies fully landed, findings
carried forward none — the sole in-scope open item is issue #50 (order-margin O(levels) →
running sums). This tick scoped and designed it; budget ran out before implementation. Branch
`bot/torihiki-fix-20261105-0237` (this commit) is the handoff. No src/test change, no PR.

## Why the obvious designs are wrong (measured reasoning, read from the tree)

`torihiki.order-margin/market-requirement` walks every resting level of the account on each
of ~4 requirement calls per order. The query needs, per (acct, mkt, side): total qty (bq/aq),
loss-b = Σ_{l>mark}(l−mark)·q, loss-a = Σ_{l<mark}(mark−l)·q, and Σ l·q for the no-mark branch.

- Plain running sums of Σl·q / Σq cannot answer the loss split: the identity
  loss = suffix_lq − mark·suffix_q forms a product mark·Q that routinely leaves the i53 domain
  (mark 1e6 × Q 2e10) while the true loss is small — and today's fold forms per-level
  (l−mark)·q terms that stay in-domain by the validate notional cap (l·q ≤ i53-max ⟹
  (l−m)·q ≤ l·q for m ≥ 0). So an aggregate identity would either throw where today computes,
  or clamp where today is exact. Fenwick/prefix-sum structures hit the same wall: their
  internal sums pass 2^53 (e.g. Σlq up to ~max/initial-margin-rate is reachable), which is the
  falsify-8…13 divergence class on nbb.
- Unbounded exact storage is impossible cross-runtime (JS doubles round past 2^53 — the whole
  falsify ledger is this fact).
- A signed incremental saturating sum diverges from a rebuild fold after a cancel that follows
  a saturation (hidden excess survives the subtraction). #41's lesson (cache kept notionals,
  threw inside apply-block) additionally forbids any checked accumulator here: everything folds
  through `fx/acc`/`fx/pmul` (total, never throws).

## Design that closes all of it (implement next tick)

Keep the `:open-orders` entry shape `{:bids {l q} :asks {l q}}` and add `:sums` per entry:

    :sums {:mark m0
           :bids {:q :lq :loss :sat?} :asks {:q :lq :loss :sat?}}

- `:q` = Σq, `:lq` = Σ l·q, `:loss` = the mark-split loss at `:mark` (bids above / asks below).
  All folds are `fx/acc`/`fx/pmul` — total, never throw. Exact whenever true sums fit the
  domain; the clamp equals today's `reduce fx/acc` result (min(i53-max, true)) since all terms
  are sign-fixed.
- `adjust`/`take-consumed` gain a `mark` parameter (call sites all hold `ex`, so
  `(get-in ex [:marks market] 0)`). Delta fold at the entry's own `:mark` is O(1). On a mark
  mismatch the entry is REKEYED (canonical recompute O(L) once per mark move per entry); on a
  clamp the side gets `:sat? true`; on a NEGATIVE delta while `:sat?` the side is recomputed
  canonically from the levels — this is what kills the hidden-excess divergence.
- Canonical fold `side-view` (sorted by level, `pmul`+`sadd`) is the single definition: the
  cache-equals-rebuild invariant then holds BY CONSTRUCTION (rebuild folds `adjust` from empty;
  incremental folds maintain the same min(max, Σ) value; recomputes reset hidden excess).
- `market-requirement` uses `(:sums entry)` when `(= mark (:mark sums))`, else falls back to a
  pure canonical fold — worst case equals today's cost, never wrong, no storage needed in the
  pure path. Stable marks ⇒ O(1) per query; per-order cost becomes O(markets) + O(1) per
  order, plus one O(L) per mark move per (acct, mkt).
- `rebuild` gains a marks parameter (3rd arity, default {}) and fills `:sums` at
  `(get marks mkt 0)`; the three call sites pass `(:marks ex)` — state.cljk:1495
  (:amend-market rederive), state.cljk:1993 (v2 activation), snapshot.cljk:107 (restore).
  `in-sync?` in the test then compares sums too — a STRONGER property than today.
- `prune`/`clear` drop `:sums` with their entry. `snapshot.cljk:91` already `dissoc`s
  `:open-orders` on capture, so no serialization shape rides anywhere.

Behavior note to pin in the PR: for raw out-of-domain per-level terms (only constructible
without the validate notional cap), today's fold throws (`fx/notional`) and `orders-allowed?`
refuses via catch; the new fold clamps (`fx/pmul`) and decides on values — total, consistent
between cached and fallback paths. In-domain terms (every validated order) are byte-identical,
so v1–v4 roots are unchanged. Pin with VALUE tests per the overflow-gates pattern.

## Test plan (each new test run BROKEN first against unfixed src)

1. `in-sync?` helper → `(om/rebuild (:books ex) (:markets ex) (:marks ex))` — arity-errors on
   unfixed src (BROKEN), and every existing call site then checks sums too.
2. New deftest: resting orders at several levels; assert `(om/requirement …)` equals pinned
   literals at the original mark AND at a moved mark (the pure-fallback path), plus
   cache-vs-fold equality of `:sums` after place/cancel/fill/amend/restore sequences.
3. New deftest (saturation hidden-excess, unit level on `om/adjust`): e.g. mark 100, bids at
   level 150, four +6e13 adjusts (loss 3e15 each → Σ 1.2e16 saturates at 9007199254740991),
   then one −6e13: pin the recomputed value 9000000000000000 (= 3×3e15). Without the
   sat?-on-negative recompute the value would be 9004199254740991 — pin the REASON/VALUE
   distinction per overflow_gates_test style.
4. New deftest: `om/adjust` never throws on out-of-domain level×qty deltas (pmul clamp VALUE
   pin) — the #41 no-halt guarantee on the apply path.

## Bench plan (acceptance 3)

`bench/torihiki/order_margin_bench.cljk` (or extend bench.cljk runner): one account resting
2,000 one-lot orders at distinct levels vs the few-levels case; measure per-order
`om/orders-allowed?` (the ~4 calls/order path) with STABLE marks — expect flat (O(1) hits);
report the mark-move scenario separately and honestly (one O(L) rekey per move per entry).

## Left

Everything: implementation, BROKEN/GREEN runs on `kbb -M:test` + `script/jvm-test.sh` +
`script/nbb-test.sh`, bench numbers, PR. Next tick starts from this branch or re-scopes
against it.
