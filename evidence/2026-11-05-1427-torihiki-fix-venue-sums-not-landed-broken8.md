# 2026-11-05 14:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# queue verification + baseline + BROKEN-first measurement). src/ untouched,
# no PR. "not run" != "passed": the only greens claimed below are the baseline
# counters and the BROKEN run, both run and counted this tick.

Load gate: 14:17 uptime direct — 2.55 / 2.45 / 2.19, all windows < 20, gate
passed. ~/.hermes/scripts/torihiki_state.sh is ABSENT on this host (not an
empty-output fault — no such file); direct uptime per the chronic-fault
precedent in the maturity frames.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-1427, branch
bot/torihiki-fix-20261105-1427 off origin/main ff0427d (PR #103 merge). This
commit is the handoff. maturity.md untouched (measurement bots' file).

## Queue verification at origin/main ff0427d (measured, exact)

maturity.md NEXT is STALE — every fix item is landed (independently
re-verified this tick, not just cited from the 04:3x record):
validate-i53-halt package (overflow_gates_test.cljk present, 65 deftests),
bench-tape-cancel-arity (bench 3-arity with owner), fuzz 常設化
(fuzz_seeded_test.cljk), wire-number-canon (PR #93), venue :place totality
(PR #101), order-margin #50 running sums (PR #102), venue-router reconcile
(PR #103). Open issues: #76 only — `terminal:` (torihiki-terminal), out of
scope per tick. No scoped-not-landed branch remains (remote bot branches
20261105-0237 / -0439 carry evidence-only handoff commits, superseded).

## Environment discoveries (the next tick needs these)

1. The local `main` checkout at
   /Users/junkawasaki/github/kotoba-lang/torihiki is STALE at 2201d03 (PR #91)
   while origin/main is ff0427d (PRs #92–#103) — reads of the local tree lie
   about venue_fake.cljk (no :place door there) and venue_router.cljk (no
   reconcile sum). Always work from origin/main in the worktree.
2. `kbb -M:test` exits 66 with "kbb: no deps.edn in /" if the shell workdir is
   lost (background-promoted terminal dropped it once) — cd inline.
3. `kbb -M:test` needs the keg-only JDK on PATH
   (/opt/homebrew/opt/openjdk/bin) or the deps door dies with the macOS
   "Unable to locate a Java Runtime" stub. kbb itself is JVM-free; the -M:
   alias door shells out through the clojure CLI. Export before every leg:
   PATH="/opt/homebrew/opt/openjdk/bin:/Users/junkawasaki/github/kotoba-lang/kotoba/bin:$PATH"
   (kbb is not on PATH either).

## Measured this tick (exact counts)

- Baseline GREEN kbb/SCI at ff0427d (unmodified tree):
  **650 tests / 5538 assertions / 0 failures / 0 errors**, KBB_EXIT=0
  (/tmp/tori-1427-baseline-kbb.{out,err}) — matches PR #103's counters.
- BROKEN kbb/SCI (new tests against unfixed src): **654 tests / 5554
  assertions / 8 failures / 0 errors**, KBB_EXIT=1
  (/tmp/tori-1427-broken-kbb.{out,err}). All 8 failures are exactly the new
  pins — 4 new deftests red:
  the-fakes-position-sum-never-leaves-the-domain (2),
  the-routers-position-sum-never-leaves-the-domain (2),
  the-position-limit-door-sees-the-saturated-position (1),
  the-reconcile-sum-never-leaves-the-domain (3).
- JVM leg (script/jvm-test.sh): NOT run (BROKEN and GREEN both).
- nbb leg (script/nbb-test.sh): NOT run.
- GREEN after fix: NOT run. PR: none.

## The fix package (scoped + BROKEN-proven, ready to land): venue sums

falsify-8's class, one port upstream — raw `+` accumulators in
src/torihiki/venue*.cljk, "one more (fnil + 0), one more fork". Four sites:

1. src/torihiki/venue_fake.cljk:56 — `(update-in [:positions symbol]
   (fnil + 0) (signed side n))` -> `(update-in [:positions symbol] fx/acc
   (signed side n))`.
2. src/torihiki/venue_router.cljk:61 — `position`'s fold `(+ acc (if (= :buy
   side) qty (- qty)))` -> `(fx/acc acc …)`, AND fold in cid order
   (`(sort (keys ledger))`) — the determinism contract's sorted fold; a
   saturating fold must have one order to have one answer on both runtimes
   (mixed signs + clamp make the fold order observable).
3. src/torihiki/venue_router.cljk `risk-refusal` — `after (+ pos signed)`
   -> `(fx/sadd pos signed)` (this is the site whose verdict CHANGES: at the
   edge the door now judges against the caller's bound, identically on both
   runtimes — pinned by the-position-limit-door-sees-the-saturated-position).
4. src/torihiki/venue_router.cljk `reconcile!` — the per-market record sum
   `(+ (:lots old) lots)` (introduced by PR #103 itself) -> `(fx/acc (:lots
   old) lots)` inside the existing nil-stated guard.

Requires: add `[torihiki.fixed :as fx]` to the ns forms of venue_fake.cljk,
venue_router.cljk (venue_test.cljk already carries it). fx/acc = fx/sadd =
saturating (fnil + 0), total, never throws, identical verdict on both
runtimes (torihiki.fixed, torihiki-core git dep — out of this repo's write
scope; nothing in the dep changes).

Tests (written, BROKEN-measured on kbb/SCI — see counters above), in
test/torihiki/venue_test.cljk after `the-fake-conforms-to-the-contract`,
VALUE-clamp pins in the overflow_gates_test.cljk pattern (fx/i53-max /
fx/i53-min at the edges, seeded the falsify way at (- fx/i53-max 1) / (inc
fx/i53-min), crossing values 2^53+1 and 2^53+3 — odd, no double holds them,
so the BROKEN values already fork across runtimes (JVM exact vs nbb rounded);
the kbb BROKEN values recorded this tick are the rounded side).

Next tick: apply the four edits above, run GREEN on all three legs
(kbb -M:test, script/jvm-test.sh, script/nbb-test.sh — same counters across
legs is the claim), optionally run the JVM BROKEN leg once for the divergence
record, then PR + merge. The suite should come back 654/5554 0F/0E on each
leg (650 baseline + 4 new deftests).

## Not run / not claimed

JVM BROKEN, nbb BROKEN, all three GREEN legs, PR create/merge: NOT RUN
(budget). No src change is in this commit — only the BROKEN-measured tests
and this record.
