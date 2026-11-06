# 2026-11-06 18:2x torihiki-fix tick — api/canon-val silent merge: SCOPED + PROBED, NOT LANDED (budget)

Branch + worktree KEPT — next tick starts here:
`bot/torihiki-fix-20261106-1824` at
`/Users/junkawasaki/github/wt/fleet/bot-torihiki-fix-20261106-1824`
(base origin/main 8d83d63 = PR #109 merge, clean tree — no code changed this tick).

Load gate: torihiki_state.sh absent at ~/.hermes/scripts/ (chronic-fault
precedent); uptime direct 18:17 — 1.18 / 1.21 / 1.23, all windows < 20,
gate passed.

## Package chosen (highest-leverage UNLANDED item)

The carry-forward named in BOTH slots of PR #109's landing record
(evidence/2026-11-06-162x-torihiki-fix-key-collision-canon-landed.md
"Left (carried, separate packages)" and the 142x scoped record's
"NOT in scope"): **api/canon-val silent merge — a reason-valued door at
validate; it cannot throw (apply-path contract); own tick.**

The defect: `canon-val` (api.cljk:107/109) merges map keys and set
elements whose CANON FORMS are `=` via `(into {})/(into #{})` — silently,
and with a hash-order-local winner. `validate*` judges the ALREADY-MERGED
tx (`(validate* ex (canon-tx tx))`), so the loss is invisible at every
door. `torihiki.state/apply-block` validates every tx first ("never applied
and never thrown from", state.cljk:1951), so a validate door closes the
whole apply path — a colliding tx becomes a `:rejected` record, which is
exactly the liveness contract.

## Measured (exact counts) — probe, both runtimes (/tmp/probe-canon-door.cljk,
## /tmp/probe2.clj; kbb and clojure -M, this tick)

kbb/SCI (JS key identity):
- `(= 5 5.0)` true; `(into {} [[5 :a] [5.0 :b]])` = 1 entry; `(into #{} [5 5.0])` = 1 — the bare-vs-bare class cannot coexist, no canon merge reachable.
- `(= 5 (js/BigInt 5))` false; `(integer? (js/BigInt 5))` false; `(number? (js/BigInt 5))` false; `(into {} [[5 :a] [(js/BigInt 5) :b]])` = 2 entries — BigInt twins stay apart through canon-val (it leaves a BigInt untouched), no merge, no loss.
- `(= [5] [5.0])` true; `(into {} [[[5] :a] [[5.0] :b]])` = 1 entry — composite class also pre-merged at construction.
=> on SCI the canon-val silent merge is UNREACHABLE for every constructible shape (reasoned over the measured `=`/int-like? semantics; canon-val only rewrites in-domain whole numbers to longs, and SCI number equality is value equality).

JVM (Clojure key identity):
- `(= 5 5.0)` false; `(into {} [[5 :a] [5.0 :b]])` = **2 entries**; `(into #{} [5 5.0])` = **2** — canon-val maps both to `(long 5)` = 5L and the fold LOSES one (map: hash-order winner keeps one value; set: one element). THE defect.
- `(= 5 5N)` true; `(into {} [[5 :a] [5N :b]])` = 1 — merges at construction, not at canon.
- `(= 5.0 5N)` false; `(into {} [[5.0 :a] [5N :b]])` = **2 entries** — canon-val maps both to 5L → silent merge. Second class.
- `(= [5] [5.0])` false; `(into {} [[[5] :a] [[5.0] :b]])` = **2 entries** — canon forms `[5L]`/`[5L]` → silent merge. Third class.
- `(= 1e17 (bigint 1e17))` false; `(into {} [[1e17 :a] [1e17N :b]])` = 2 entries — canon-val leaves both (out of domain), NO merge (but the WIRE refuses this pair — see scoping note).

## Fix design (ready to implement next tick, TDD BROKEN-first)

Criterion = EXACTLY the merge identity of `canon-val`: two map keys or set
elements (at any depth) collide iff their canon forms are `=`. No false
positives (refuse iff an entry would actually be lost). NaN keys are not
collisions (`(= ##NaN ##NaN)` false — consistent with the wire's
"singleton NaN unchanged").

src/torihiki/api.cljk only:
1. private `canon-collides?` (deep walk: maps → keys pairwise + recurse into
   values; vectors → recurse; sets → elements pairwise + recurse into
   elements). Total: `canon-val` is total, `=` is total. Uses a wrapper
   return (log.cljk `first-collision` precedent) so a colliding nil/false
   key is not hidden from `when-let`.
2. `validate` refuses BEFORE `canon-tx`: `(if (canon-collides? tx) :canon-collision (validate* ex (canon-tx tx)))`.
3. `reasons` gains `:canon-collision` (closed-set taxonomy, ns docstring).
4. `canon-val` docstring one-line amendment: the merge is only reachable
   un-validated; the door refuses exactly its domain. NO throw anywhere —
   the apply path keeps its total contract.

Scoping note (deliberately NOT this package — carried, BigInt-asymmetry
tick's territory): the door uses the ENGINE canon criterion (canon-val `=`),
not log.cljk's wire criterion (`dec-val∘enc-val` `=`). They differ only for
BigInt/out-of-domain numeric twins (measured above: {5, BigInt5} on SCI,
{1e17, 1e17N} on JVM — no canon-val merge, but `tx->wire` refuses them
post-PR-#109). Aligning the validate domain with the wire domain is the
"api/int-like? BigInt door asymmetry" carry, which is already queued as its
own tick; this package makes no BigInt-domain decisions.

## Tests (planned; NOT written, NOT run — budget)

test/torihiki/api_test.cljk, overflow_gates pattern (REASON literal pinned
for refusals, VALUE pinned for the clean side; one `is` per line so all
three legs keep identical counters — reader-conditional constructions, the
PR #109 precedent "each runtime's own key identity"):
- colliding map keys refused at validate with `:canon-collision` pinned
  (JVM: `(into {} [[5 :a] [5.0 :b]])` and `(into {} [[5.0 :a] [(bigint 5) :b]])`;
  the reader-conditional SCI line pins the BigInt-twin VALUE answer instead —
  no collision there, construction collapses in the caller).
- colliding set elements refused (`(into #{} [5 5.0])` class, JVM).
- composite keys refused (`[[5] :a]` vs `[[5.0] :b]`, JVM).
- nested: a colliding map inside a value (e.g. an extra spec field) refused.
- VALUE pins (green from birth): `{5 :a}` / `{5 :a 6 :b}` / `#{5 6}` /
  nested clean maps still validate exactly as before; `every-rejection-is-
  in-the-closed-set` keeps passing (the new keyword is in `reasons`).
- BROKEN-first: run with the door withheld — the colliding txs answer nil
  (silently merged) instead of `:canon-collision` = red on the JVM leg by
  construction. (Note: the bare-vs-bare classes are JVM-constructible only;
  the kbb/nbb legs run the same deftests with the reader-conditional lines
  pinned to their measured semantics.)

## Handoff (next tick)

Worktree KEPT at 8d83d63 + this evidence file + probe copies
(evidence/probe-canon-door.cljk). Next tick: (1) write the deftests, run
BROKEN on kbb (fix withheld) and JVM/nbb if budget allows; (2) implement
the three pieces above; (3) GREEN x3 identical counters (`kbb -M:test`,
`script/jvm-test.sh`, `script/nbb-test.sh` with
KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang); (4) PR + merge.

## Measured this tick (exact counts) — what is claimed

- Load gate: 1.18 / 1.21 / 1.23 (uptime direct) — passed.
- Probe outputs above: exact counts from kbb and `clojure -M` runs this tick.
- Suite / BROKEN / GREEN legs: **NOT run** (budget). Nothing is claimed
  green. Baseline cited from landed evidence only (origin/main 8d83d63:
  681 tests / 5632 assertions / 0F / 0E on all three legs per
  evidence/2026-11-06-162x-torihiki-fix-key-collision-canon-landed.md).
- "not run" != "passed"; no test in this package exists yet, so no test is
  claimed red or green.

maturity.md untouched (measurement bots' file).
