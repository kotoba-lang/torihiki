# 2026-11-04 20:2x torihiki-fix tick — nbb runner registration gap LANDED
# (venue namespaces join the deploy leg). One BROKEN run + three GREEN legs,
# all counted this tick. "not run" != "passed" — only the runs below are
# claimed. maturity.md untouched (measurement bots' file).

Load gate: 20:17 uptime direct — 2.33 / 1.62 / 1.40, all windows < 20, gate
passed (torihiki_state.sh absent in this profile's scripts dir — direct
uptime per the chronic-fault precedent).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-2019, branch
bot/torihiki-fix-20261104-2019 off origin/main f806b96 (PR #99 tip).

## What lands

The single finding carried forward by PR #99's landing record (from PRs
#91/#92, reconfirmed in #98 and #99): script/tests-on-nbb.cljk listed 36
namespaces (require list and run-tests list) while the test tree carries 38 —
`torihiki.venue-test` and `torihiki.venue-mt-test` were in NEITHER list, so
the ClojureScript leg — the runtime that DEPLOYS (torihiki-node compiles this
engine to a Worker bundle) — passed its floor over the suite MINUS the venue
namespaces and blessed it as "the suite". Measured at f806b96 before this
change: 609 tests / 5274 assertions over 36/36 against 644 / 5460 on
kbb/JVM. That is the failure this runner's own header names: a check that
could not run returning the value of a check that ran and found nothing.

Fix = register both namespaces in the require list and the run-tests list
(alphabetical, `validators-test` < `venue-mt-test` < `venue-test`) and raise
the floor 36 -> 38 with the provenance comment in the established style. The
runner's own words hold: "A floor that counts the namespaces actually listed
is only as honest as the list" — the list is now every
test/torihiki/*_test.cljk (38 files carry deftests, 38 namespaces run). No
src change; the ENGINE BEHAVIOUR does not change. The two venue namespaces
passed on nbb/SCI first touch (see GREEN) — no test change needed.

## BROKEN run (floor 38 in place, registrations absent), measured this tick:

- script/nbb-test.sh: 609 tests / 5274 assertions / 0 failures / 0 errors,
  namespaces 36/38, NBB_BROKEN_EXIT=**2** (/tmp/tfix2017-nbb-broken.out):
  "REFUSING to report a pass: 36 of 38 namespaces ran and 609 tests
  executed. The classpath did not carry the suite; this run is unanswerable,
  not clean." — the new gate red against the unregistered runner, exactly
  the scoped defect.

## Baseline (pre-change tree), measured this tick:

- script/nbb-test.sh: 609 tests / 5274 assertions, 36/36,
  NBB_EXIT=0 — the gap is live at f806b96: the leg passed while running the
  suite minus the venue namespaces (/tmp/tfix2017-nbb-baseline.out).

## GREEN (tree = fix commit), three legs, counted this tick:

- kbb -M:test: 644 tests / 5460 assertions / 0 failures / 0 errors,
  KBB_EXIT=0 (/tmp/tfix2017-kbb-green.out).
- script/jvm-test.sh (renamed copy + dep shims): 644 tests / 5460
  assertions / 0 failures / 0 errors, JVM_EXIT=0 (/tmp/tfix2017-jvm-green.out).
- script/nbb-test.sh (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang):
  644 tests / 5460 assertions / 0 failures / 0 errors, namespaces 38/38,
  TESTS-ON-NBB: pass, NBB_EXIT=0 (/tmp/tfix2017-nbb-green.out).

Counters arithmetic, for the record: the venue namespaces contribute 35
tests / 186 assertions (609 -> 644 tests, 5274 -> 5460 assertions on the nbb
leg) and all three legs now print the SAME counters — the cross-runtime
counter claim the nbb-test.sh header demands ("that equality is the
cross-runtime claim") holds for the first time since PRs #91/#92 added the
venue namespaces.

## Findings carried forward

None from this package. This closes the last finding carried by PRs #98/#99
landing records.

## Environment notes (this host)

- JAVA_HOME=/opt/homebrew/opt/openjdk@27/libexec/openjdk.jdk/Contents/Home
  (openjdk 27) required for kbb's dep resolution; kbb on PATH via
  /Users/junkawasaki/github/kotoba-lang/kotoba/bin.
- Terminal default cwd points at a removed worktree (exit 126 on every bare
  call) — explicit workdir on every terminal call.
