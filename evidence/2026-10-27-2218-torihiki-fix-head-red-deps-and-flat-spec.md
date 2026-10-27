# 2026-10-27 22:18 torihiki-fix tick — HEAD measured RED twice (deps classpath + cl/flat-spec-ok?), fix PARTIAL, not landed

Branch `bot/torihiki-fix-20261027-2218` (worktree `~/github/wt/fleet/bot/torihiki-fix-20261027-2218`, base origin/main **a0add53**). No PR, no merge — the suite was never green this tick.

## Load gate
22:17 uptime **8.94 / 19.90 / 17.62**, 22:2x **8.12 / 19.00 / 17.35** — not all windows > 20, so the tick ran.

## Survey (all of maturity.md NEXT against origin/main)
Fix packages in NEXT are LANDED at HEAD: validate i53/notional/balance-domain gates (api.cljk:141/:148/:373), accumulator sum gates via fx/acc (funding.cljk:85/:148 etc.), settle-deficit delta clamp + pmul + mul-rate (pinned in test/torihiki/overflow_gates_test.cljk, 65 deftests), bench 3-part fix (bench.cljk:123 3-arg cancel!; probe.cljk:31, curve.cljk:26 3-arg), fuzz 常設化 (test/torihiki/fuzz_seeded_test.cljk wired), nbb-classpath bootstrap (script/nbb-test.sh etc.), repro3 bench run (PR #59 `3fb5add`). Unlanded NEXT item left = the HEAD re-anchor re-measurement — which found the two defects below.

## Measured defect 1 — `kbb -M:test` cannot build its classpath at HEAD (deps-extraction fallout, PRs #74/#75)
`kbb -M:test` EXIT=1 before any test ran:
`Error building classpath. No known ancestor relationship between local versions for io.github.kotoba-lang/text: ~/.gitlibs/…/edn/b94158f…/lib/kotoba.text and ~/.gitlibs/…/torihiki-core/c59d8fd…/lib/kotoba.text`
Cause: every pinned kotoba monorepo checkout reaches kotoba.text / kotoba.merkleSum transitively as `{:local/root "../kotoba.text"}` — one "local version" per CHECKOUT — and nbb.edn named no top-level text/merkle-sum version to defer to. (JVM `clojure -Stree` survives via `:use-top`; kbb's borkdude.deps builder does not.) Mixed shas were a red herring: failure persisted with both roots at b94158f.
FIX (applied, verified): nbb.edn gains top-level pins for `io.github.kotoba-lang/text` and `merkle-sum` at b94158f; torihiki-core pin aligned c59d8fd → b94158f (byte-identical core: empty `git diff c59d8fd b94158f -- lib/kotoba.torihikiCore`; commits between are harness-only). After the fix the classpath build passes ("Downloading… Extracting… Done.").

## Measured defect 2 — `cl/flat-spec-ok?` does not exist in the pinned core (PR #73 orphaned by PR #74)
With the classpath fixed, `kbb -M:test` fails at analysis: `Unable to resolve symbol: cl/flat-spec-ok?` — src/torihiki/api.cljk:630 (also :602; state.cljk:1233/:1307). PR #73 (350128e, merged 2026-10-27 03:25Z) added `flat-spec-ok?` to the then in-repo src/torihiki/clearing.cljk; PR #74 (merged 03:32Z) replaced clearing with the kotoba `torihiki-core` pin whose import (kotoba c59d8fd = kotoba PR #912) predates #73. Verified: `flat-spec-ok?` exists in NO kotoba sha — main's history of lib/kotoba.torihikiCore is exactly one commit (c59d8fd), and neither c59d8fd nor b94158f contains it (both have margin-tiers-ok?/fee-tiers-ok? only). A pin bump cannot fix this; `flat-spec-ok?` must be re-homed in-repo (api or a small shared ns; state requires api at state.cljk:34 so api is a cycle-free home) and the 4 call sites + overflow_gates_test pins rewired — or imported into the kotoba core and the pin bumped (two-repo change, out of tick scope).

## What is left
1. Re-home `flat-spec-ok?` in-repo (definition from 350128e's clearing.cljk hunk, ~40 lines incl. docstring), rewire api.cljk:602/:630, state.cljk:1233/:1307, overflow_gates_test's cl/flat-spec-ok? pins → green on all three legs (expected counters 546/3697 per #73's measurement).
2. Land as one package with this deps fix; run BROKEN→GREEN as #73 did.
3. Follow-up for the monorepo: import flat-spec-ok? into kotoba lib/kotoba.torihikiCore so "the code exists once" holds again.

Raw runs: /tmp/tfix2218/kbb-test{,2,3}.{out,err} (on the run host; kbb-test3.err shows the flat-spec-ok? analysis error).
