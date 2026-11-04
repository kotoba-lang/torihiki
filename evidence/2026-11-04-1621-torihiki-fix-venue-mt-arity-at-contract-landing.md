# 2026-11-04 16:2x torihiki-fix tick — venue-mt JVM arity red LANDED (test
# calls the contract's shape). One BROKEN run + three GREEN legs, all counted
# this tick. "not run" != "passed" — only the runs below are claimed.
# maturity.md untouched (measurement bots' file).

Load gate: 16:17 uptime direct — 1.31 / 1.18 / 1.70, all windows < 20, gate
passed; re-measured 16:21 0.94 / 1.02 / 1.52 (torihiki_state.sh absent in
this profile's scripts dir — direct uptime per the chronic-fault precedent).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-1621, branch
bot/torihiki-fix-20261104-1621 off origin/main 3ce969a (PR #98 tip).

## What lands

Finding 1 from PR #98's landing record (from PR #92, 053c2e4): the JVM leg's
single error. venue_mt_test.cljk's no-order_can_be_placed knocked every
refusal handler with one argument — `((get venue f) {})` over
[:place :cancel :orders :history] — but torihiki.venue/contract documents
`:orders []` (zero args), and every other face agrees: the router calls
`((:orders venue))`, venue_fake answers `(fn [] ...)`, venue-mt answers
`(fn [] {:error :read-only})`. The one deviating call shape was the test's.

Direction is the contract, not the handler: three votes for 0-arity
(contract doc, router call site, fake venue) against one sloppy knock.
Fix = knock each refusal handler at exactly its documented arity —
:place [order], :cancel [id], :orders [], :history [since] — keeping the
{:error :read-only} VALUE pin. No src change; the ENGINE BEHAVIOUR does not
change.

The arity is now part of the pin: the JVM leg enforces it (a handler that
drifts from its contract arity ArityExceptions at this row), and the row's
comment names the cross-runtime asymmetry that hid the red — kbb/SCI
silently accepts a wrong-arity call while the JVM dies on it.

## BROKEN (before the fix), measured this tick:

- kbb -M:test: 644 tests / 5460 assertions / 0 failures / 0 errors,
  KBB_EXIT=0 (/tmp/tfix1621-broken-kbb.out) — green even un-fixed: SCI
  swallows the extra argument. This is the asymmetry itself.
- script/jvm-test.sh: 644 tests / 5460 assertions / 0 failures / **1
  error**, JVM_EXIT=1 (/tmp/tfix1621-broken-jvm.out:78-81):
  `ERROR in (no-order_can_be_placed) (AFn.java:429) ... actual:
  clojure.lang.ArityException: Wrong number of args (1) passed to:
  torihiki.venue-mt/venue/fn--20822` — the 0-arity :orders handler.
- nbb BROKEN: not run (venue-mt-test is not registered on that leg —
  finding 2 below; the row cannot fail there today).

## GREEN (tree = fix commit), three legs, counted this tick:

- kbb -M:test: 644 tests / 5460 assertions / 0 failures / 0 errors,
  KBB_EXIT=0 (/tmp/tfix1621-green-kbb.out).
- script/jvm-test.sh: 644 tests / 5460 assertions / 0 failures / 0 errors,
  JVM_EXIT=0 (/tmp/tfix1621-green-jvm.out). NAMED GATE: the JVM leg is
  green — 0 errors — with the fixed tests included. First exit 0 for this
  leg since PR #92 added venue-mt.
- script/nbb-test.sh (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang):
  609 tests / 5274 assertions / 0 failures / 0 errors, namespaces 36/36,
  TESTS-ON-NBB: pass, NBB_EXIT=0 (/tmp/tfix1621-green-nbb.out).

Counters unchanged on every leg: the row keeps its 4 assertions, the change
is the call shape. kbb/JVM stay equal at 644/5460 — the cross-runtime
counter claim holds; nbb stays at 609/5274 for the registration reason below.

## Finding carried forward (measured earlier, NOT fixed here — one tick, one
## fix package):

- nbb runner registration gap (from PRs #91/#92, reconfirmed in #98's
  record): script/tests-on-nbb.cljk lists 36 namespaces (floor 28) while
  the test tree has 38 — venue-test and venue-mt-test never run on the nbb
  leg, which therefore passes its floor over the suite MINUS the venue
  namespaces (609/5274 vs 644/5460). Reconfirmed this tick (36/36). This
  is why the fixed row is not exercised on the nbb leg.

## Environment notes (this host)

- JAVA_HOME=/opt/homebrew/opt/openjdk@27/libexec/openjdk.jdk/Contents/Home
  (openjdk 27) required for kbb's dep resolution; kbb on PATH via
  /Users/junkawasaki/github/kotoba-lang/kotoba/bin.
- gh auth is working again this tick (PR via `gh pr create`/`gh pr merge`).
- Terminal default cwd points at a removed worktree (exit 126 on every bare
  call) — explicit workdir on every terminal call.
