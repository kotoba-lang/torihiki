# 2026-11-01 18:2x torihiki-fix tick — leverage-doors package LANDED

Continuation of the 16:2x reconnaissance's scoped design (its worktree/branch,
bot/torihiki-fix-20261101-1620 at fc88b10 + the scope evidence commit bdadbf9).
Load gate: torihiki_state.sh 18:17 — 13.68 / 12.42 / 12.09, all windows < 20.

## What landed (the 16:2x design, implemented as scoped)

- api.cljk: `max-leverage-ok?` (new, public) — a map's `:max-leverage` is
  ABSENT or a count 1..i53-max; present-nil is garbage (a keyword default
  does not fire when the key is present, and `cl/set-leverage`'s
  `(:max-leverage market 1)` then evaluates `(> lev nil)` — JVM NPE inside
  apply-block, silent true on nbb). `tier-leverage-ok?` is now
  `max-leverage-ok?` per tier; `flat-spec-ok?` is ABSENT-or-count for all
  five flat slots (:tick :lot :max-leverage :open-interest-cap :asset — no
  fixture sent present-nil for the other four, checked first). Both doors'
  `:set-leverage :leverage` slot: `(and (integer? v) (pos? v))` ->
  `int-in? 1 fx/i53-max`, :missing-field (word-for-word, store-tx-shape +
  validate).
- state.cljk: engine guard at `apply-tx :set-leverage` (fire-time precedent)
  — a market whose stored `:max-leverage` is present-but-not-a-count makes
  the tx a state-identical NO-OP before `cl/set-leverage` computes. No throw
  on the apply path.
- test/torihiki/leverage_doors_test.cljk (new, 4 deftests): REASON pins at
  both doors + VALUE pins through `applied`; the JVM-NPE pin (seeded
  present-nil cap) + over-tightening guards (absent slots pass; 1 and
  fx/i53-max leverage pass both doors; a healthy market records).
- script/tests-on-nbb.cljk: ns registered (require + run-tests),
  expected-namespaces 34 -> 35 with provenance.

## Measured (everything below ran)

- BROKEN (kbb/SCI, unmodified engine): 594 tests / 5098 assertions /
  21 failures / 0 errors, exit 1 — all 21 in torihiki.leverage-doors-test,
  590-test baseline green. Red modes as observed: present-nil :max-leverage
  answered nil at both doors (list STORED it, amend ERASED the live cap
  40 -> nil); all four flat slots present-nil admitted; 2^53+1 and 4.5e17
  leverage admitted at both doors; a string cap let :set-leverage RECORD
  leverage 5 (JS coercion). The present-nil-cap VALUE pin was GREEN pre-fix
  on SCI ((> 5 nil) -> true, silent refusal) — it pins the JVM NPE; honest
  note: it never failed on this leg. Raw: evidence/2026-11-01-1820-...-lev-broken.out
- GREEN kbb/SCI: 594 / 5098 / 0F / 0E, exit 0.
- GREEN JVM (script/jvm-test.sh, renamed copy): 594 / 5098 / 0F / 0E, exit 0.
- GREEN nbb (KOTOBA_CHECKOUTS=... script/nbb-test.sh): namespaces 35/35,
  594 / 5098 / 0F / 0E, exit 0. First nbb attempt refused 34/35 (exit 2 —
  the ns was in `require` but not `run-tests`; the floor caught it).
- All three legs identical counters.

JVM BROKEN leg: not run (budget) — the JVM NPE red mode of the present-nil
pin is predicted by the 16:2x direct probe ((> 5 nil) threw NPE), not by a
suite run; do not cite it as suite-measured. maturity.md untouched.
