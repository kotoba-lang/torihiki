# 2026-11-06 14:2x torihiki-fix — canon-colliding keys (signing-canon Finding 4) SCOPED, NOT LANDED (budget)

Branch + worktree KEPT — next tick starts here:
`bot/torihiki-fix-20261106-1424b` at
`/Users/junkawasaki/github/wt/fleet/bot-torihiki-fix-20261106-1424b`
(base origin/main 9516c4c = PR #108 merge, clean tree — no code changed this tick).

## Package chosen (highest-leverage UNLANDED item)

The carry-forward named OPEN in BOTH signing-canon records
(evidence/2026-11-06-002x-…-not-landed-broken.md Finding 4 + its handoff
paragraph, and evidence/2026-11-06-025x-…-landed.md "Left (carry-forward)"):
**canon-colliding map keys / set elements — refuse loudly per the NaN
precedent.** The EVM word-door tick (122x) completed an in-flight package
and left no queue; this is the oldest named open item.

The bug (measured in the 002x tick, JVM): a map whose keys collide under the
wire canon (`(into {} [[5 :a] [5.0 :b]])` — 2 entries on the JVM, since
`(= 5 5.0)` is false) encodes BOTH keys to bare `5`, decodes to ONE entry,
and `(= tx (wire->tx (tx->wire tx)))` is false. The wire-idempotence law
(`the-wire-round-trip-is-idempotent`, log_test.cljk) breaks the same way:
the pair-list sort ties on identical encodings, so the pair ORDER (and the
bytes) are hash-iteration-local — one tx, two logs.

## Scope (surveyed at 9516c4c, direct code reads)

Three merge points, one fix door (enc-val, the NaN precedent's exact
location — `torihiki.log/enc-val` bottom `ex-info "value has no wire form"`):

1. `enc-val` map branch (log.cljk:215) — `(into {})` at decode merges keys
   that are `=`-equal post-decode.
2. `enc-val` set branch (log.cljk:218) — `(into #{})` merges elements.
3. `tx->wire` rest-pairs (log.cljk:276) — same merge on the tx map's own
   non-keyword keys (flat keys are unique strings; a pairs key cannot
   decode to `:tx` or to a flat keyword).

Collision classes (both real, one per runtime's key identity):
- **bare vs bare**: Long 5 and Double 5.0 keys (JVM; `(into {} [[5 :a]
  [5.0 :b]])` — 2 entries) — both encode to bare `5`.
- **bare vs "u" twin**: Number 5 and BigInt 5n keys (JS; `(into {} [[5 :a]
  [(js/BigInt 5) :b]])` — IF SCI keys them apart; MUST be measured on kbb
  before the test is written) — encodings `5` and `["u" "5"]` DIFFER but
  decode to `=`-equal keys (`dec-val` "u" returns `js/Number` on JS, and
  `(= 5 (bigint 5))` is true on the JVM), so `(into {})` merges them. This
  class REQUIRES a decode-identity fingerprint, not a raw-encoding compare.

## Fix design (ready to implement next tick, TDD BROKEN-first)

`src/torihiki/log.cljk` only (signing-payload's composite rendering rides
through enc-val and inherits the refusal; `slot-str`'s "Total: it never
throws" docstring needs a one-line amendment — the collision class refuses
loudly because its payload cannot commit to the tx it renders).

1. `key-id` (private): the identity `(into {})/(into #{})` merges by, over
   ENCODED values. Normalizes bare integral integers and `["u" digits]` to
   one class (`["i" digits]`); recurses into `"m"`/`"v"`/`"set"` payloads so
   a composite key cannot smuggle the twin past the door (`[5]` vs `[5n]`).
   Everything else is its own encoding.
2. `enc-val` map branch: refuse when two entries' key-ids collide;
   set branch: refuse when two elements' key-ids collide.
3. `tx->wire` rest-pairs: refuse when two pairs' key-ids collide.
4. Refusal = the NaN precedent verbatim: `ex-info` throw at the WIRE
   (never on the apply path — enc-val is unreachable from apply-block),
   message e.g. `"torihiki.log: map keys collide under the wire canon"`,
   ex-data `{:value v}`. Decode stays TOTAL (old logs must keep decoding);
   tx->wire refusing means the encoder's domain is exactly the round-trip
   domain, and the law holds over it.

NOT in scope (noted, not claimed): `api/canon-val` (api.cljk:105) also
merges colliding keys silently (and runtime-locally — `into {}` over hash
order) inside the validate/apply front door; it cannot throw (apply-path
contract) and needs a reason-valued door at validate — its own tick.

## Tests (planned; NOT written, NOT run)

test/torihiki/log_test.cljk, overflow_gates pattern (REASON/message pins for
the refusals, VALUE pins for what still round-trips):
- colliding map keys refuse loudly (JVM construction `[[5 :a] [5.0 :b]]`,
  reader-conditional JS construction `[[5 :a] [(js/BigInt 5) :b]]` — each
  runtime's own key identity; pin ex-data/message).
- colliding set elements refuse loudly (same constructions in a set).
- colliding TX-level keys (non-keyword keys in the tx map) refuse loudly.
- nested: a colliding map inside a slot value refuses loudly.
- VALUE pins (green from birth): `{5 :a}` alone, `{5 :a 6 :b}`, `#{5 6}`,
  `{5.5 :a}` round-trip; wire-idempotence still holds for them; the NaN
  refusal is unchanged.

## Measured (exact counts)

- Host load gate: 14:17 direct `uptime` 4.77 / 2.24 / 1.78 — all windows
  < 20, gate passed (torihiki_state.sh absent at ~/.hermes/scripts/ —
  known; measured directly).
- Suite / legs / BROKEN runs: **NOT run** (budget). Nothing is claimed
  green here except runs cited from landed evidence (baseline at origin/main
  9516c4c: 675 tests / 5617 assertions / 0F / 0E on all three legs per
  evidence/2026-11-06-122x-torihiki-fix-evm-word-door-landed.md).
- "not run" != "passed"; no test in this package exists yet, so no test is
  claimed red or green.

## Handoff (next tick)

Worktree `bot/torihiki-fix-20261106-1424b` KEPT at 9516c4c + this evidence
file. Next tick: (1) MEASURE the JS construction on kbb (`(into {} [[5 :a]
[(js/BigInt 5) :b]])` entry count; `(= 5 (js/BigInt 5))`; `int-like?` on a
BigInt — the enc-val branch order assumes it falls to the "u" branch), and
the JVM construction on the jvm leg (`(into {} [[5 :a] [5.0 :b]])` = 2
entries); (2) write the tests, run BROKEN on kbb (fix withheld), then nbb
and JVM BROKEN if budget allows; (3) implement the four pieces above;
(4) GREEN ×3 identical counters (`kbb -M:test`, `script/jvm-test.sh`,
`script/nbb-test.sh` with KOTOBA_CHECKOUTS); (5) PR + merge. If the JS
BigInt construction collapses in SCI, fall back to the out-of-domain twin
`[[1e17 :a] [(js/BigInt 100000000000000000) :b]]` (identical "u" encodings —
caught without the fingerprint) and say so in the record.

maturity.md untouched (measurement bots' file).
