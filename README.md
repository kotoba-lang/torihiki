# torihiki (取引)

A deterministic, fully on-chain exchange state machine — the open replacement
for Hyperliquid's closed HyperCore.

**Tier**: `T2` **Role**: `library` **Status**: execution layer implemented and
benchmarked; verifiable single-sequencer log implemented; not yet attached to
consensus.

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

`torihiki.mark` derives the **mark price** — what margin and liquidation are
measured against. It is deliberately not the last trade:

```
mark = oracle + clamp(impact-mid - oracle, ±band)
```

Using the last print as the mark is a way to liquidate other people: one lot
lifted through a thin book moves the mark, the mark decides who is under
maintenance margin, and the attacker collects. So the mark is anchored to the
oracle and may move only as far as the book can carry weight — `impact-mid`
is the midpoint of what a *reference-sized* order would actually pay, and the
band bounds the damage of even a well-funded manipulation. A book too thin to
price the reference size does not get to price itself; the mark is the oracle.
The last print is kept separately as `:last`, for display only.

`torihiki.trigger` holds **conditional orders** — stop-loss and take-profit.
A trigger sits outside the book, invisible and consuming no depth, until the
mark crosses its price; then it becomes an ordinary reduce-only order.

Which triggers fire is a total function of (triggers, mark), and the ORDER
they fire in is a consensus rule rather than an implementation detail: two
validators that fire them differently produce different books. So the order is
stated — by creation sequence, the one total order nobody can buy. Sorting by
trigger price would let a trader purchase priority with a fractionally closer
price.

Firing moves the book, which reprices the mark, which can arm more triggers.
That cascade is a real market event; the rounds are capped so it terminates,
and anything still armed at the cap waits for the next transaction rather than
being lost.

**reduce-only** is enforced in the clearinghouse (the book holds no positions,
so it cannot know). Oversized closes are clamped rather than rejected — the
intent is unambiguous — but the excess can never open a position on the other
side. Reduce-only orders do not rest: enforcing the check once at placement is
only sound if the order cannot outlive the position it was checked against.
Hyperliquid does let them rest; that is a stated difference.

`torihiki.api` is the request surface: validation, a closed rejection
taxonomy, and the read models a client needs (`book-snapshot`,
`account-state`, `market-info`). Pure — no transport, no serialization format.

It exists mostly because of one property: **application is total.** `apply-tx`
used to throw on a price level outside the ladder, which inside a block is not
a rejected order but a HALTED CHAIN — every validator stops at the same place,
so anyone who can submit a transaction can stop the chain with a typo. Every
transaction is now validated first; one that fails is recorded in `:rejected`
and skipped, never applied and never thrown from.

Rejections are part of the state root. Two validators must agree on what was
*refused* as much as on what was executed, or a sequencer could quietly drop a
transaction and still produce a matching root.

`torihiki.state` folds a block and computes a state root.

### The log (`torihiki.log`)

A single sequencer, and a way for anyone to check that it did not lie. Blocks
are appended to a [`kotoba-lang/chain`](https://github.com/kotoba-lang/chain)
parent-linked content-addressed commit chain — reused rather than reinvented.

Two guarantees, and conflating them is how a chain ends up trusting its own
operator:

- `chain.core/verify-chain` proves the log was **not tampered with**. Every
  commit re-derives to its own CID; `seq` increases by exactly one.
- `torihiki.log/replay` proves the recorded state roots are **correct**. A
  sequencer can publish a perfectly well-formed, untampered chain in which
  every state root is invented — only re-executing the transactions catches
  that. There is a test for exactly this case.

Storage (`put!`/`get-fn`) and signing (`sign-fn`/`verify-fn`) are injected,
so this namespace performs no I/O and imports no crypto. The second one
matters: `kotoba-lang/ed25519` is JVM-only, and importing it would stop the
browser that wants to check the log from being able to.

This is a **sequencer, not consensus**. One writer decides the order; nothing
here votes or tolerates a Byzantine peer. Saying otherwise would be the lie
ADR-2608010930 Decision 5 exists to forbid.

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

`state-root` is **SHA-256** over a tagged, length-prefixed canonical encoding,
via [`kotoba-lang/bytes`](https://github.com/kotoba-lang/bytes) — pure,
synchronous, and identical on both platforms. It is safe to sign, which
matters because `torihiki.log` does exactly that. (It was a 32-bit FNV-1a
checksum first; signing a digest with no collision resistance authenticates
every other preimage that collides with it.)

The encoding hashes the **live** state, not the preallocated slabs, so a root
costs what the book holds rather than what it could hold. It is tagged and
length-prefixed so two different states cannot encode to the same bytes.

It is still a **flat** digest: it commits to everything and proves nothing
about any part. A light client that wants to verify one balance without
replaying the chain needs an authenticated tree, and this is not one. Nor is
it incremental — a root costs the whole state, which is right for the
kilobytes a normal block touches and wrong for a book holding hundreds of
thousands of resting orders.

## Platform

`.cljc` throughout. The JVM path is the one that meets the throughput target;
the ClojureScript path exists so a browser or a Worker can replay and verify a
block without trusting a validator. `torihiki.slab` documents the one place
they differ (`long[]` versus `Float64Array`) and why the i53 domain makes them
bit-identical anyway.

**Verified, not assumed** — the two runtimes are checked against each other:

```bash
clojure -M:parity
nbb --classpath "src:<path-to>/bytes/src" -e "(require '[torihiki.parity :as p]) (p/report)"
# both must print
#   STATE ROOT  5bdc5b84b106c35aa19a4afd6e7b21361a7bf8a033698ca71d243a5e081b414e
```

That check earns its place. A JVM-side optimization — reading the `Book`
record's fields with direct interop rather than keyword lookup — silently
broke the ClojureScript path completely: `(.-lvl_head b)` does not fail there,
it returns `undefined`, and the failure surfaces much later as an array read
on nothing. The JVM suite could not observe it, and for a while the claim
above was simply false. Run `:parity` after touching `slab` or `book`.

Why not `.kotoba`, given this workspace's runtime priority? Because as of
2026-08-01 the native backend cannot host this: records are rewritten into
stack slots at codegen time and cannot cross a function boundary, there is no
provider or capability mechanism at all, and the recursive value work is still
ahead. `.cljc` is the honest choice today, and the migration target is
recorded rather than assumed.

## What is not here

- **Consensus.** Blocks are produced and ordered by ONE writer; nothing is
  agreed. `kotoba-lang/engi`
  holds chained-HotStuff safety rules and stake/slashing logic; its pacemaker,
  view change, p2p, and signature aggregation do not exist yet.
- **Networking, persistence, an API.** The log describes itself; it does not
  store itself.
- **Real signatures.** The signing seam is implemented and tested with a
  stand-in signer. Wiring an actual key is a caller's job and has not been done.
- **Multi-market.** The structure is a map of books keyed by market id, but
  only the single-market case has been run.
- **An oracle.** `:oracle` transactions carry an already-agreed price;
  aggregating validator submissions into that price is a consensus-layer job.

## Test

```bash
clojure -M:test          # 87 tests, 291 assertions
clojure -M:bench 3000000 # throughput
```
