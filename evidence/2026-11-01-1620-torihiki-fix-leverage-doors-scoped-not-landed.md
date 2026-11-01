# 2026-11-01 16:2x torihiki-fix tick — NOT LANDED (run budget exhausted during reconnaissance). Fix package scoped, bug CLASS measured.

Load gate: torihiki_state.sh 16:17 — 15.94 / 13.17 / 12.04, no window over 20,
gate passed. Branch bot/torihiki-fix-20261101-1620 (worktree
~/github/wt/fleet/bot/torihiki-fix-20261101-1620 at origin/main fc88b10) kept
for the next tick. maturity.md untouched (measurement bots' file).
No suite, no leg, no BROKEN run executed this tick — "not run" != "passed";
nothing is claimed green here except runs cited from landed evidence.

## Finding (measured, code reads at origin/main fc88b10): the leverage slots
are the last open falsify-6/7 halt in-repo

maturity.md NEXT ② is stale against the tree (the 08:17 2026-10-28 recon's
Finding 1 stands): validate i53 caps, the notional cap, the falsify-8
balance-domain gate (api.cljk:1092-1097), and the accumulator sweep through
fx/acc/fx/sadd are all landed (PRs #52-#77 + core pin c59d8fd), and the
totality series (#79-#85) plus the i53 boundary (#86) leave every apply-tx
dispatch behind a store door. The i53-boundary note's "falsify-7/8 validate
package proper remains open" is a maturity.md-text statement, not a code
statement — verified open against the tree is the following instead.

### Bug 1 (halt class, measured asymmetry): present-nil `:max-leverage`

`flat-spec-ok?`'s count-ok? is `(or (nil? v) (int-in? v lo fx/i53-max))` and
`tier-leverage-ok?` allows `(nil? (:max-leverage t))` — so a spec carrying
`:max-leverage nil` (PRESENT, value nil) passes BOTH doors. Reachable with
in-domain inputs everywhere else: bridge-signed `:list-market` with
`{:spec {:max-leverage nil ...}}` (state.cljk:1527 stores the spec flat) or
`:amend-market {:spec {:max-leverage nil}}` (state.cljk:1466 `(merge cur
(dissoc spec :id :tick :lot))` ERASES the live cap). The next `:set-leverage`
reaches the pinned core's `cl/set-leverage` (clearing.cljk:572) whose reader
is `(:max-leverage market 1)` — a keyword default that does NOT fire when the
key is present with nil — and evaluates `(> lev nil)`.

Measured runtime asymmetry of that exact expression (this tick, direct):

- JVM (clojure 1.12.0): `(> 5 nil)` -> **THREW
  java.lang.NullPointerException "Cannot invoke \\"Object.getClass()\\" because
  \\"x\\" is null"** — a throw inside apply-block: the halted-chain contract
  break state.cljk forbids (falsify-6/7 class, one signed tx pair halting
  every validator at the same place).
- nbb: `(> 5 nil)` -> true (no throw), `(> 9007199254740993 nil)` -> true —
  silent refusal, state unchanged. Same log, two different outcomes.

The fold is NOT the hazard: `enc-int` starts `(let [v (or v 0) ...])` so
present-nil encodes as 0 bytes; the hazard is only the reader above.
`cl/market`'s `(fx/fdiv fx/rate-scale max-leverage)` is not reachable from
apply-tx (its only caller in-repo is parity.cljk:27 with a good spec).

### Bug 2 (door divergence, measured): the `:set-leverage :leverage` slot

Both doors (store-tx-shape api.cljk:547, validate api.cljk:1187) judge
`:leverage` with `(and (integer? (:leverage t)) (pos? (:leverage t)))` — no
i53 bound, and `integer?` is runtime-dependent. Measured: `(integer? 4.5e17)`
is **false on the JVM, true on nbb** — the same transaction is admitted by
one door and answered `:missing-field` by the other (the falsify-6/7 class at
the door itself, the exact asymmetry the spec-ok? docstring names). A
2^53+1 leverage passes both doors today; it is only cl/set-leverage's
`> max-leverage` refusal that keeps it out of `[:leverage acct mkt]` state.

## Scoped design (ready to implement next tick, TDD BROKEN-first)

One package, both doors word-for-word, same order, same `reasons` keywords:

1. `flat-spec-ok?` + `tier-leverage-ok?`: `:max-leverage` is ABSENT or an
   in-domain count 1..i53-max — present-nil refused (`:missing-field`). This
   matches the fn's own docstring ("All optional (the readers default); when
   present: a count"). Apply the same absent-or-count rule to the other four
   flat slots (:tick :lot :open-interest-cap :asset) only if no existing
   fixture sends present-nil for them (check first; narrow to :max-leverage
   if they do).
2. `:set-leverage :leverage` in BOTH doors: `int-in? 1 fx/i53-max`,
   `:missing-field` (i53-boundary tick pattern).
3. Engine guard at `apply-tx :set-leverage` (state.cljk, fire-triggers-gate
   precedent): pre-fix state can carry a market whose `:max-leverage` is
   present-but-not-a-count — refuse as a NO-OP (state unchanged) before
   `cl/set-leverage` computes `(> lev ...)`. Never a throw on the apply path.
4. Tests (overflow_gates_test.cljk pattern; REASON pins at both doors, VALUE
   pins through `applied` so a throw fails the pin): list/amend with
   present-nil :max-leverage answers :missing-field at BOTH doors; a tier
   carrying :max-leverage nil is refused; leverage 9007199254740993 and
   4.5e17-shaped values are refused at both doors; leverage fx/i53-max and a
   spec with :max-leverage ABSENT still pass (over-tightening guard); seeded
   market with :max-leverage nil + :set-leverage is a state-identical no-op
   on every leg (the JVM NPE pin — BROKEN-first on kbb/SCI). Register a new
   test namespace in script/tests-on-nbb.cljk (floor 34 -> 35 with
   provenance) or extend an existing file to avoid the floor change.

## Not run / not claimed

kbb/SCI, JVM (script/jvm-test.sh renamed copy), nbb (script/nbb-test.sh)
suites NOT RUN this tick. No BROKEN run, no GREEN run. The only executions
this tick are the two one-liner runtime probes quoted above and code reads.
Next tick: implement in this worktree/branch, run the new tests BROKEN first
and record the counts, then three-leg GREEN (baseline 590 tests / 5050
assertions per the 2026-11-01-1435 landed note), then PR + merge.
