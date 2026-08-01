# torihiki (取引)

A deterministic, fully on-chain exchange state machine — the open replacement
for Hyperliquid's closed HyperCore.

**Tier**: `T2` **Role**: `library` **Status**: execution layer implemented and
benchmarked; not yet attached to consensus.

## What this is

Hyperliquid is not an EVM chain with an exchange deployed on it. Its trading
engine *is* the state machine, and its EVM sits beside that as a deliberately
slower composability surface. `torihiki` is that same idea, built here: the
order book, the clearinghouse, funding, and the liquidation waterfall as one
pure transition

```
(state, ordered-txs) -> (state', events)
```

with no consensus, no networking, and no I/O inside it. Consensus decides the
ORDER of transactions and nothing else; this library decides what that order
means. That split is what lets a validator *verify* a block by replaying it
rather than trusting whoever produced it.

HyperCore is closed source — `hyperliquid-dex/node` ships signed binaries and
documentation, not code — so nothing here is ported. It is derived from the
published specification and from first principles, and the places where the
specification is subtle are called out in the source.

## Measured throughput

```
$ clojure -M:bench 10000000

  operations       10,000,000
  placed            4,501,960
  cancelled         2,918,203
  resting at end      839,431
  elapsed              3.169 s

  THROUGHPUT        3,155,313 ops/sec
  latency                 317 ns/op
```

Reference: HyperCore is documented at roughly **200,000 orders/sec**. This is
**15.8x** that figure — on one core of an Apple M-series laptop, with other
work running on the machine.

Read that number for what it is. It measures the matching engine under a
steady-state workload (43% cancels, 45% passive quotes, 12% aggressive orders
that cross, book holding ~840k resting orders). It does **not** include
consensus, networking, signature verification, or persistence, and those are
what will actually set a live chain's block rate. What it establishes is that
the execution layer is not the bottleneck, which is the only claim it should
be used for.

Reproduce with `clojure -M:bench <n-operations>`. The benchmark asserts
nothing and gates nothing, so it can only rot in one direction: silently. If
you change the engine, re-run it.

## Design

### The book (`torihiki.book`)

Three preallocated structures, O(1) on every path:

- a **price ladder** indexed directly by price — a price is not a key to look
  up, it is the array index
- an **intrusive FIFO** per level, so time priority is maintained rather than
  sorted
- a **four-level bit ladder** that answers "best bid / best ask" in a bounded
  number of word operations regardless of how sparse the book is

Order ids encode `slot * 2^20 + generation`, so cancel resolves by arithmetic
instead of a hash lookup, and the generation counter stops a stale cancel from
hitting whoever inherited a reused slot.

### Everything else

`torihiki.clearing` (positions, cross/isolated margin, the solvency test),
`torihiki.funding` (Hyperliquid's premium-index formula), and
`torihiki.liquidation` (book → backstop vault → insurance fund → ADL) are
ordinary immutable Clojure data. They run once per fill or once per hour, not
once per order, so clarity is worth more there than speed — and clarity is
worth a great deal, because that is where money is actually lost.

`torihiki.state` folds a block and computes a state root.

## Determinism, and how it is enforced

Two validators that disagree on one bit produce different state roots and the
chain halts. Every rule below exists because breaking it is easy:

- **No floating point.** All values are integers in the i53 domain
  (`torihiki.fixed`), checked on the way into storage. i53 rather than i64
  because that is the widest integer both the JVM and JS represent exactly,
  and a state machine whose two implementations disagree above 2^53 is worse
  than one that refuses those values outright.
- **One rounding rule.** Floor division is the only primitive; everything else
  is expressed in terms of it, so "which way does this round" has exactly one
  answer.
- **No unordered iteration.** Every fold over accounts sorts first. Clojure
  map iteration order is unspecified, and this workspace has already recorded
  one case where a map silently stopped being ordered past eight entries.
- **No wall clock.** Logical time arrives in the block header. Liquidation
  cooldowns read it, never `System/currentTimeMillis`.
- **Total orderings, not partial ones.** The ADL ranking breaks ties by
  account id, because two nodes sorting equal-scored accounts differently
  would deleverage different traders.

`state-root` is a 32-bit FNV-1a checksum, the same algorithm on both
platforms. It detects divergence between replays. It is **not** a
cryptographic commitment: not collision-resistant, no proofs about parts of
the state. A production chain needs SHA-256 over a canonical encoding, and
light clients would need an authenticated tree.

## Platform

`.cljc` throughout. The JVM path is the one that meets the throughput target;
the ClojureScript path exists so a browser or a Worker can replay and verify a
block without trusting a validator. `torihiki.slab` documents the one place
they differ (`long[]` versus `Float64Array`) and why the i53 domain makes them
bit-identical anyway.

Why not `.kotoba`, given this workspace's runtime priority? Because as of
2026-08-01 the native backend cannot host this: records are rewritten into
stack slots at codegen time and cannot cross a function boundary, there is no
provider or capability mechanism at all, and the recursive value work is still
ahead. `.cljc` is the honest choice today, and the migration target is
recorded rather than assumed.

## What is not here

- **Consensus.** No blocks are produced, ordered, or agreed. `kotoba-lang/engi`
  holds chained-HotStuff safety rules and stake/slashing logic; its pacemaker,
  view change, p2p, and signature aggregation do not exist yet.
- **Networking, persistence, an API.**
- **Multi-market.** The structure is a map of books keyed by market id, but
  only the single-market case has been run.
- **An oracle.** `:oracle` transactions carry an already-agreed price;
  aggregating validator submissions into that price is a consensus-layer job.
- **Cryptographic state commitments.** See above.

## Test

```bash
clojure -M:test          # 40 tests, 119 assertions
clojure -M:bench 3000000 # throughput
```
