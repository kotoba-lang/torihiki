# 2026-11-06 00:2x torihiki-fix tick — signing/wire canon package: SCOPE +
# BROKEN-measured (kbb), fix IMPLEMENTED, NOT landed (budget). "not run" !=
# "passed": every number below was run and counted this tick; GREEN is NOT
# run and NOT claimed.

Load gate: torihiki_state.sh absent on this host (chronic-fault precedent);
uptime direct 00:17 — 1.45 / 1.39 / 1.49, all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261106-0029, branch
bot/torihiki-fix-20261106-0029 off origin/main 867b3e3 (PR #106 tip).
maturity.md untouched (measurement bots' file).

## Queue check (measured by code read at 867b3e3)

The 222x landing record's "Left" list is STALE on both items — verified this
tick: (1) "venue sums (20261105-1427)" landed in PR #104 (all four sites go
through fx/acc, grep-verified at 867b3e3); (2) "wire-number-canon 'd' route"
landed in PR #93 (log.cljk frac-expansion is the non-BigInt mul5d digit
route, "validated on SCI"; decode pins are wire-level per the 142x
correction). maturity.md NEXT: fully landed (same verdict as the last four
queue checks). Queue head = NEW probe target.

## Findings (measured this tick): evidence/probe-signing-canon.cljk, run on
## the JVM leg (renamed copy /tmp/tori-jvm-0029, clojure -M) and the SCI leg
## (script/nbb-leg.sh) — raw printing is not a canon, and the payload line
## saw ten fields

1. WIRE FORK — enc-val's SET branch sorted by the RAW `pr-str` (the map
   branch sorts by the printed ENCODING — "so two runtimes that fold the
   same map produce the same bytes" — the lesson never reached sets):
   `#{1e21 15.0}` folds [["u" …] 15] on the JVM ("1.0E21" < "15.0") and
   [15 ["u" …]] on kbb/nbb ("15" < "1e+21"). Same tx, two wire byte
   strings, two CIDs, no throw.

2. SIGNATURE COVERAGE — signing-payload rendered ten named slots
   (account market side level qty flags oid amount price) and nothing
   else. Two DIFFERENT :trigger txs (different :trigger-price, different
   :order) produced the IDENTICAL payload "trigger:1:2:::::::"; same for
   :slices/:every. A signature over one verified the other.

3. COMPOSITE SLOT RENDERING — slot-str fell back to raw `str` for
   composites: `#{1e21 15.0}` rendered "#{15 1.0E21}" on the JVM and
   "#{1e+21 15}" on kbb/nbb (number print AND iteration order are
   runtime-local); {:a 1e17} rendered "{:a 1.0E17}" vs
   "{:a 100000000000000000}". One tx, two signed payloads.

4. ROUND-TRIP LAW BROKEN (JVM, carried forward — NOT fixed this tick): a
   map whose keys collide under the canon (5 and 5.0 built with
   `into {}`, 2 entries on the JVM, unrepresentable as a literal — the JS
   reader rejects "duplicate key: 5") encodes both keys to bare 5, decodes
   to ONE entry, `(= tx back)` false. NaN's precedent (law cannot hold →
   refuse loudly) applies; not fixed here (one tick, one fix package).

## The fix (implemented, NOT verified green)

src/torihiki/log.cljk:
1. enc-val set branch sorts by (comp pr-str enc-val) — the map rule.
2. slot-str renders map?/vector?/set? through (pr-str (enc-val v)) — the
   wire canon's bytes, identical on both runtimes because the encoding is.
3. signing-payload: every entry outside the named slots rides after them
   as `;key=value` (slot-keys set + extra-str), sorted by the printed
   encoding of the key. A tx carrying only named slots keeps its exact
   bytes (every existing payload pin is unchanged by construction).

## Measured (exact counts) — BROKEN only, kbb leg

Tests written first (4 deftests, 13 assertions; overflow_gates pattern —
VALUE pins for renderings, loud-refusal style for the law):
- kbb (`kbb -M:test`) BROKEN (tests present, fix withheld):
  **665 tests / 5595 assertions / 8 failures / 0 errors**, KBB_EXIT=1
  (/tmp/tori-0029-broken-kbb.{out,err}). Baseline 661/5582 (PR #106
  landing record; independently re-measured this tick on the JVM leg:
  script/jvm-leg.sh test -> 661 / 5582 / 0F / 0E, JVM_EXIT=0) + 4
  deftests / +13 assertions = 665/5595 on every leg. The 8 reds:
  set-order pin 2, all 4 coverage pins, both composite pins, and the
  wire-round-trip-payload law. (set pin 1 is green on kbb by luck — the
  JS fold order already matched the encoded order; expect it red on the
  JVM BROKEN leg.)

## Not run / not claimed

- BROKEN on the JVM leg and the nbb leg: NOT run (budget).
- GREEN on all three legs: NOT run. The fix is in the tree of this branch
  but UNVERIFIED — do not merge without the three-leg green run
  (identical counters on kbb / script/jvm-test.sh / script/nbb-test.sh,
  expect 665/5595 0F/0E on each).
- PR create/merge: NOT run (budget).

## Handoff (next tick)

Branch bot/torihiki-fix-20261106-0029 carries: the fix (log.cljk: enc-val
set sort, slot-str composite canon, signing-payload extra-str), the 4
deftests (test/torihiki/log_test.cljk), and the measured probe
(evidence/probe-signing-canon.cljk; a copy at script/ — remove before
landing). Next: run the three legs green (expect 665/5595 0F/0E on each;
if the composite or trigger pins disagree with the implementation, the
PIN is the contract — fix the implementation to the canon the pins state,
or re-derive the pin from the encoding rules and say so in the record),
delete script/probe-signing-canon.cljk, then PR + merge. Carry-forward
finding 4 (canon-colliding map keys / set elements: refuse loudly per the
NaN precedent) stays open for a later tick.
