# torihiki (取引)

A deterministic, fully on-chain exchange state machine — the open replacement
for Hyperliquid's closed HyperCore.

**Tier**: `T2` **Role**: `library` **Status**: execution layer implemented and
benchmarked; verifiable single-sequencer log implemented; not yet attached to
consensus.

## Which id a key may claim

An account id was claimed by first use. Under a single sequencer the owner is
always first, so this looked fine and shipped.

Under consensus it is not fine. **The party ordering transactions sees a
pending binding before it commits and can insert its own binding for that id
first** — and then the genuine owner is refused `:wrong-key` on their own
account, permanently. A Byzantine leader did exactly that in
`engi`'s `torihiki-on-engi` harness: account 1 ended at −50, exactly the
thief's order, while 34 of the owner's transactions were refused.

With a `derive-fn` supplied, a key may only bind the id derived from it:

```clojure
(auth/check ex envelope chain verify-fn derive-fn)
;; own id        -> nil
;; somebody else's -> :not-your-account
```

Already-bound accounts are untouched — derivation decides who may **claim** an
id, not who may keep using one they hold, or changing the derivation would
confiscate every existing account.

### The old note here had the comparison backwards

It said deriving the id trades a registration race for an account collision,
and that this was worse. It is not. **A collision is refused** — the second
key gets `:wrong-key`, sees it, and can use another key — while **the race is
silent, permanent, and profitable for whoever orders the transactions**. And
the slab holds i53 rather than 32 bits, so a collision needs tens of millions
of accounts rather than tens of thousands.

Without a `derive-fn`, ids stay first-come, which is correct for replaying a
history already agreed and wrong anywhere a proposer can front-run — so it is
a decision the caller makes rather than a default.

`torihiki.address/derive` is the concrete one, in one place for everybody who
has to agree on it: the engine, the node, and the browser deciding which
account to trade as. **One implementation and not three** — a browser
computing its own address while the chain computes a different one is a user
who cannot sign for the account they are shown, and the symptom is a rejection
naming the account, which reads as a permissions problem rather than an
arithmetic one. The test pins two known keys to two known ids for exactly that
reason: a derivation that drifts locks somebody out and nothing else notices.

## Collateral has to come from somewhere

Every number the clearinghouse produces — initial margin, maintenance margin,
the liquidation waterfall, the insurance fund, auto-deleveraging — is exact
arithmetic over collateral. None of it says where the collateral came from.

`:deposit` credited whichever account signed it, for any amount. So any
account could conjure collateral and every downstream number stayed exactly
correct, which is what made it invisible: nothing is wrong with the
arithmetic, the inputs are simply not backed by anything.

An exchange may now name a **bridge authority**, and then only it may credit
an account:

```clojure
(st/new-exchange {:market m :bridge-authority 900})
;; account 900 -> ok
;; anybody else -> :not-the-bridge
```

Configured closes the door, exactly as `:oracle-publishers` closes the direct
price setter. Leaving both open would make the authority decorative, since an
attacker would use the door that does not check.

Withdrawal is deliberately not gated: a holder moving their own collateral out
needs no authority beyond their signature, and the clearinghouse already
refuses to take more than is free.

**The default is nil, and the deployed devnet leaves it nil** — so on
`torihiki-node` today, any account credits itself. That is a devnet faucet,
and `/head` says so in its own response rather than leaving it to be inferred
from the absence of a bridge. The other half of a real deposit — something on
another chain that actually received the value — does not exist yet, and a
withdrawal here decrements a balance and pays out nowhere.

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
`torihiki.liquidation` (book → backstop vault → insurance fund → ADL, all four
stages executing) are
ordinary immutable Clojure data. They run once per fill or once per hour, not
once per order, so clarity is worth more there than speed — and clarity is
worth a great deal, because that is where money is actually lost.

`torihiki.clearing` also carries **margin tiers** and **open interest**. A
market may declare tiers, and a position pays the rate of the tier its
notional falls into — past the last tier the last tier applies, so a position
does not escape the schedule by being bigger than anyone anticipated. Without
tiers every position pays the same rate regardless of size, which is how a
large position ends up under-collateralised: the book cannot absorb it at the
price the margin assumed, and the difference is paid by the insurance fund or
by ADL.

Open interest is maintained incrementally on every fill rather than summed on
demand — summing would make a risk check cost a walk of every account. A
market may declare a cap, and `torihiki.api` refuses orders that could breach
it. The check is deliberately conservative: whether an order opens new
interest depends on which side the counterparty is on, which is not known
until it matches, so it assumes the whole order could. Over-rejecting near the
cap is the safe direction — the other one discovers the breach only after it
cannot be undone.

`torihiki.oracle` decides where the external price comes from. The mark is
anchored to the oracle precisely so a thin book cannot be used to liquidate
people — and until this existed, `:oracle` was a transaction carrying an
already-agreed price, so whoever could send it moved the mark. The hole had
been pushed one layer down, not closed.

The aggregate is the **median** of fresh submissions from authorised
publishers. A mean lets one publisher move the result by however much they are
willing to lie; with a median a minority cannot move it at all, however
extreme their submissions. For an even count the lower middle is taken —
which of the two matters little, that it is stated matters.

Below quorum there is **no price**, not a bad one, and the oracle is marked
stale rather than silently reused. **A stale oracle stops liquidation**: bad
debt can grow while the feed is down, and that is the smaller cost, because a
liquidation is irreversible and waiting is not.

With publishers configured the direct setter is closed. Leaving both doors
open would make the aggregate decorative — an attacker would use the one that
does not aggregate.

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

`torihiki.auth` answers **who is allowed to move an account**. Until it
existed, `:account` was a number a client asserted — anyone could submit a
transaction claiming to be account 5.

It looks like a transport concern and it is not: two of its three parts are
consensus state. The **nonce** is per-account state and every validator must
agree on it or they disagree about what is a replay; the **key binding** is
the same. Only the framing belongs to a transport, and that is the easy half.

The signed payload covers the chain id (a testnet signature must not authorise
a mainnet transaction), the account, the nonce, and every field that can
change what the transaction does. Nonces are strictly sequential — a gap would
let a key holder sign several transactions and choose their order later.

An authenticated transaction that then fails validation **still spends its
nonce**: the holder authorised that nonce for that transaction, and leaving it
unspent would keep the signature reusable. A failure of authentication spends
nothing, because such a message was never from the account.

Verification is injected (`verify-fn`), so this namespace imports no crypto —
same reason as `torihiki.log`.

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
#   STATE ROOT  22f75a7ff4777d1c5ae397f47aa2b62b08aba8f2ba131aa6ae506b156cd9c87e
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
clojure -M:test          # 134 tests, 405 assertions
clojure -M:bench 3000000 # throughput
```
