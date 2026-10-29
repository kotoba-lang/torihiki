# 2026-10-30 02:3x torihiki-fix tick — LANDED: store-path totality package

Load gate: torihiki_state.sh 02:17 — 8.77 / 8.83 / 11.30, all windows < 20,
gate passed. Host load at tick end: not re-measured.

Branch bot/torihiki-fix-20261029-2223 (worktree kept from the 22:23/00:17
ticks) off origin/main b5c2cd9 (PR #79 merge). maturity.md untouched
(measurement bots' file).

## What landed

The store path is TOTAL: the REST of the apply-tx dispatches (PRs #78/#79
covered :order/:amend/:cancel/:trigger/:scale/:twap) drop a transaction they
cannot apply as a no-op — never a garbage key in the maps the encoders fold
into the state root, never a throw inside apply-block. Two doors, one shape
rule (torihiki.api/reasons at both):

- api.cljk: `int-in?`, `account-ok?`, `market-exists?` publicized; one public
  shape gate `store-tx-shape` over :deposit (account + credit key),
  :withdraw (account + amount 1..i53-max — the negative-amount MINT:
  `free >= -3` passed the free-collateral gate and paid the withdrawer),
  :evm-deploy (account — `account-as-address` threw on nil),
  :anchor (account + cid string 1..128), :create-sub-account (account),
  :set-referrer (account + referrer), :authorize-agent/:revoke-agent
  (account), :cancel-trigger (market — `update-in` CREATES the key),
  :funding-settle (market), :funding-sample/:cancel-all/:oracle/
  :oracle-submit (market + BOOK + signer/price). The book door is STRICTER
  than validate on purpose: validate passes a bookless market and the engine
  dereferenced the nil book (the falsify-6/7 class) — "never a throw"
  outranks "never over-refuse" at this door. Refusals answer keywords from
  api/reasons (:bad-account/:bad-amount/:bad-cid/:unknown-market/
  :bad-price-level); nothing here throws.
- state.cljk: the door at the TOP of the 14 store dispatches, before any
  destructured field is computed with (before `account-as-address`,
  `fnd/premium`, `bk/cancel-all!`, the `cl/withdraw` free-collateral gate,
  and every `assoc-in`/`update-in` that makes the key).
- test/torihiki/store_totality_test.cljk (new, 11 deftests): VALUE pins at
  the engine (nothing lands as a key, nothing moves, the mint pin: a refused
  withdrawal leaves no claim, no claim id spent, collateral untouched),
  over-refusal guards (every valid store/market tx still lands), and 21
  REASON regression pins at api/validate (unchanged by this package).
- script/tests-on-nbb.cljk: torihiki.store-totality-test registered; floor
  28 -> 29 with provenance.

## What was measured (exact counts)

- BROKEN (this tick, doors absent, tests as written): `kbb -M:test`
  **543 tests / 4080 assertions / 96 failures / 0 errors, exit 1** — all 96
  in torihiki.store-totality-test across 8 deftests (garbage keys stored:
  :accounts/:referrers/:sub-of/:agents/:triggers/:funding/:last-funding-rate/
  :oracle/:oracle-submissions/:anchors; 11 throw pins — a nil book
  dereference halted apply-block in 10 cases and the evm address derivation
  threw in 1); the 532-test baseline and the 3 valid/REASON deftests stayed
  green. Raw: evidence/2026-10-30-torihiki-fix-store-totality-broken.out.
- GREEN, all three legs, identical counters **543 tests / 4111 assertions /
  0 failures / 0 errors**:
  - SCI `kbb -M:test` exit 0 (evidence/2026-10-30-torihiki-fix-green-kbb.out)
  - JVM `script/jvm-test.sh` (renamed .cljk->.cljc copy, kotoba-lang dep
    shims) JVM_EXIT=0 (evidence/2026-10-30-torihiki-fix-green-jvm.out)
  - nbb `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
    script/nbb-test.sh` namespaces 29/29, TESTS-ON-NBB: pass, NBB_EXIT=0
    (evidence/2026-10-30-torihiki-fix-green-nbb.out)

## Test-file correction (found BY the runs, recorded as such)

Two `(empty? (:funding ex'))` pins were unachievable on the fixture:
new-exchange seeds every market's empty accumulator into :funding
(state.cljk:111), so the assert failed on the FIXTURE, not on the behavior.
The 00:17 author had already caught exactly this for the :oracle pin and
corrected it to a key-set pin; the :funding pins got the same treatment
(`(= #{1} (set (keys (:funding ex'))))`) after both a BROKEN run (garbage
keys measured beside the seed: {1 …, "5" …} / {1 …, 2.5 …} / …) and a
fixed run (seed only). The pinned claim — "no garbage key lands" — ran red
before the doors and green after; only the assertion shape changed.

## Known scope residual (documented, not fixed here)

:authorize-agent :agent/:expires value shape and :cancel-trigger :id shape
(validate words not mirrored at the engine), :liquidate/
:equivocation-evidence/:validator-*/:upgrade-*/:principal-* dispatches,
:deposit-attest/:transfer/:withdraw-*/:bond/:vault-* doors, apply-tx
:twap/:scale raw-garbage direct-call residual (PR #79's note), and all
JVM-leg failure modes ((long nil) etc. — the JVM leg RAN green here, but
the JVM-specific red shapes the pins reason about were never exercised as
red on the JVM: BROKEN was measured on the SCI leg only).
