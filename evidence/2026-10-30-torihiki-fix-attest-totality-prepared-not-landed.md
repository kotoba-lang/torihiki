# 2026-10-30 10:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# BROKEN measurement + api.cljk door; state.cljk gates, GREEN runs and landing
# not reached). "not run" != "passed": nothing is claimed green here.
# maturity.md untouched (measurement bots' file).

Load gate: torihiki_state.sh 10:17 — 6.34 / 19.06 / 21.48. Not all windows > 20
(the profile stop condition), so the tick proceeded (15-min 21.48 declining).

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261030-1020b, branch
bot/torihiki-fix-20261030-1020b off origin/main fb8a927 (PR #82 merge —
market totality). KEPT as handoff. NOTE: the first worktree this tick
(bot-torihiki-fix-20261030-1020) VANISHED from disk between commands (git
metadata present, directory gone — same symptom that ate the 10:18/12:24
handoffs); recreated as ...-1020b and re-verified present. If it is gone
again, the uncommitted state below must be re-created from this note.

## Package (continuation of the totality series; queue = market-totality's
## residual + money-totality's first residual item)

ATTEST-FAMILY DOOR TOTALITY — the six dispatches whose VALIDATE words were
never mirrored at the engine (api/store-tx-shape answered nil for all six):
:deposit-attest / :withdraw-settle / :withdraw-attest / :withdraw-cancel /
:withdraw-invalidate-attest / :attest-reserves.

## Written this tick (uncommitted in the worktree)

1. test/torihiki/attest_totality_test.cljk (NEW, 8 deftests): VALUE pins
   (nothing lands in :inbound/:paid/:invalidated/:reserve-attestation, no
   garbage :accounts key via cl/deposit) + REASON pins at BOTH
   api/store-tx-shape and api/validate (same word at both doors, closed-set
   api/reasons guard) + over-refusal guards (valid attestations still land).
2. src/torihiki/api.cljk — store-tx-shape extended with the six cases,
   validate's words mirrored verbatim (claim integer? :missing-field;
   :deposit-attest credit account-ok? + amount int-in? 1..i53-max;
   :withdraw-attest optional amount but in-domain when said;
   :attest-reserves amount 0..i53-max + spec-ok? :height*). DONE.

## Measured (exact counts) — kbb (SCI) leg only

- BROKEN #1 (tests as first written, engine unmodified): 567 tests / 4647
  assertions / 66 failures / 0 errors, exit 1 (/tmp/attn-broken.out, not
  landed — re-run below is the citable one).
- Pin corrections found BY BROKEN #1 (recorded as such):
  (a) nil :amount is VALID for :withdraw-attest (validate passes it — the
  reported amount is optional); (b) zero is VALID for :attest-reserves
  (zero reserves is a claim, int-in? 0..). Both were over-refusal pins.
- BROKEN #2 (corrected pins, engine unmodified): **567 tests / 4644
  assertions / 64 failures / 0 errors, exit 1** (/tmp/attn-broken2.out, not
  landed). Red distribution: 54 attest-shape-reasons (store-tx-shape answers
  nil — the missing door), 6 withdraw-attest VALUE, 2 deposit-attest VALUE,
  2 deposit-attest-quorum VALUE. Observed failure modes:
  - :deposit-attest out-of-domain credit/amount STORED RAW into :inbound on
    the first attestation (the falsify-8 divergence class: 2^53+1 values in
    root bytes), and at quorum the out-of-domain credit LANDS as a garbage
    :accounts KEY via cl/deposit — both measured, no throw.
  - :withdraw-attest with a garbage reported amount (string/fractional/
    out-of-domain/zero/negative) MINTED :paid anyway (the non-set path
    ignores the field; only the by-set variant mismatch-checks it) — no
    throw.
  - :withdraw-settle/:withdraw-cancel/:withdraw-invalidate-attest and the
    string/claim/account garbage sets were GREEN before (guards — the plain
    path already no-ops; the door adds the REASON parity).
- One further pin correction AFTER BROKEN #2 (so the red count on the final
  file is 63, NOT re-run — budget): the "unsaid amount proceeds" guard
  asserted settlement with ONE attester; quorum is 3, so it now asserts the
  :paid record + claim survival. Do not cite a green run of the final file.

## NOT done (next tick starts here)

1. state.cljk: no-op gates at the TOP of the six defmethods
   (:deposit-attest :267, :withdraw-settle :422, :withdraw-attest :439,
   :withdraw-cancel :510, :withdraw-invalidate-attest :532,
   :attest-reserves :1115) — `(if (api/store-tx-shape ex t) ex …)`, reasons
   from api/reasons, never a throw. Deposit-attest/by-set internals keep
   their own checks (belt under the door).
2. GREEN `kbb -M:test` (expected 567 tests / ~4644 assertions 0F/0E), then
   script/jvm-test.sh and KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
   script/nbb-test.sh — never claim both runtimes from one.
3. script/tests-on-nbb.cljk: add torihiki.attest-totality-test to require +
   run-tests; expected-namespaces floor 31 -> 32 with provenance.
4. Land: commit (tests + api.cljk + state.cljk + tests-on-nbb.cljk + this
   record), push, PR, merge --delete-branch, worktree remove.

## Known scope residual (documented, not fixed here)

The by-set/bridge VALUE paths (deposit-attest-by-set / withdraw-attest-by-set
under a validator set + :bridge) are door-covered but NOT probed this tick
(plain-path fixtures only) — money-totality's residual, still half-open.
Remaining totality queue: :authorize-agent :agent/:expires mirror +
:cancel-trigger :id shape, then :liquidate/:equivocation-evidence/
:validator-*/:upgrade-*/:principal-* dispatches, then :twap/:scale raw-garbage
direct-call residual.

Host load at tick end: not re-measured.
