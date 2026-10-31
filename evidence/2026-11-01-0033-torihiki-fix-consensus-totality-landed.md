# 2026-11-01 00:3x torihiki-fix tick — consensus-totality package LANDED (PR #85)

Load gate: torihiki_state.sh 00:17 — 12.81 / 14.68 / 18.33, all windows <= 20,
gate passed.

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261031-2219, branch
bot/torihiki-fix-20261031-2219 off origin/main dc33c92 (PR #84 merge). The
2026-10-31 22:19 tick's handoff (tests written, nothing run) — continued here.

## What landed

Door totality for the ten dispatches PR #84's record named as the remaining
queue: `:liquidate`, `:upgrade-propose`, `:upgrade-vote`, `:principal-claim`,
`:principal-confirm`, `:principal-rotate`, `:principal-rotate-cancel`,
`:validator-register`, `:validator-rotate-key`, `:validator-retire`,
`:equivocation-evidence`. `api/validate` judged them but `api/store-tx-shape`
answered nil for all of them (its `case` default), so `torihiki.state/apply-tx`
reached directly STORED what `validate` refuses.

- src/torihiki/api.cljk: `store-tx-shape` gained the consensus/identity/keeper
  cases — `validate`'s clauses mirrored word for word, same judgments, same
  order, same keywords from `reasons`. `validate` itself is UNCHANGED. The
  mirror does not sharpen: `integer?` subjects/proposals/ids stay admitted
  (documented residual, not pinned).
- src/torihiki/state.cljk: the engine gate `(if (api/store-tx-shape ex t) ex
  ...)` at the top of all ten apply-tx defmethods, before any destructured
  field is used. Refusals are NO-OPs (dropped whole, exchange unchanged);
  no throw anywhere on the apply path.
- script/tests-on-nbb.cljk: torihiki.consensus-totality-test in require +
  run-tests; expected-namespaces floor 33 -> 34 with provenance.

## Measured (exact counts; "not run" != "passed" — everything below ran)

- BROKEN (kbb/SCI, unmodified engine, tests as corrected below):
  586 tests / 5015 assertions / **73 failures / 0 errors**, exit 1
  (evidence/2026-11-01-0033-torihiki-fix-broken1.{out,err}) — all 73 in
  torihiki.consensus-totality-test; the 574-test baseline stayed green.
  Failure modes as observed: 50 REASON-parity pins where the engine door
  answered nil instead of the validate word; VALUE pins where garbage landed
  (proposals/scheduled/seq moved, votes set joined, :principal-claims /
  :principals / :principal-accounts / :rotations keyed by garbage, a garbage
  operator stored in :validators, a slash recorded :by garbage with the 50
  reward minted and the bond taken, garbage fires, etc.). No pin failed on
  the `validate` side of the parity table — validate already answered every
  expected word.
- Test-file corrections found BY the BROKEN run (both fixture bugs, engine
  untouched by the corrections):
  1. the shapely vote pin expected #{4 5} on a fixture seeding :votes #{} —
     impossible: #{4 5} is the two-head supermajority (3h > 2t) and the
     proposal would LEAVE :proposals. Corrected to #{5} (the counted vote),
     keeping the fixture's "no votes counted yet" intent.
  2. the shapely liquidate fixture filled at level 500 on `base`'s 64-level
     ladder (stops at 63) — the landed apply-path gates correctly refused the
     off-ladder orders, so no position existed and the precondition
     `cl/liquidatable?` measured false. Corrected: that block builds its own
     512-level book. (Pre-fix BROKEN had this sub-test red at the
     precondition only; the post-fix sweep assertion now runs for real.)
- GREEN kbb/SCI: 586 tests / 5015 assertions / 0 failures / 0 errors, exit 0
  (evidence/...-green-kbb.{out,err}).
- GREEN JVM (script/jvm-test.sh, renamed .cljk->.cljc copy with dep shims):
  586 tests / 5015 assertions / 0 failures / 0 errors — counters identical to
  the SCI leg (evidence/...-green-jvm.{out,err}).
- GREEN nbb (script/nbb-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/github/
  kotoba-lang): namespaces 34/34, 586 tests / 5015 assertions / 0 failures /
  0 errors — all three legs identical (evidence/...-green-nbb.{out,err}).

## Not fixed here (documented residual, same class as prior packages)

- The mirror does not SHARPEN validate: an out-of-domain integer
  :subject/:proposal key is admitted by BOTH doors and remains a garbage key
  reachable through validated traffic — that disagreement is validate's own,
  to be fixed with the falsify-7/8 package.
- :upgrade-vote/:validator-rotate-key/:validator-retire/
  :principal-rotate-cancel have no pre-door garbage-LANDS path on kbb/SCI
  (internals mismatch/no-op first) — their pins are guards and REASON parity
  only, recorded honestly as such.
- JVM-only pre-fix red modes ((long nil) throw class) were NOT exercised
  broken on the JVM leg this tick — the BROKEN leg is kbb/SCI only per
  convention.

maturity.md untouched (measurement bots' file).
