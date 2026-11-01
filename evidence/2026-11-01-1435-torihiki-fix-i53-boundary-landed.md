# 2026-11-01 14:3x torihiki-fix tick — i53-boundary package LANDED

Load gate: torihiki_state.sh 14:17 — 17.01 / 14.36 / 19.26, no window over 20,
gate passed.

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261101-0231, branch
bot/torihiki-fix-20261101-0231 off origin/main 896133b (PR #85 merge;
re-fetched this tick — origin/main had not moved). Continues the 02:31 tick's
handoff (its scoping note rides in this commit as
evidence/2026-11-01-0231-torihiki-fix-i53-boundary-scoped-not-landed.md).

What landed: the validate-side piece of maturity.md NEXT ② that PR #85 named
as its residual — the i53 boundary, sharpened in BOTH doors
(`api/validate` and `api/store-tx-shape`, word for word, same order, same
`reasons` keywords) at 5 integer slots:

- `:upgrade-vote :proposal` — `integer?` -> `int-in? fx/i53-min fx/i53-max`,
  :missing-field. Keys `:proposals`; a winning vote stores it into
  `:protocol :scheduled :proposal`.
- `:principal-confirm :subject` and `:principal-rotate :subject` —
  `integer?` -> `account-ok?` (a subject IS an account: it keys
  `:principals`/`:rotations` and is stored as the `:principal-accounts`
  VALUE), :bad-account.
- `:equivocation-evidence :evidence-height` and `:view` — `integer?` ->
  `int-in? fx/i53-min fx/i53-max`, :missing-field. `[witness
  evidence-height view]` is the KEY the slash record is filed under in
  `:slashed` — a genuine falsify-8 landing class.

The engine gate (`torihiki.state/apply-tx`, PR #85) calls `store-tx-shape`,
so it sharpens with the door; no state.cljk change. Refusals stay no-ops;
nothing throws on the apply path. tests-on-nbb floor unchanged (34 — no new
namespace).

Measured (everything below ran; "not run" != "passed"):
- Baseline before edits (02:31 tick, /tmp/tfx-baseline): 586 / 5015 / 0F /
  0E on kbb/SCI.
- BROKEN kbb/SCI on the unmodified engine with the new tests:
  **590 tests / 5050 assertions / 20 failures / 0 errors, exit 1** — all 20
  in torihiki.consensus-totality-test (raw:
  evidence/2026-11-01-1435-torihiki-fix-broken.{out,err}). Pin-level red
  modes as observed: the 3 flipped parity rows + 2 new rows answered nil
  from BOTH doors where the word is now expected (10 red); a vote naming a
  seeded ood proposal conj'd account 4 into `:votes` under the garbage key
  (actual #{4}); a confirm with a seeded ood claim bound `:principals`
  {9007199254740992 pid} and `:principal-accounts` {pid 9007199254740992}
  (2 red); a rotate with a seeded ood binding keyed `:rotations`
  {9007199254740992 {...}} (1 red); vouching evidence at an ood height AND
  at an ood view filed the slash under the garbage key, minted the reward to
  submitter 500 and debited the bond (2 x 3 red). Exactly 20.
- GREEN kbb/SCI: **590 / 5050 / 0F / 0E, exit 0**
  (evidence/2026-11-01-1435-torihiki-fix-green-kbb.out).
- GREEN JVM (script/jvm-test.sh, renamed copy): **590 / 5050 / 0F / 0E,
  exit 0** (evidence/2026-11-01-1435-torihiki-fix-green-jvm.out).
- GREEN nbb (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
  script/nbb-test.sh): **namespaces 34/34, 590 / 5050 / 0F / 0E, exit 0**
  (evidence/2026-11-01-1435-torihiki-fix-green-nbb.out).
- All three legs identical counters.

Test-file notes: the three parity rows that pinned the old residual
("...out-of-domain ... is still an integer", expected nil) flip with the fix
— intentional, they pinned exactly what this package removes; their labels
now name the boundary. New rows cover `:view` ood, `:principal-rotate`
`:subject` ood, and boundary guards (fx/i53-min/fx/i53-max names pass BOTH
doors — the guard against over-tightening). Four VALUE deftests (one fixture
per case, every pin routes through `applied` so a throw fails the pin):
seeded garbage-key proposal + vote, seeded garbage-key claim + confirm,
seeded garbage-key binding + rotate, and vouching-verifier evidence at ood
height/view — each pins what must stay unmuted in state.

Residual (documented, not fixed here): the withdraw `:claim` ids and the
cancel `:id`s stay `integer?` — names that can only look up a record created
under an in-domain key, pinned as admitted where they live (attest-totality,
agent/trigger-totality). The falsify-7/8 validate package proper (deposit
amount / order qty i53 caps, notional cap, balance-domain gate, accumulator
sum gates, mul-rate/REDUCING product bounds, rate caps) remains open in
maturity.md NEXT ②.

maturity.md untouched (measurement bots' file). Host checkout evidence/
carries a copy of this note for the measurement bots.
