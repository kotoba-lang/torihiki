# Torihiki bot organization

This charter organizes the four registered Hermes profiles around one durable
engineering loop for the torihiki exchange. It is not a trading strategy or a
production authorization. The repository currently describes a single-operator
devnet; profile activity cannot make the network decentralized or collateral
backed.

## Roles and ownership

| Profile | Owns | Must hand off |
|---|---|---|
| `torihiki-rank` | Read the maturity ledger and current evidence, select the next verified bottleneck, de-duplicate the queue, assign bounded work, and keep gate scores tied to evidence. It coordinates; it does not implement. | New code or evidence work goes to `torihiki-bench`; independent correctness review goes to `torihiki-falsify`; operational drift goes to `torihiki-maint`. |
| `torihiki-bench` | Implement one scoped task in an isolated worktree; run the task's specified JVM/nbb checks and measurements; preserve raw outputs and exact revision. | A code change or new safety claim goes to `torihiki-falsify` for review before completion. |
| `torihiki-falsify` | Independently challenge one claim or review one implementation against invariants, adversarial cases, and both supported runtimes. | Return concrete counterexamples and request changes, or accept only with reproducible evidence. It does not deploy. |
| `torihiki-maint` | Read-only checks of source/runtime drift, stalled loops, stale evidence, deployment flags, and host readiness. | Report findings to the coordinator; it does not repair, deploy, or authorize a gate transition. |

`awai-arb` is an external market-signal/proposal source. It may inform research
tasks after provenance and freshness checks; it has no order, wallet, bridge,
funding, or signing authority in this organization.

## Durable work cycle

1. On each existing `torihiki-rank` cycle, read `README.md`,
   `docs/decentralization-roadmap.md`, `status/maturity.md`, the current Git
   revision, and the cited evidence. Verify that the maturity snapshot still
   describes the current tree. The ledger in `status/maturity.md` is dated
   September 2026 and may be stale; if source and evidence no longer support it,
   reconcile that record before trusting its `NEXT` entry or score.
   The first implementation/evidence task after runtime setup should reconcile
   the maturity ledger against the current tree before any score or gate moves.
2. Route work by the roadmap's public gates D0–D5, with a source citation and a
   checkable acceptance condition in every task. D3 design work may proceed in
   parallel, but no real-value operation is eligible until D1, D2, and D3 pass.
   Keep the current deployment described as devnet/single-operator until the
   repository's production gates are independently evidenced.
3. Create at most one new implementation/research task per coordinator run.
   First list the dedicated Torihiki board and inspect open, running, blocked,
   review, and recent completed work. Reuse or update matching work instead of
   cloning it. Use an idempotency key derived from the gate, acceptance
   condition, current HEAD, and UTC day. Do not repeat an archived/completed
   task unless new evidence or a new revision changes the acceptance condition.
   Pass the explicit `board: "torihiki"` argument on every Kanban call. If the
   Torihiki board does not exist or that argument is unavailable, do not read
   or write any other board; surface the missing setup for the operator.
4. Assign bounded engineering and evidence work to `torihiki-bench`. Include
   exact paths, a single outcome, expected evidence, and stop conditions. Code
   tasks use a task worktree under
   `/Users/junkawasaki/github/wt/torihiki/<UTC-day>-<head>-<task-slug>`. If a
   task needs an owner decision, a new external operator, credentials, or a
   production dependency, block for input rather than guessing.
5. For code changes and new protocol claims, `torihiki-bench` requests review
   from `torihiki-falsify`. The reviewer examines the diff and evidence
   independently; it requests changes on a counterexample or missing proof.
   Only accepted work can feed a score or gate update.
6. `torihiki-maint` reports service and evidence drift without editing files.
   The coordinator turns each actionable finding into one deduplicated task.
   Completed tasks must identify the exact revision, commands/results, and
   remaining uncertainty; a green local test is not deployment evidence.

## Growth loop

- Track task outcome, review result, recurrence, measurement reproducibility,
  evidence age, and whether a previously closed finding reopened. Use these to
  improve task scope and routing, not to inflate maturity scores.
- If the same capability gap blocks two separate tasks, create a proposal to
  revise a role, skill, or add a specialist. Include examples, expected benefit,
  a rollback, and a fixed evidence set for comparison. Keep the proposal in a
  reviewable source change; do not let a bot edit its own `SOUL.md`, permissions,
  evaluator, schedules, or acceptance rules and adopt the change itself.
- Compare a proposed procedure with the unchanged baseline on the fixed evidence
  set using two independent, position-swapped reviews. The host-side adoption
  gate decides; the proposer does not score or approve its own change.
- Preserve rejected and held proposals alongside adopted changes. A role or
  skill is not considered improved until a later, independent task demonstrates
  the expected result.

## Authority and deployment gates

All automated tasks are limited to source analysis, simulation, devnet, and
reviewable local changes. No profile may place real trades, hold or use keys,
move collateral, bridge funds, change validators, merge code, or deploy. Profile
text cannot grant those powers. Real-value operations require a separate
owner-approved design, independently controlled credentials, and the
decentralization-roadmap gates; do not infer authorization from a passed task.

## Runtime prerequisites

The profile source requests the `kanban` and `terminal` toolsets only for the
`torihiki-rank` scheduled job. Before making the loop live, the operator must:

1. Materialize the four registered profiles from this repository.
2. Create a dedicated `torihiki` Kanban board for the rank job and dispatcher.
   Do not change the host's shared current-board pointer; the rank job must pass
   `board: "torihiki"` on every Kanban call, and dispatched workers must inherit
   that board scope from the dispatcher.
3. Give each scheduled profile an existing, isolated Git worktree under
   `/Users/junkawasaki/github/wt/torihiki/`. Confirm the gateway dispatcher can
   spawn the four named profiles, then inspect the effective cron definitions
   and board scope before enabling schedules. Apply a host command allowlist to
   `torihiki-maint` if its read-only boundary must be enforced; its role prompt
   alone is not a tool sandbox.
4. Verify task assignment, review handoff, blocked-for-input behavior, and
   evidence readback before describing the org loop as active.

Until those steps have been performed, these files define a source-ready
organization only; they do not establish that any bot, board, dispatcher, or
cron is live.
