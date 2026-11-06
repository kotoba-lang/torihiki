# 2026-11-06 20:2x torihiki-fix tick — canon-val silent merge scoped, NOT LANDED (budget)

Load gate: torihiki_state.sh absent (chronic-fault precedent); uptime direct
20:18 — 1.33 / 1.24 / 1.21, all windows < 20, gate passed.

Worktree bot/torihiki-fix-20261106-2020 off origin/main 8d83d63 (PR #109
key-collision canon merge) created and clean. NO code changed, NO tests run —
runtime budget exhausted during scoping/design. Nothing landed.

## Scoped package (the two carried items of the PR #109 tick, 162x evidence)

1. api/canon-val silent merge (this package's core): `canon-val`'s map branch
   `(into {} (map ...) v)` merges distinct keys whose CANON FORMS are `=`
   (JVM constructible pair: {5 :a 5.0 :b} — `(= 5 5.0)` false on the JVM, both
   canon to long 5). The merge winner is iteration order — one tx, different
   applied state per runtime. The recorded design stands: a reason-valued door
   at `validate` (cannot throw); check the RAW tx BEFORE `canon-tx` runs
   (validate* receives the already-merged map today, so the door must sit in
   `validate` itself, around the canon).
2. api/int-like? BigInt door asymmetry (the JS leg of the same class): on SCI,
   `int-like?` answers false for a BigInt (integer? is not type-identity
   there), so validate refuses a BigInt :bad-quantity on the JS legs while the
   JVM (integer? true for clojure.lang.BigInt) admits the same tx — one tx,
   two verdicts across runtimes, a :rejected-vs-applied root divergence on
   replay. Without this fix the api collision door cannot see the JS merge
   pair at all: on SCI the constructible distinct pair is 5 vs (js/BigInt 5)
   (measured in PR #109 tests) and canon-val leaves a BigInt untouched today,
   so no merge occurs — the pair halts at the wire door instead.

## Design decisions reached (not yet implemented)

- Criterion at the api door: two map keys / set elements collide iff their
  CANON FORMS are `=` (`canon-val` equality) — this is exactly the silent-
  merge hazard. The wire door (PR #109, landed) already refuses the strictly
  larger encode-collision class loudly; the two doors guard different hazards.
- New keyword in `torihiki.api/reasons` for the refusal (candidates:
  `:canon-collision`), pinned as a REASON literal in tests per the
  overflow_gates pattern.
- int-like? extension: reuse the PR #109 detector shape — type identity
  (`(identical? (type v) js/BigInt)` on cljs; `instance? clojure.lang.BigInt`
  on clj). `(long v)` THROWS on a SCI BigInt (measured in PR #109), so
  canon-val's normalization needs the `bare-int` shape (js/Number for
  in-domain BigInt on cljs). log.cljk already carries private
  bigint-like?/bare-int/canon-key/first-collision as the model — api cannot
  require log (log requires api), so the api door gets its own copies.
- Apply path stays total: no throw introduced anywhere below validate;
  apply-block routes refusals into `:rejected` (state root) as before.

## Left

- Implement + BROKEN-measure + GREEN-measure on 3 legs (kbb / JVM / nbb) the
  package above; then PR + merge. Next tick should start from this design and
  the tests pinned in the PR #109 log_test.cljk (collide-map/collide-set
  construction precedent: never literal, reader rejects duplicates — the
  constructible pair differs per runtime).
- untouched: maturity.md (measurement bots' file).
