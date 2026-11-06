# 2026-11-06 22:2x torihiki-fix tick — api/canon-val silent merge: canon-collision door LANDED

Load gate: torihiki_state.sh absent at ~/.hermes/scripts/ (chronic-fault
precedent); uptime direct 22:17 — 0.94 / 1.07 / 1.37, all windows < 20,
gate passed.

Package = the carry named in BOTH slots of PR #109's landing record and
scoped in evidence/2026-11-06-182x-torihiki-fix-canon-val-merge-door-
scoped-not-landed.md (branch + worktree `bot/torihiki-fix-20261106-1824`,
merged into this tick's line so the scope record and probe ride along).
Implemented exactly the 182x door design, TDD BROKEN-first.

## Measured (exact counts)

- BROKEN (fix withheld, tests present), all three legs — counters identical:
  **688 tests / 5648 assertions** on kbb / JVM / nbb.
  - kbb `kbb -M:test`: **1F / 0E** (KBB_EXIT=1) — the designed leg-agnostic
    red (`:canon-collision` ∉ `reasons` yet); the collision constructions
    are JVM-constructible only, so the refusal pins ride their measured
    SCI VALUE answers on the JS legs (182x probe ground truth).
  - JVM `script/jvm-test.sh`: **9F / 0E** (JVM_EXIT=1) — exactly the
    designed reds: map keys 3 (bare-vs-bare, 5.0/5N twin, tx-level keys),
    set elements 1, composite keys 1, any-depth 2 (value-nested,
    key-nested), liveness rejection record 1, closed-set membership 1.
  - nbb `KOTOBA_CHECKOUTS=… script/nbb-test.sh`: **1F / 0E** (NBB_EXIT=1) —
    same designed red as kbb.
  (/tmp/cc-broken-{kbb,jvm,nbb}.out)
- GREEN, all three legs — counters identical: **688 tests / 5648
  assertions / 0 failures / 0 errors**, KBB_EXIT=0 / JVM_EXIT=0 /
  NBB_EXIT=0. Same-count identity with the BROKEN counters 3/3.
  (/tmp/cc-green-{kbb,jvm,nbb}.out)

One real red was found by the first GREEN run itself and fixed before any
green was claimed: `state_test/every-refusal-reason-has-its-own-code`
(red measured on kbb: `reasons with no code: [:canon-collision]`) — the new
reason needs its stable root code; `st/reason-codes` gained `:canon-collision
23` (append-only, never renumbered). Red measured before its fix, green
after, in this tick's runs.

## What landed

- `src/torihiki/api.cljk` only + its registry entry in `src/torihiki/state.cljk`:
  1. private `first-canon-collision`: the first key/element, at any depth
     (keys included — `canon-val` canonizes a key's own structure too),
     whose canon form repeats an earlier one's — exactly the merge identity
     of `canon-val`'s `(into {})/(into #{})`. Wrapper return (`{:hit x}`),
     log.cljk `first-collision` precedent; NaN keys collide with nothing.
  2. `validate` refuses BEFORE `canon-tx`: `:canon-collision` when the canon
     would lose an entry — the silent hash-order-local merge becomes a
     rejection record, never a throw (apply-path contract held: the
     `st/apply-block` pin proves the refusal is a `:rejected` entry and the
     surrounding transactions still execute).
  3. `reasons` gains `:canon-collision`; `st/reason-codes` gains code 23.
  4. `canon-val`/`validate` docstrings amended (the merge is reachable only
     un-validated; the door refuses exactly its domain).
- `test/torihiki/api_test.cljk`: 7 deftests / 16 assertions, overflow_gates
  pattern (REASON literal pinned for refusals, VALUE pins green from birth
  for the clean side: single key, distinct forms, distinct set elements,
  nested clean maps, NaN twins), one `is` per line — identical counters on
  all three legs by construction.

Out of this package (carried, per the 182x scoping note): the
validate/wire domain difference for BigInt/out-of-domain numeric twins —
`tx->wire` refuses `{5, BigInt5}` (PR #109) while the engine canon sees no
collision; aligning the domains is the api/int-like? BigInt door asymmetry
carry — the only PR #109 carry left after this package.

maturity.md untouched (measurement bots' file).
