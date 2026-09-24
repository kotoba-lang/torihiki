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
| `torihiki-bench` | torihiki 成熟ループ: bench (test/bench 実測, 30min cron cowork) |
| `torihiki-falsify` | torihiki (Hyperliquid 型取引ステートマシン) 成熟ループ: falsify (15min cron cowork) |
| `torihiki-maint` | torihiki 成熟ループ: maint watch (red detection, 30min cron cowork, read-only) |
| `torihiki-rank` | torihiki 成熟ループ: rank (7軸再測定, 15min cron cowork) |
