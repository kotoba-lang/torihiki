# 2026-10-30 04:2x torihiki-fix tick — LANDED: money-door totality package

Load gate: torihiki_state.sh 04:18 — 8.17 / 16.29 / 19.60, all windows < 20
(15-min 19.60 near the gate), gate passed.

Branch bot/torihiki-fix-20261030-0420 (fresh worktree) off origin/main
6083458 (PR #80 merge). maturity.md untouched (measurement bots' file).

## What landed (PR #81)

The money-movement dispatches — :bond/:unbond/:collect-unbonded/:transfer/
:set-leverage/:vault-deposit/:vault-withdraw — are TOTAL: a transaction
`torihiki.api/validate` refuses is dropped whole, never a garbage key in the
maps the encoders fold into the state root, never a coerced amount in the
money arithmetic. One door: `api/store-tx-shape` carries the seven cases
(words and reason keywords exactly the validate mirror: :bad-account/
:bad-amount/:bad-quantity/:unknown-market/:missing-field); `torihiki.state`
gates each method at the top, before `cl/bond`/`cl/unbond`/`cl/transfer`/
`cl/vault-deposit`/`cl/vault-withdraw`/`cl/set-leverage` run. Refusals are
no-ops; nothing here throws. script/tests-on-nbb.cljk: floor 29 -> 30 with
provenance.

## What was measured (exact counts)

- Recon probes (kbb/SCI, at 6083458, uncommitted probe scripts): garbage
  lands at 4 doors — :bond keyed :bonds {1 {"5" 5000}} / {1 {2.5 5000}} /
  {1 {2^53+1 5000}} / {1 {-3 5000}} / {1 {nil 5000}} and coerced amounts
  ("5"->5, 2.5->2); :unbond moved stake on coerced amounts; :vault-deposit
  keyed :vaults AND minted garbage :accounts keys (str/frac/ood/neg/nil
  vault) and coerced shares; :transfer moved money on coerced amounts;
  :collect-unbonded/:vault-withdraw refused garbage; :set-leverage looked
  total in the probe but the probe fixture (new-exchange {:market 1}) made
  :max-leverage default to 1 and silently refused every leverage — the suite
  fixture (cl/market record) showed :leverage {"5" {1 2}} landing
  (cl/set-leverage assoc-in's [:leverage account market] directly). The
  BROKEN run is the ground truth; the probe discrepancy is recorded here
  rather than hidden.
- BROKEN (tests only, engine unmodified): `kbb -M:test` 551 tests / 4306
  assertions / 66 failures / 0 errors, exit 1 — all 66 in
  torihiki.money-totality-test across 7 deftests (the 8th, the valid-landing
  over-refusal guard, stayed green by construction). Raw:
  evidence/2026-10-30-torihiki-fix-money-totality-broken.out.
- GREEN, all three legs, identical counters 551 tests / 4292 assertions /
  0 failures / 0 errors:
  - SCI `kbb -M:test` exit 0 (evidence/2026-10-30-torihiki-fix-green-kbb.out)
  - JVM `script/jvm-test.sh` JVM_EXIT=0
    (evidence/2026-10-30-torihiki-fix-green-jvm.out)
  - nbb `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
    script/nbb-test.sh` namespaces 30/30, TESTS-ON-NBB: pass, NBB_EXIT=0
    (evidence/2026-10-30-torihiki-fix-green-nbb.out)
  Baseline before this PR: 543 tests / 4111 assertions (PR #80).
  Assertion count differs from the BROKEN run (4306 -> 4292) because the
  garbage sets changed with the pin corrections below.

## Pin corrections (found BY the runs, recorded as such)

1. The account-id garbage set carried -3 — but `account-ok?` judges
   slab-storability and a NEGATIVE id is a legal account id
   (`:backstop-vault -1` is one). -3 landing as a key is validate-PASSING
   traffic, not a bug; the id-garbage sets (validator/vault/to/account)
   now carry string/fractional/out-of-domain/nil only, amount sets keep
   negative. The pinned claim — "no garbage key lands, no coerced amount
   moves" — ran red before the doors and green after; only the garbage set
   narrowed to the contract's definition of garbage.
2. One key-set pin (`transfer to` garbage) forgot the fixture's second
   funded account (#{1} vs the real #{1 2}).

## Known scope residual (documented, not fixed here)

:deposit-attest/:withdraw-settle/:withdraw-attest/:withdraw-cancel/
:withdraw-invalidate-attest (the attest family — measured noop on the plain
path; the by-set/bridge paths need validator-set fixtures and were NOT
probed), :attest-reserves, :liquidate/:equivocation-evidence/:validator-*/
:upgrade-*/:principal-* dispatches, :amend-market/:list-market/:cancel-twap,
and the :authorize-agent :agent/:expires value shape residual from PR #80.
JVM-leg red shapes: the JVM leg RAN green here, but the JVM-specific red
modes (e.g. `(long "5")` throwing where JS coerces) were never exercised as
red on the JVM — BROKEN was measured on the SCI leg only, same caveat as
PR #80's landing.
