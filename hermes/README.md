# hermes/ — the resident bots that act for this repository

This directory is the **source of truth** for the Hermes profiles listed below
(ADR-2609241200). The host's `~/.hermes/profiles/<profile>` is materialized
from `hermes/profiles/<profile>/` and checked against it:

```
kbb --backend sci scripts/hermes-profile-repo.cljk materialize <profile>   # repo -> host
kbb --backend sci scripts/hermes-profile-repo.cljk check <profile>         # 0 agree / 1 drift / 2 could not compare
kbb --backend sci scripts/hermes-profile-repo.cljk export <profile>        # host -> repo, then commit
```

(run from the com-junkawasaki/root superproject; registry
`manifest/hermes-profile-repos.edn`.)

Each profile directory holds SOUL.md, profile.yaml, config.yaml (host-local
blocks removed), cron/jobs.json (definitions only), scripts/ and the skills the
profile owns. **Never here:** `.env` or any secret value, workspace/ledgers,
sessions, memories, logs, caches, run state.

## Profiles

| profile | description |
|---|---|
| `torihiki-rank` | org coordinator: evidence-based prioritization, Kanban routing, maturity ledger stewardship |
| `torihiki-bench` | bounded implementation and reproducible test/benchmark work; no deploy or merge |
| `torihiki-falsify` | independent correctness/security falsification and implementation review |
| `torihiki-maint` | read-only readiness, drift, and evidence freshness watch |

The team charter, DEX readiness gates, task routing, and learning rules are in
[`organization/README.md`](organization/README.md). The self-organization
skill used by `torihiki-rank` is in
`profiles/torihiki-rank/skills/torihiki-self-organization/SKILL.md`.

This repository is profile **source**, not runtime activation. The rank cron
requests the Kanban toolset in its job definition; a dedicated `torihiki` board,
profile materialization, dispatcher availability, and live cron state must be
configured and checked on the host separately. Do not switch the shared current
board to make this profile work; use a board-scoped runtime instead.
