---
name: torihiki-self-organization
description: Route Torihiki maturity work through evidence, durable tasks, specialist review, and gated learning.
---

# Torihiki self-organization

Use this skill for Torihiki roadmap, task routing, maturity scoring, or profile
coordination. Follow `hermes/organization/README.md` as the team contract.

## Coordinator procedure

1. Read `README.md`, `docs/decentralization-roadmap.md`, `status/maturity.md`,
   current Git revision/status, and cited evidence. Treat old maturity text as a
   hypothesis until its citations match the current tree.
2. List the dedicated Torihiki Kanban board before creating work. Pass the
   explicit `board: "torihiki"` argument on every Kanban call; never rely on the
   host's shared current-board setting. If the board does not exist or the
   explicit argument is unavailable, do not call Kanban tools. Do not mark a
   gate from a plan, profile, cron definition, local-only check, or report
   without the roadmap's production evidence.
3. Select the earliest unsatisfied dependency gate, except explicitly parallel
   D3 design work. Turn one bottleneck into one bounded task with a measurable
   acceptance condition and idempotency key based on gate, acceptance condition,
   current HEAD, and UTC day. Check recent completed work too; do not repeat an
   archived task unless new evidence or a new revision changes the acceptance
   condition. Do not fan out speculative work.
4. Route implementation/evidence tasks to `torihiki-bench`, adversarial review
   to `torihiki-falsify`, and read-only readiness checks to `torihiki-maint`.
   Code tasks must use a task worktree under
   `/Users/junkawasaki/github/wt/torihiki/<UTC-day>-<head>-<task-slug>`. The
   coordinator does not implement the assigned task.
5. For a code or protocol task, require a `torihiki-falsify` review handoff.
   Preserve exact revision, commands, raw evidence paths, reviewer verdict, and
   unresolved questions in the durable task record.
6. If scope needs a user decision, credentials, external operators, a budget,
   or production access, block with a precise question. Never fill that gap with
   an assumption or with a new authority.

## Self-improvement procedure

Record recurring failures, duplicate work, review findings, stale evidence, and
measurement reproducibility. After the same capability gap blocks two separate
tasks, draft a source-level profile/skill proposal with examples, a rollback,
and a fixed comparison set. A bot may recommend the change but may not change
and activate its own persona, permissions, evaluator, cron, or acceptance gate.
Compare the proposal with the unchanged baseline on that evidence using two
independent, position-swapped reviews. Keep held/rejected/adopted decisions
visible and require later independent evidence before calling a learning change
effective. The host-side gate, not the proposer, decides adoption.

## Non-negotiable limits

- Research, tests, and development are limited to source, simulation, and
  devnet. No live orders, wallet or signer use, bridge movement, validator
  changes, merge, or deployment.
- `awai-arb` signals are inputs only and require freshness/provenance checks.
- A DEX milestone is not complete until its specific public roadmap gate has
  passed in the target deployment; the current repo claim remains
  devnet/single-operator.
- If the Kanban board cannot be proven Torihiki-only, leave it unchanged and
  report the missing runtime scope.
