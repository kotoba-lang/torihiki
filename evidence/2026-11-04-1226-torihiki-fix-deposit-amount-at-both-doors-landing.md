# 2026-11-04 12:2x torihiki-fix tick — :deposit :amount at both doors LANDED.
# This is the landing record: one BROKEN run + three GREEN legs, all counted
# this tick. "not run" != "passed" — only the runs below are claimed.
# maturity.md untouched (measurement bots' file).

Load gate: 12:17 uptime direct — 1.24 / 1.42 / 1.88, all windows < 20, gate
passed (torihiki_state.sh absent on this host — direct uptime per the
chronic-fault precedent).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-1226, branch
bot/torihiki-fix-20261104-1226 off origin/main f11be58 (PR #97 tip; the
worktree landed under fleet/bot/ because the branch name carries a slash —
`git worktree move`d to the flat path, same as the 2229 tick's lesson).

## What lands

store-tx-shape's :deposit clause (src/torihiki/api.cljk) judges account and
credit but not :amount — validate* judges :amount (1..i53-max, :bad-amount)
and the engine door did not. This is integral_double_test.cljk's "22:2x
finding 2 residual", carried forward twice (the 0028 queue check scoped it as
the small alternate package). Fix = the one int-in? :amount judgment mirrored
into the :deposit clause — same keyword, same order (after account, before
credit) — plus the :deposit amount pin rows at BOTH doors and the two
residual comments retired.

This is a contract alignment, and the record says so plainly: the ENGINE
BEHAVIOUR does not change. cl/deposit (pinned torihiki-core c59d8fd) is
already total — a non-positive, fractional or out-of-domain amount is
refused (returned unchanged) there, so a garbage :deposit lands nothing
today. What was broken is the door's ANSWER: `store-tx-shape` is the shape
rule's engine face ("one shape rule, two doors") and it answered nil where
`validate` answers :bad-amount. The VALUE rows pin the unchanged landing
behaviour so the door fix cannot move it.

## BROKEN run (the new rows present, api.cljk un-fixed), measured this tick:

- kbb -M:test: 644 tests / 5460 assertions / 0 failures / 3 FAILs,
  KBB_EXIT=1 (/tmp/tfix-broken-kbb.out). All 3 reds are the new shape-door
  rows in out-of-domain-and-fractional-values-are-refused-at-both-doors,
  each `actual: (not (= :bad-amount nil))` — "deposit amount out-of-domain",
  "deposit amount fractional", "deposit amount non-positive". The validate
  half of every row passed (validate* already judged :amount); the 3 new
  VALUE rows passed too (cl/deposit's total gate) — the red is the door
  contract, exactly the scoped defect.

## GREEN, three legs, counted this tick (tree = fix commit):

- kbb -M:test: 644 tests / 5460 assertions / 0 failures / 0 errors,
  KBB_EXIT=0 (/tmp/tfix-green-kbb.out). NAMED GATE: green with the new
  tests included.
- script/jvm-test.sh (renamed copy + dep shims): 644 tests / 5460
  assertions / 0 failures / 1 error, JVM_EXIT=1 (/tmp/tfix-green-jvm.out).
  Identical counters to kbb. The 1 error is the pre-existing venue-mt arity
  bug (below) — present before this package, in files this branch does not
  touch.
- script/nbb-test.sh (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang):
  609 tests / 5274 assertions / 0 failures / 0 errors, namespaces 36/36,
  TESTS-ON-NBB: pass, NBB_EXIT=0 (/tmp/tfix-green-nbb.out). +15 assertions
  over the wire-number-canon landing baseline (5259 -> 5274) = the new rows
  ran on this leg too.

Counters arithmetic, for the record: 5445 (landing baseline) + 15 new
assertions = 5460 on kbb/JVM; 5259 + 15 = 5274 on nbb (609 tests, unchanged
— the rows extend existing deftests). BROKEN was measured on the kbb leg
only; the JVM/nbb BROKEN runs were not run.

## Findings carried forward (measured earlier, NOT fixed here — one tick, one
## fix package):

1. venue-mt JVM red (from PR #92, 053c2e4): torihiki.venue-mt/venue's
   :orders handler is 0-arity while venue_mt_test.cljk:87 calls every
   read-only handler with one arg — JVM ArityException in
   no-order_can_be_placed (the JVM leg's single error). kbb/SCI ignores the
   extra arg. Still present at f11be58 (reconfirmed this tick: the error
   fires with this package's api.cljk change in place and the files are
   untouched by this branch).
2. nbb runner registration gap (from PRs #91/#92): script/tests-on-nbb.cljk
   lists 36 namespaces (floor 28) while the test tree has 38 — venue-test
   and venue-mt-test are never registered, so the nbb leg passes its floor
   while running the suite MINUS the venue namespaces. Reconfirmed this
   tick (36/36 pass over 609 tests vs 644 on kbb/JVM).

## Environment notes (this host)

- kbb's dep resolution shells out to a JVM: without JAVA_HOME the leg dies
  at "Unable to locate a Java Runtime" before running anything. JAVA_HOME=
  /opt/homebrew/opt/openjdk@27/libexec/openjdk.jdk/Contents/Home (openjdk
  27.0.1) makes it go; /usr/bin/java is the failing stub.
- script/nbb-leg.sh calls kbb — kbb must be on PATH
  (/Users/junkawasaki/github/kotoba-lang/kotoba/bin) for the nbb leg too.
- gh auth still dead ("The token in default is invalid") — PR create/merge
  go through the REST API over the osxkeychain git credential without
  printing it (/tmp/tfix-pr.sh).
- Terminal default cwd points at a removed worktree (exit 126 on every
  bare call) — explicit workdir on every terminal call.
