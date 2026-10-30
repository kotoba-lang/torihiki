# 2026-10-30 12:3x torihiki-fix tick — attest-totality LANDED (PR #83)

Load gate: torihiki_state.sh 12:17 — 10.34 / 14.41 / 14.46, all windows < 20,
gate passed.

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261030-1020b, branch
bot/torihiki-fix-20261030-1020b off origin/main fb8a927 (PR #82 merge —
market totality). Continuation of the 10:2x handoff (tests + api.cljk door
already in the worktree uncommitted).

## Package: attest-family door totality

The six dispatches whose VALIDATE words were never mirrored at the engine
(`api/store-tx-shape` answered nil for all six): :deposit-attest /
:withdraw-settle / :withdraw-attest / :withdraw-cancel /
:withdraw-invalidate-attest / :attest-reserves. Landed:

1. src/torihiki/api.cljk — `store-tx-shape` extended with the six cases
   (claim integer? :missing-field; :deposit-attest credit account-ok? +
   amount int-in? 1..i53-max; :withdraw-attest optional amount but in-domain
   when said; :attest-reserves amount 0..i53-max + spec-ok? :height*),
   validate's words mirrored verbatim. (From the 10:2x handoff, unchanged.)
2. src/torihiki/state.cljk — no-op gates at the TOP of the six defmethods,
   `(if (api/store-tx-shape ex t) ex …)` in the established house pattern;
   refusals are no-ops, never a throw; belt checks stay under the door. The
   :deposit-attest inner `t` binding renamed to `prior` (the tx `t` is now
   the gate's argument).
3. test/torihiki/attest_totality_test.cljk — 8 deftests (VALUE pins at the
   engine + REASON pins at BOTH doors + over-refusal guards). (From the
   10:2x handoff, with its post-BROKEN#2 pin correction.)
4. script/tests-on-nbb.cljk — torihiki.attest-totality-test in require +
   run-tests; expected-namespaces floor 31 -> 32 with provenance.

## Measured (exact counts)

- BROKEN #1/#2 (previous tick, engine unmodified): 567 tests / 4647
  assertions / 66F, then 567 / 4644 / 64F — see
  evidence/2026-10-30-torihiki-fix-attest-totality-prepared-not-landed.md.
- BROKEN #3 (this tick — final test file + api.cljk door, state.cljk gates
  ABSENT; covers the one pin corrected after BROKEN #2): **567 tests / 4644
  assertions / 9 failures / 0 errors, exit 1** — exactly the state.cljk gate
  pins: deposit-attest garbage credit/amount stored RAW into :inbound (2),
  garbage credit at quorum = garbage :accounts key (2), withdraw-attest
  garbage reported amount minted :paid (5). Raw:
  evidence/2026-10-30-1232-torihiki-fix-broken3.{out,err}.
- GREEN, three legs, identical counters — **567 tests / 4644 assertions /
  0 failures / 0 errors**:
  - kbb -M:test (SCI): evidence/2026-10-30-1232-torihiki-fix-green-kbb.{out,err}
  - script/jvm-test.sh (renamed .cljk->.cljc copy, kotoba-lang dep shims):
    evidence/2026-10-30-1232-torihiki-fix-green-jvm.{out,err}
  - KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
    script/nbb-test.sh: namespaces 32/32 floor held,
    "TESTS-ON-NBB: pass — the runtime that deploys ran the suite":
    evidence/2026-10-30-1232-torihiki-fix-green-nbb.{out,err}

JVM-specific red modes ((long nil) throw class) were NOT exercised this tick
(both BROKEN legs were kbb/SCI only) — the previous tick's caveat stands:
never cite them as measured.

## Known scope residual (documented, not fixed here)

The by-set/bridge VALUE paths (deposit-attest-by-set / withdraw-attest-by-set
under a validator set + :bridge) are door-covered but NOT probed (plain-path
fixtures only) — money-totality's residual, still half-open. Remaining
totality queue: :authorize-agent :agent/:expires mirror + :cancel-trigger
:id shape, then :liquidate/:equivocation-evidence/:validator-*/:upgrade-*/
:principal-* dispatches, then :twap/:scale raw-garbage direct-call residual.

maturity.md untouched (measurement bots' file).
