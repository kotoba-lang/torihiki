# Decentralization roadmap

Status: proposed
Date: 2026-10-07
Scope: `torihiki` (engine), `torihiki-node` (validator / sequencer), `torihiki-terminal` (client)

## Context

The README calls torihiki "a deterministic, fully on-chain exchange state
machine — the open replacement for Hyperliquid's closed HyperCore". The
**engine** earns that sentence: book, clearinghouse, funding, liquidation,
oracle aggregation, bonding, deposit/withdraw attestation are one pure
`(state, txs) -> state'`, and a replica verifies a block by replaying it.

The **deployment** does not. Today the chain is operated by one party, funded
by a faucet, and has no exit:

| Surface | Today | Single point of failure / trust |
|---|---|---|
| Ordering | `torihiki-node` = one Durable Object sequencer; `torihiki-validator-v3` = 4 replicas (w1–w4, quorum 3) on `inga.consensus` | all four replicas on one Cloudflare account |
| Validator keys | `W1_KEY`…`W4_KEY` are Worker secrets of that account (`torihiki-node/src/torihiki_node/validator.cljk:507`) | whoever holds the account can sign as a quorum |
| Validator set | public keys hardcoded in source; no set-change tx, no key rotation (`deploy/README.md`, "What it does not recover from") | changing the set = redeploy by the operator |
| Liveness | 2026-09-23: v3 stalled at height 5805, tip uncertified, w1/w3 reported as equivocators; sequencer refused current clients (code-version 12) | no running BFT chain to measure |
| Collateral | `:bridge-authority` = faucet account; `BRIDGE_KEY` a Worker secret; `faucet-grant` mints on request (`validator.cljk:1020,1079`) | collateral is unbacked; one key is a mint |
| Exit | `:withdraw` records a claim; `:withdraw-attest` needs 3 bonded attesters of a payout — **nobody is defined to make that payout** | withdrawals pay out nowhere |
| Oracle | `torihiki.oracle` aggregates a quorum of publishers; publishers are operator-run | price = operator's price |
| Client | `torihiki-terminal/src/.../client.cljk:52` falls back to the single sequencer when the page names no node | users silently on the non-BFT path |
| Data | log + checkpoints in DO SQLite (`store.cljk`); no public archive | history = operator's copy |

### The escrow design has a hole that must be fixed before it is built further

`torihiki.thorchain` assumes a user can send an asset to THORChain's inbound
vault with memo `TORIHIKI:<account>` and that "the asset sits behind a key no
single party holds" until torihiki pays it back out. That is not how THORChain
works:

- THORChain vaults only act on memos THORChain itself understands (`SWAP:`,
  `ADD:`, …). An unrecognized memo is **refunded** to the sender by THORChain,
  not held for a third-party protocol.
- THORChain's validators will never sign an outbound on torihiki's behalf.
  `payouts-in` attests a payout "written by whoever sent it", but there is no
  party able to send it from that vault.

So the THORChain path can at best *observe* value; it cannot *custody* it for
torihiki. Escrow must be a vault torihiki's own validator set controls. This is
the same conclusion Hyperliquid reached (Bridge2 on Arbitrum).

## Goal and non-goals

**Goal**: no single party — including us — can (a) mint collateral, (b) block
or forge a withdrawal, (c) move the price, (d) halt or rewrite the chain, or
(e) change the validator set or code, without that being refused by the
protocol or visible and slashable.

**Non-goals for this roadmap**: matching Hyperliquid's 200k orders/s; an EVM
composability layer beyond the existing `:evm-deploy`; a native token.
Throughput is tracked in the README benchmark section, not here.

## Decision: six phases, each with a public flag

Each phase flips one boolean in `/head` (and `/.well-known/torihiki`) only when
its gate passes in production. **Until all of D1–D4 are true, docs and the
terminal must not call torihiki "decentralized" or "on-chain" without the
qualifier "devnet, single operator".** This mirrors nexus-x402 ADR-0006.

| Phase | Flag | Removes |
|---|---|---|
| D0 | `claims-honest` | misleading wording, silent sequencer fallback |
| D1 | `bft-live` | dependence on the single sequencer |
| D2 | `operators-independent` | one party holding a quorum of keys |
| D3 | `collateral-backed`, `exit-live` | faucet mint, no exit |
| D4 | `oracle-independent` | operator-set prices |
| D5 | `history-public`, `upgrades-governed` | operator-held history, admin upgrades |

Order is by "what makes the next step meaningful". D3 can be built in parallel
with D1/D2, but must not hold real value before D2 passes: a bridge secured by
validators that one party runs is a custodial bridge with extra steps.

---

### D0. Honest baseline (days)

- `/head` already states "devnet faucet" when `:bridge-authority` is nil.
  Extend it with the eight flags above (six phases), all `false`.
- `torihiki-terminal`: default to the BFT validator; remove the silent
  fallback to the sequencer, or show a banner "single sequencer, not
  consensus" when it is used.
- README first paragraph: keep "state machine", qualify "fully on-chain" with
  the current deployment status and link here.
- Mark `torihiki.thorchain` as observation-only (see Context) and stop
  extending the THORChain escrow path.

**Gate**: flags served; terminal shows which path it is on; README updated.

### D1. A BFT chain that stays up, off Cloudflare (weeks)

`deploy/README.md` already measured the answer: Durable Objects give
74–104 ms/block, ordinary hosts 3–13 ms/block, Containers neither. The
validator should run as the `nbb` process under `torihiki@.service`/launchd.

1. Fix the v3 stall. Reproduce the 2026-09-23 state (height 5805, uncertified
   tip, w1/w3 equivocators) in the `engi` `torihiki-on-engi` harness; the
   history of accidental equivocation in `validator.cljk` (pacemaker deadline
   0, view-scoped equivocation, inga e4974f7) says this is still the dominant
   failure mode.
2. Run w1–w4 on **four hosts**, each with a `SEED_Wn` generated on that host.
   No validator key in any Worker secret.
3. Keep a Worker/DO only as a read cache and `/tx` relay in front of the
   validators (stateless; forwards to any 1 of N).
4. Retire `torihiki-node` (single sequencer) once the terminal no longer
   points at it.
5. Measure end-to-end latency with `script/latency_probe.cljk` against the
   chain, which the README says could not be done on 2026-09-23.

**Gate**: 14 consecutive days of block production on 4 hosts with
(a) one host killed for ≥1 h — chain continues; (b) one replica fed a
deliberately Byzantine history — repaired from quorum, no split root;
(c) zero equivocation reports from honest replicas; (d) latency p50/p99
published.

### D2. Independent operators and a changeable validator set (1–2 months)

The engine has the pieces: `clearing/bond` with `unbond-delay-blocks`,
`inga.stake` (permissionless stake-weighted admission, equivocation-only
slashing). What is missing is wiring and people.

1. **Validator-set change transaction** — add/remove a validator by
   stake-weighted >2/3 certificate, activated at a future height. Replaces the
   hardcoded key list (`validator.cljk:498-554`).
2. **Key rotation** for a validator (today: "losing its seed — no rotation").
3. **Stake-weighted quorum** in consensus: the set and weights come from chain
   state (`inga.stake/stake-qc`), not from source.
4. **Equivocation evidence transaction**: any party submits two conflicting
   signed votes → automatic slash. Today equivocators are *reported*; they must
   be *punished* by the chain.
5. **Genesis ceremony**: each operator generates its key, publishes the
   public half, and the genesis set is a signed document, not a commit by us.
6. Recruit **≥4 operators that are not us, plus us at <1/3 stake**, across
   ≥3 hosting providers / jurisdictions. Candidate pool: kotoba-lang
   community, murakumo fleet partners, the nexus-x402 ADR-0006 L4 operator.

**Gate**: a validator-set change and a key rotation executed on the live
chain; a staged equivocation (two votes, one key) slashed by evidence tx; our
own stake <1/3, so the chain continues — and refuses our blocks — if every
node we run lies.

### D3. Backed collateral and a real exit (2–3 months, parallel with D1/D2)

Replace the THORChain escrow with a **validator-controlled bridge contract**
on one EVM L2 (the Hyperliquid Bridge2 shape):

- `Bridge.sol` holds USDC. `deposit(account, amount)` emits an event.
- Validators observe the event through a **multi-RPC quorum** (k-of-N
  independent RPC providers, same design as nexus-x402 ADR-0006 L2) and
  submit `:deposit-attest`; the engine already credits only on
  `attest-quorum` distinct bonded attesters, exactly once per txid.
- Withdrawal: `:withdraw` creates claim *id*; validators sign
  `(claim, dest, amount, nonce)`; the contract releases funds only with
  signatures of **>2/3 of the validator stake it currently knows**, after a
  **dispute window** (e.g. 200 s, as Hyperliquid) during which any single
  validator can pause the bridge ("locker").
- Validator-set updates (D2) are mirrored to the contract by a >2/3-signed
  update message; the contract never trusts an admin key. No upgrade proxy,
  or a proxy owned by the same >2/3 set with a timelock.
- `:withdraw-attest` then records the contract's `Withdraw` event, so the
  chain can follow money off-chain without anyone "sending" a payout by hand.
- Remove `faucet-grant`, `BRIDGE_KEY` and `:bridge-authority` from any chain
  that holds real value. The faucet survives only on a testnet with a
  different chain id.
- **Proof of reserves**: make collateral non-negativity an invariant (insurance
  fund / ADL absorbs shortfall before an account leaf goes negative) so the
  merkle-sum root in `commit.cljk` can carry a real total, and publish
  `contract balance ≥ Σ collateral + Σ pending claims` per block.
- External audit of `Bridge.sol` before mainnet; deposit cap (e.g. $10k total,
  then raised by governance) until it has run under value.

Chain choice is open (see Open questions); Base reuses the nexus-x402 RPC and
USDC tooling, Arbitrum matches Hyperliquid and its liquidity.

**Gates**:
`collateral-backed` — on testnet then mainnet-with-cap, every account balance
traces to a contract `Deposit` attested by quorum; faucet mint refused;
reserves equation holds every block for 14 days.
`exit-live` — a withdrawal paid by the contract with >2/3 signatures, a
withdrawal attempt with 1/3 signatures refused by the contract, and a pause
by one validator during the dispute window, all on the live deployment.

### D4. Independent oracle (weeks, after D2)

`torihiki.oracle` already takes a quorum of publisher submissions with max-age
and stops liquidation when stale. Make the publishers the validator set:

- Each validator publishes its own price from ≥3 external venues it chooses
  (weighted median, as Hyperliquid), signed into a block every N blocks.
- `:oracle-publishers` = current validator set, stake-weighted median;
  operator-only publishers removed.
- Deviation alarms: a validator whose submissions are persistently outliers is
  visible in `/head`.

**Gate**: on the live chain, one validator submitting a price 10 % off moves
the oracle by less than the band; two validators offline → stale, liquidation
paused, no wrong price.

### D5. Public history and governed upgrades (ongoing)

- **History**: blocks and checkpoints content-addressed (`inga.ref` CIDs,
  already the direction in `validator.cljk`), pinned by every validator and
  ≥1 third party; a non-validator full node that replays from genesis to the
  current state root is documented and run by someone other than us.
- **Light clients**: the terminal verifies its own balance with the
  `merkle-sum` inclusion proof against a quorum-certified root instead of
  trusting the RPC answer.
- **Upgrades**: `code-version` changes only via an on-chain proposal accepted
  by >2/3 stake with an activation height. No `wrangler deploy` changes
  consensus rules. (The README already records that a DO does not pick up a
  deploy and that a mixed-version set disagrees on roots; governed activation
  fixes both.)

**Gates**: third-party full node in sync for 7 days; one code upgrade
activated by on-chain vote with zero split roots.

---

## Timeline (indicative)

```
Oct            Nov            Dec            Jan            Feb
D0 ■
D1 ■■■■■■■■■■■■
D2        ■■■■■■■■■■■■■■■■■■■■■■
D3   ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■ (testnet) ─ audit ─ capped mainnet
D4                       ■■■■■■■■
D5                              ■■■■■■■■■■■■■■■■■■■■■■■■■■■■ →
```

Real value (any non-test deposit) requires D1, D2 and D3 gates. Lifting the
deposit cap additionally requires D4 and the audit.

## Consequences

- Cloudflare stops being the trust root; it becomes an optional read cache.
- We lose the ability to fix the chain by redeploying. Every rule change goes
  through governance; mistakes cost an upgrade cycle instead of a deploy.
- New operating surface: operator onboarding, key ceremonies, bridge contract
  maintenance and audit, RPC quorum, pinning.
- Honest claim boundary: each flag is public and false until its gate passes.

## Open questions

1. Bridge chain: Base (shared tooling with nexus-x402) or Arbitrum (same as
   Hyperliquid, deeper perp liquidity)?
2. Stake asset: USDC bonded on the bridge chain (as `inga.stake` assumes) or
   torihiki collateral bonded in-chain (`clearing/bond`)? Slashing must reach
   whichever it is.
3. Who are the first four external operators, and what do they earn
   (fee share)?
4. Keep THORChain at all? Plausible later role: a swap *route* into the bridge
   for non-USDC assets, never custody.
5. Is a dispute window plus lockers enough, or do we want a ZK proof of the
   withdrawal claim against the state root (removes validator trust for exits,
   much larger effort)?

## References

- `torihiki/README.md` — "Collateral has to come from somewhere", "Against
  Hyperliquid", "What is not here"
- `torihiki/src/torihiki/state.cljk` — `:deposit-attest`, `:withdraw`,
  `:withdraw-settle`, `:withdraw-attest`
- `torihiki/src/torihiki/thorchain.cljk`, `oracle.cljk`, `clearing.cljk`
- `torihiki-node/deploy/README.md`, `src/torihiki_node/validator.cljk`
- `inga/src/inga/stake.cljk`, `kotoba-lang/engi`
- `network-awai/nexus-x402/docs/adr/0006-decentralization-roadmap.md`
- Hyperliquid docs: HyperBFT, Bridge2 (validator-signed withdrawals,
  dispute period, lockers), oracle (validator-weighted median)
