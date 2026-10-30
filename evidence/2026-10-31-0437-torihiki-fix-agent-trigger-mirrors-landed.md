# 2026-10-31 04:3x torihiki-fix tick — agent/trigger shape mirrors LANDED

Load gate: torihiki_state.sh 04:17 — 16.91 / 23.51 / 27.99 (1-min < 20; the
"all windows > 20" stop condition did not hold), tick proceeded.

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261031-0419, branch
bot/torihiki-fix-20261031-0419 off origin/main c936e66 (PR #83 merge —
attest totality). Fresh worktree: the previous handoff worktree
(bot-torihiki-fix-20261028-1224) no longer existed; its package landed as
PR #78 and the queue had moved on.

## Package: the last two store-door mirrors (per the attest-totality queue)

`api/store-tx-shape` answered with only HALF of what `api/validate` judges
for these transactions — the two-doors contract ("the SAME judgments
`validate` makes, in the same order, answering with the same keyword") was
broken on exactly three words:

1. `(:authorize-agent :revoke-agent)`: `validate` requires `:agent` to be a
   44-character base64 Ed25519 public key and `:expires` (authorize only,
   when said) an integer in 1..i53-max (`:missing-field` otherwise). The
   engine door checked only the account, so the engine STORED any string as
   the `:agents` KEY and any value as the `:expires` record field. The
   value is not inert: `torihiki.auth/agent-of` COMPARES `:expires` against
   the block height on every agent-signed trade — the BROKEN run measured
   the pre-fix asymmetry directly (kbb/SCI): `:expires "never"` makes
   `agent-of` answer nil (JS `>` coercion — the key silently dies) where
   the JVM comparison throws (expected, NOT measured this tick), and
   `:expires "5"` returns the record RAW with the string still in it. An
   out-of-domain height is the falsify-8 divergence class inside the root.
2. `:cancel-trigger`: `validate` requires `:id` to be an integer
   (`:missing-field` otherwise). `trg/cancel` is total on the id, but the
   engine's `update-in [:triggers market]` CREATES the key — the BROKEN run
   measured a garbage id writing `:triggers {1 []}` into the root for a
   transaction `validate` refuses.

Landed:

1. src/torihiki/api.cljk — `store-tx-shape` extended with the exact
   `validate` words in the exact order (account first, then agent string?,
   then count 44, then expires for :authorize-agent only; market first,
   then `(integer? (:id t))` for :cancel-trigger — mirroring `validate`'s
   own clause order). Integer, not natural, for the cancel id: `validate`
   says `integer?`, and one shape rule means the mirror does not sharpen it.
2. test/torihiki/agent_trigger_totality_test.cljk — 7 deftests: VALUE pins
   at the engine (garbage never reaches :agents / never overwrites the real
   record / never creates the :triggers key / never touches live triggers),
   the auth-layer read pin (`auth/agent-of` on the post-gate state answers
   the REAL record — no throw, no garbage value), REASON pins at BOTH doors
   for 22 cases including precedence (account before agent, agent before
   expires, market before id) and the over-refusal guards (out-of-domain
   id is still an integer and still passes; i53-max is a legal expiry).
3. script/tests-on-nbb.cljk — torihiki.agent-trigger-totality-test in
   require + run-tests; expected-namespaces floor 32 -> 33 with provenance.

## Measured (exact counts)

- BROKEN (kbb/SCI, engine door unmodified, tests as written):
  **574 tests / 4748 assertions / 33 failures / 0 errors, exit 1** — all 33
  in torihiki.agent-trigger-totality-test (9 authorisation VALUE, 6
  auth-layer read, 5 cancel-id VALUE, 13 two-door REASON); the 567/4644
  baseline and every over-refusal guard stayed green. Observed kbb/SCI
  failure modes: garbage agent/expiry STORED (record overwritten), garbage
  cancel id CREATED `[:triggers 1] []`, `store-tx-shape` answered nil where
  `validate` answered :missing-field. Raw:
  evidence/2026-10-31-0436-torihiki-fix-broken1.{out,err}.
- GREEN, three legs, identical counters — **574 tests / 4748 assertions /
  0 failures / 0 errors**:
  - kbb -M:test (SCI): exit 0 —
    evidence/2026-10-31-0436-torihiki-fix-green-kbb.{out,err}
  - script/jvm-test.sh (renamed .cljk->.cljc copy, kotoba-lang dep shims):
    exit 0 — evidence/2026-10-31-0436-torihiki-fix-green-jvm.{out,err}
  - KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
    script/nbb-test.sh: namespaces 33/33 floor held, "TESTS-ON-NBB: pass —
    the runtime that deploys ran the suite", exit 0 —
    evidence/2026-10-31-0436-torihiki-fix-green-nbb.{out,err}

JVM-leg BROKEN run: NOT run (the BROKEN leg is kbb/SCI only this tick). The
JVM-specific pre-fix red modes ((long nil)-class throw at the `agent-of`
comparison for `:expires "never"`) are EXPECTED but UNMEASURED — do not
cite them as measured.

## Known scope residual (documented, not fixed here)

Remaining totality queue (from the attest-totality record): the
:liquidate/:equivocation-evidence/:validator-*/:upgrade-*/:principal-*
dispatches, then the :twap/:scale raw-garbage direct-call residual. Also
still door-covered but unprobed: the by-set/bridge attest VALUE paths.
`validate`'s own :trigger clause does not judge the submission `:id`
(engine-assigned from :trigger-seq, so not garbage-reachable today).

maturity.md untouched (measurement bots' file).
