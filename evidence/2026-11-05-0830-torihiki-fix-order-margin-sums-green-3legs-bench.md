# 2026-11-05 08:3x torihiki-fix tick — order-margin #50 running sums: GREEN
# on all three legs, bench acceptance measured on kbb/SCI, LANDED via this
# branch (PR + merge attempted in the same tick; gh auth was broken — see
# "Landing"). "not run" != "passed": everything unmeasured is named below.

Load gate: uptime direct 08:18 — 1.78 / 1.51 / 1.44, all windows < 20, gate
passed. torihiki_state.sh absent on this host (same as the 06:19 handoff);
direct uptime per the chronic-fault precedent.

Continuity: this tick finishes branch bot/torihiki-fix-20261105-0619 (commit
faa504f = the 06:19 handoff: implementation + tests written, BROKEN-first
measured, GREEN red with 2 diagnosed bugs). Queue: maturity.md NEXT is
stale (all landed — verified by the 02:37/04:39 records); the sole open item
is issue #50.

## What was fixed this tick (the two handoff bugs)

1. fold-one's `:sat?` was the raw `or` chain — with no clamp anywhere and no
   loss term (`f` nil) the chain ENDS in nil, so `:sat?` was nil (falsy),
   which silently disabled the sat?-on-negative recompute. Wrapped in
   `boolean`.
2. `adjust` ran `prune` BEFORE the sums write; a negative delta removing the
   last level dropped the entry, and the nil-sums branch re-created it as a
   ghost `{:sums {:mark m}}` no rebuild can produce (this is what turned the
   cache-equals-rebuild property test red). Fix: if the entry is gone after
   prune, return `oo`. Handoff bug 3 ("sums missing entirely on a pure-adds
   scenario") was the same prune-drop path — no third bug; it is gone with
   1+2.

## Measured this tick (exact counts)

- Pre-fix GREEN reproduction (both bugs live), kbb/SCI: 649 tests / 5526
  assertions / 487 fail / 0 error, exit 1 — the failure dumps confirmed both
  diagnoses (`:sat? nil` in the sums pins; ghost `3 {1 {:sums {:mark 101}}}`
  in the property-test state).
- After the two fixes — kbb/SCI `kbb -M:test`: **649 tests / 5526
  assertions / 0 failures / 0 errors**, exit 0 (/tmp/om-green2-kbb.out).
- JVM `script/jvm-test.sh` (renamed-copy recipe): **649 / 5526 / 0F / 0E**,
  exit 0 (/tmp/om-green2-jvm.out) — same count as SCI.
- nbb `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
  script/nbb-test.sh`: **649 / 5526 / 0F / 0E, namespaces 38/38,
  TESTS-ON-NBB: pass**, exit 0 (/tmp/om-green2-nbb.out). All three legs
  agree at 649/5526.
- BROKEN-first for the new tests is the 06:19 run (649/5526, 11 fail against
  unfixed src, every new pin RED) plus this tick's 487-fail reproduction.
- Bench acceptance 3 (2,000-level), kbb/SCI `kbb -M:bench-om`, best-of-5 of
  20,000 queries, stable marks:
    levels=  8, sums       : 18,299.99 ns/query
    levels=2000, sums       : 17,849.99 ns/query   (flatness 0.98x)
    levels=2000, pure fold  : 3,027,649.99 ns/query (169.6x the sums)
    mark move, rekey L=2000 : 1,829,000.06 ns/move (honest: one O(L) fold
                              per entry per move)
  The O(1) claim holds: the query cost is flat in the level count, the fold
  is O(L).

## Not run / not claimed

- The bench on the JVM leg (`script/jvm-leg.sh bench-om`) and the nbb leg:
  NOT RUN (runtime budget). The bench numbers above are kbb/SCI only.
- The 2,000-level bench is new in this landing (bench/torihiki/order_margin_bench.cljk
  + deps.edn alias :bench-om), written to the 02:37 bench plan.

## Landing

Branch bot/torihiki-fix-20261105-0619 carries: the 06:19 WIP commit, this
tick's two fixes, the bench, this evidence. gh auth is BROKEN on this host
this tick ("The token in default is invalid" / "no oauth token found for
github.com" — it worked at 06:19), so the PR/merge step may not complete;
git push works via the credential helper. Whatever the PR step prints is the
truth of what landed — do not assume a merge from this file.
