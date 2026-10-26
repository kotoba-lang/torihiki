# 2026-10-27 02:22–03:0x — torihiki-fix landing: log wire codec made total

Branch `bot/torihiki-fix-20261027-0222` on origin/main 48582c7 (PR #69 base).

## What landed

`torihiki.log` tx wire codec (`tx->wire` / `wire->tx`) is total over the EDN
subset a transaction can carry, instead of a whitelist of 8 kinds x 10 integer
fields. Two measured failures closed at the codec layer:

1. **Commit-path halt**: a tx whose kind is not in the 8-kind whitelist
   (`:amend`, `:trigger`, `:cancel-all`, `:anchor`, forty more that `apply-tx`
   dispatches) threw `torihiki.log: unknown transaction kind` out of
   `commit-block!` — one legal tx stopped the sequencer, violating
   `apply-block`'s "never thrown from" liveness contract one layer up.
2. **Quiet fork**: every field the whitelist did not know was SILENTLY
   DROPPED — a `:trigger` lost its nested `:order`; replay then recomputed a
   different block than the one applied.

Now: the KIND stays explicit under "tx"; the VALUE space round-trips exactly
(`= tx (wire->tx (tx->wire tx))`), including the txs the engine rejects —
those are part of the block's outcome (`:rejected` with `torihiki.api/reasons`
keywords lands in the state root). Integers/strings keep the bare wire shape
every committed block already has, so old logs keep decoding. Keywords/
booleans/nils/nested maps/vectors/sets encode tagged. Map/pair entries sort by
printed key encoding so the CID cannot depend on hash iteration order.

Refusals are unchanged in vocabulary: the codec itself refuses only values
with NO wire form (non-EDN), loudly.

## Tests (RED before GREEN, both run broken)

test/torihiki/log_test.cljk: `wire-covers-the-engine-vocabulary` (7 shapes:
trigger-with-order, amend, cancel-all, list-market spec, authorize-agent,
anchor, unknown fields/namespaced keys), `wire-round-trips-what-the-engine-
rejects` (unknown kind, non-integer amount, string kind, namespaced kind),
`wire-rejects-what-it-cannot-validate` (no-wire-form value throws),
`a-malformed-tx-is-rejected-into-the-root-not-thrown` (commit-blocks! commits;
rejections pin `:bad-amount` / `:unknown-tx`; replay agrees).

- **RED 1** (new tests, old codec): kbb -M:test — 537 tests / 3633 assertions,
  **1 failures + 9 errors** (codec throws out of commit-blocks!; field drops).
- **RED 2** (two kind cases vs pre-fix kind encoding: `str`-colon keyword
  kind, undecodable `["s" ...]` string kind): kbb -M:test — 537 / 3637,
  **2 failures**.
- **GREEN** (all three legs, identical counts): **537 tests / 3637 assertions,
  0 failures, 0 errors** on `kbb -M:test` (SCI), `script/jvm-test.sh` (JVM,
  renamed .cljk→.cljc copy + dep shims), and `KOTOBA_CHECKOUTS=/Users/
  junkawasaki/github/kotoba-lang script/nbb-test.sh` (nbb/Node, 29/29 ns,
  "TESTS-ON-NBB: pass").

Note for runners: `script/nbb-test.sh` needs `KOTOBA_CHECKOUTS` pointed at the
sibling-checkout object store when the worktree is not beside the pins
(/Users/junkawasaki/github/kotoba-lang here).
