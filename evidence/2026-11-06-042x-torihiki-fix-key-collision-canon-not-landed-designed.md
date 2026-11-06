# 2026-11-06 04:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# queue verification + measured collision probes + fix/test design). No src
# change, no test written, no PR. "not run" != "passed": nothing is claimed
# green this tick except the probe outputs quoted below. maturity.md untouched
# (measurement bots' file).

Load gate: torihiki_state.sh ABSENT (chronic-fault precedent); uptime direct
04:18 — 1.40 / 1.29 / 1.27, all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261106-0422, branch
bot/torihiki-fix-20261106-0422 off origin/main 324a9ec (PR #107 merge) —
KEPT as the handoff for the next tick. HEAD 324a9ec verified at fetch.

## Queue check (measured)

maturity.md NEXT is STALE — every fix item landed (independently re-verified
by the 0028 / 224x / 1427 / 1821 / 202x queue checks; PR #100's record closed
the last carried finding). The 22:2x landing record's "Left" list is itself
stale on both items: venue sums landed as PR #104 (GREEN x3,
evidence/2026-11-05-1626-...-landed-3legs.md) and the wire-number-canon
decode pins are already WIRE-level (test/torihiki/log_test.cljk
the-wire-round-trip-is-idempotent + `==` recovery pins, the 142x correction
applied). Open issue #76 (terminal:) is out of scope per tick boundaries.

The live queue head is PR #107's carry-forward Left: **"Map keys that collide
under the canon (5 vs 5.0 → both bare 5) break the round-trip law — refuse
loudly per the NaN precedent"** — this tick's package.

## Finding (code-read + probes, measured): the collision class is a silent
## merge at three doors and a non-deterministic fold at a fourth

`torihiki.log` encodes map entries sorted by `(pr-str (enc-val k))` and
decodes with `into`. Distinct keys with ONE canon form (5 vs 5.0 — both bare
5) therefore: (1) TIE the sort at `enc-val`/`tx->wire`, so the pair order
falls back to hash iteration — the determinism failure the enc-val docstring
itself warns about ("an unsorted pair list would make the CID depend on hash
iteration order" — a sort with ties is no sort); (2) MERGE silently at
`dec-val` "m" / "set" and `wire->tx` (`into`) — the round trip LOSES an
entry, `(= tx (wire->tx (tx->wire tx)))` false, no throw. Measured this tick
on the JVM leg (clojure -M, openjdk 27): `(into {} [[5 :a] [5N :b]])` ->
`{5 :b}`; `(count {5 :a 5.0 :b})` -> 2. Same class as the NaN refusal: the
wire cannot distinguish the entries, so the codec must refuse loudly rather
than mangle.

### Probe ground truth (measured this tick, kbb/SCI + JVM)

JVM (clojure -M /tmp/probe-collide-jvm.clj, EXIT=0):
- `(count {5 :a 5.0 :b})` = **2** (the collision map is JVM-constructible)
- `(count (hash-map 5 :a 5N :b))` = 1 (5/5N are map-equal; unconstructible)
- `(count (hash-map [5] :a [5.0] :b))` = 2
- `(into {} [[5 :a] [5N :b]])` = `{5 :b}` (silent merge), `(into {} [[5 :a]
  [5.0 :b]])` = 2 entries
- `#{5 5.0}` = 2 elements; `(= 5 5N)` = true

kbb/SCI (kbb --backend sci, /tmp/probe-collide-kbb.cljk + /tmp/probe2.cljk):
- `(count (hash-map 5 :a 5.0 :b))` = 1 (JS merges 5/5.0 at construction —
  the collision pair must be built differently per runtime)
- `(count (hash-map 5 :a (js/BigInt 5) :b))` = **2** — `(= 5 5n)` is FALSE
  on SCI, so 5 vs js/BigInt 5 is the SCI-constructible collision pair
- `(into {} [[5 :a] [5 :b]])` = `{5 :b}`; `(into {} [[5 :a] [5n :b]])` keeps
  2 raw — BUT wire->tx decodes ["u" "5"] via `js/Number`, so both keys
  decode to Number 5 and merge at dec — the round trip breaks there
- `(conj #{5} (js/BigInt 5))` = 2 elements (set twin pair for SCI);
  `(conj #{5} 5.0)` = 1
- `#{5 5.0}` as a LITERAL is a cljs READER error ("Set literal contains
  duplicate key: 5") — the tests must construct sets programmatically, never
  as literals
- `(integer? (js/BigInt 5))` = false (api/int-like? answers false for a
  BigInt; enc-val's `instance? js/BigInt` branch is what encodes it)
- `(declare foo)` works on SCI (probe answered 42) — the mutual reference
  the fix needs is safe on all three legs

## Designed fix (NOT implemented — next tick, in this order)

Criterion (one, at every door): two keys/elements collide iff their
DECODED CANON FORMS are `=`: `(= (dec-val (enc-val a)) (dec-val (enc-val
b)))`. Subsumes every measured variant: identical encodings (5 vs 5.0),
decode-merge across wire forms (5 vs 5n on SCI — wire forms bare 5 vs
["u" "5"] differ but both decode to Number 5), and re-encode collision
(crafted ["m" [[5 x] [["d" "5.0"] y]]] decodes to `=`-distinct 5/5.0 that
re-encoding would refuse). Needs `(declare dec-val)` above `enc-val`.

Refusal shape per the NaN precedent (loud ex-info, like "value has no wire
form"), REASON literals to pin:
- enc map keys (nested maps AND tx-level entries — a tx is a map):
  `"torihiki.log: map keys collide under the wire canon"` {:key k}
- enc set elements: `"torihiki.log: set elements collide under the wire
  canon"` {:element e}
- dec "m" and wire->tx (kind + flat + pairs decoded keys):
  `"torihiki.log: wire map keys collide"` {:key k}
- dec "set": `"torihiki.log: wire set elements collide"` {:element e}

Sites (6): `enc-val` "m" keys, `enc-val` "set" elements, `tx->wire` entry
keys (flat + rest checked uniformly), `dec-val` "m" decoded keys, `dec-val`
"set" decoded elements, `wire->tx` decoded keys (the kind slot included —
crafted `_pairs` key ["k" "tx"] collides with the :tx kind key today).

## Test design (NOT written — BROKEN-first per the overflow_gates pattern)

Refusal pins (red before the fix: no throw today), thrown-with-msg? with the
REASON literals above:
- enc map, portable collision pair via reader conditional:
  `#?(:clj {5 :a 5.0 :b} :cljs (hash-map 5 :a (js/BigInt 5) :b))` — both
  measured 2 entries on their own runtime
- enc set: `#?(:clj (conj #{5} 5.0) :cljs (conj #{5} (js/BigInt 5)))` —
  measured 2 elements (never a set literal, see reader error above)
- dec wires (portable data): ["m" [[5 x] [5 y]]] same-form twin;
  ["m" [[5 x] [["u" "5"] y]]] cross-form twin; ["m" [[5 x] [["d" "5.0"]
  y]]]; ["set" [5 5]]; wire->tx top-level {"tx" "t" "_pairs" [[["k" "tx"]
  ...]]} and {"tx" "t" "a" 5 "_pairs" [[["k" "a"] 6]]} (flat/pairs)
Positive controls (VALUE pins, green before AND after — over-refusal guard):
{5 :a "5" :b} round-trips `=`-equal (5 and "5" share no canon form); wire
idempotence for {1 :a 2 :b} and (conj #{1} 2); single-key {5.0 :a} keeps its
wire (the canon's declared 5.0 ≡ 5 normalization stays — only MERGING
entries are refused).

BROKEN expectations: every refusal pin red on all three legs (nothing throws
today); positive controls green. Then GREEN x3 (`kbb -M:test`,
script/jvm-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
script/nbb-test.sh — identical counters, never claim both runtimes from
one), then land.

## Carried finding (measured, NOT fixed — separate package)

`torihiki.api/canon-val` has the same silent merge: `(into {} (map ...) v)` /
`(into #{} ...)` canonize colliding keys/elements with an UNSPECIFIED winner
(insertion order of the submitted map). It is unreachable for honest flows
once the codec refuses (commit-block! throws at the wire before signing is
recorded), but a direct canon-tx caller can still observe a lossy forge —
and `#{5 5.0}`-class maps are JVM-constructible. One later tick, one
package.

## Not run / not claimed

Tests not written; BROKEN runs, all three GREEN legs, the same-count claim,
and the landing (commit of the fix, PR, merge): NOT RUN this tick. The only
writes to the repo tree are this evidence file and the probe scripts under
/tmp (outside the repo). Leg commands re-verified present: script/jvm-test.sh
(renamed-copy recipe in script/jvm-leg.sh), script/nbb-test.sh (floor in
script/tests-on-nbb.cljk).
