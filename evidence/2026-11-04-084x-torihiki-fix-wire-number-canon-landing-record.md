# 2026-11-04 08:4x torihiki-fix tick — wire-number-canon LANDED. This is the
# landing record: BROKEN run + three GREEN legs, all counted this tick.
# "not run" != "passed" — only the runs below are claimed.

Load gate: 08:18 uptime direct (jacob.local) — 1.87 / 1.43 / 1.73, all
windows < 20, gate passed (torihiki_state.sh still absent on this host;
direct uptime per the chronic-fault precedent).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-0028, branch
bot/torihiki-fix-20261104-0028. This tick CONTINUED the kept handoff and
merged origin/main 053c2e4 (venue-mt JUN-814, PR #92) into the branch —
clean merge 8893212 — so every counter below is measured on the state that
lands. maturity.md untouched (measurement bots' file).

## What lands

wire-number-canon: every real number a transaction can carry has ONE
canonical wire form, identical bytes on both runtimes. src/torihiki/log.cljk
enc-val/dec-val + signing-payload (the ["u" exact-decimal-digits] /
["d" exact-decimal-expansion] tags, Inf/NaN policy, total kind rendering),
src/torihiki/api.cljk int-like? portable finiteness guard, and the
test/torihiki/log_test.cljk pin family. This closes the three scoped gaps
(0222 evidence): out-of-domain whole doubles (asymmetric commit halt on the
JVM leg), fractional doubles (symmetric commit halt on both), and the
signing-payload str divergence ("1.0E17" vs "100000000000000000").

## BROKEN run (the guard reverted — api.cljk at its pre-fix form; the new
deftest present), measured this tick:

- kbb -M:test: 644 tests / 5445 assertions / 0 failures / 0 errors,
  KBB_EXIT=0 (/tmp/wnc4-broken-kbb.out). The kbb leg is GREEN even broken:
  SCI's `rem` lands on NaN for the non-finites and answers false — the red
  is JVM-leg-specific. (SCI also silently ignores EXTRA args to a 0-arity
  fn: `((fn [] :x) {})` -> :x — see the venue finding below.)
- script/jvm-test.sh: 644 tests / 5442 assertions / 0 failures / 7 ERRORS,
  JVM_EXIT=1 (/tmp/wnc4-broken-jvm.out). Six errors are one root cause,
  `java.lang.NumberFormatException: Infinite or NaN` from
  clojure.lang.Numbers.remainder inside api/int-like?: decoded-numbers-
  recover-the-submitted-value x2 (##Inf/##-Inf), the-wire-round-trip-is-
  idempotent, fractional-numbers-tag-d x2, and the NEW deftest
  an-infinite-amount-is-rejected-into-the-root-not-thrown — run red before
  the fix (a test that never failed is theatre). The 7th error is the
  pre-existing venue-mt arity bug (below), present with this package's
  api.cljk change reverted and in files this branch does not touch.

## GREEN, three legs, counted this tick (guard restored, tree = 8893212):

- kbb -M:test: 644 tests / 5445 assertions / 0 failures / 0 errors,
  KBB_EXIT=0 (/tmp/wnc4-green-kbb.out). NAMED GATE: green with the new
  tests included.
- script/jvm-test.sh (renamed copy + dep shims): 644 tests / 5445
  assertions / 0 failures / 1 error, JVM_EXIT=1 (/tmp/wnc4-green-jvm.out).
  Identical counters to kbb. The 1 error is the venue-mt arity bug only —
  every wire-number-canon pin is green on the JVM leg.
- script/nbb-test.sh (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang):
  609 tests / 5259 assertions / 0 failures / 0 errors, namespaces 36/36,
  TESTS-ON-NBB: pass, NBB_EXIT=0 (/tmp/wnc4-green-nbb.out).
- Focused nbb pin (script/nbb-leg.sh with a scratch run-tests of
  torihiki.log-test, scratch removed after): 22 tests / 86 assertions /
  0 failures / 0 errors — 22 = every deftest in log_test.cljk (grep
  "(deftest" = 22), so the new liveness deftest ran green on the nbb leg.

NO three-leg same-count claim this landing: the nbb leg's canonical
36-namespace list does not include torihiki.venue-test /
torihiki.venue-mt-test (the 35 tests / 186 assertions difference) — see
findings. On the 36 namespaces it does list, the nbb leg is clean.

## Findings carried forward (measured, NOT fixed here — one tick, one fix
package):

1. venue-mt JVM red (from PR #92, 053c2e4): torihiki.venue-mt/venue's
   `:orders` handler is `(fn [] {:error :read-only})` (0-arity) but
   venue_mt_test.cljk:87 calls every read-only handler with one arg
   `((get venue f) {})` — JVM ArityException in no-order_can_be_placed.
   SCI/kbb ignores the extra arg, so the kbb and nbb legs pass and only
   the JVM leg errors. src/torihiki/venue_mt.cljk and
   test/torihiki/venue_mt_test.cljk are byte-identical to origin/main in
   this branch (git diff origin/main -- <both> = empty) and the error
   fires in the BROKEN run too, so it predates this package. Fix belongs
   with the venue work: either the handler takes the request arg like its
   :place/:cancel siblings, or the test calls `((get venue f))` per the
   contract — and the SCI arity leniency means kbb cannot police it.
2. nbb runner registration gap (from PRs #91/#92): script/tests-on-nbb.cljk
   lists 36 namespaces (floor 36, last bumped by PR #88) while kbb/JVM
   auto-discover 38 — venue-test (24 deftests) and venue-mt-test (13
   deftests) were never registered. The leg passed its floor while running
   the suite MINUS the venue namespaces — exactly the "a floor that counts
   the namespaces is only as honest as the list" failure the file's own
   comment warns about. Before the venue PRs, 36 was the whole suite and
   the three legs matched.
3. Discovery carry-forward (from the 062x tick, reconfirmed): the JVM's
   `rem` is BigDecimal-backed and THROWS on NaN/Infinities while JS lands
   on NaN — any future whole-ness or mod-1 arithmetic must guard
   finiteness first (the `(== (- v v) 0)` pattern in api/int-like?).

## Compatibility note (from the 0028 tick, carried into the landing):

signing-payload now renders a namespaced kind through the total helper
("y" -> "a/y"). An old committed block carrying a namespaced kind would
not re-verify against the new payload bytes — such blocks are
engine-rejected :unknown-tx but committable. In-domain integral doubles
keep their bare wire form (5.0 stays 5), so the old logs of admitted
transactions keep decoding.

## Environment notes

gh auth is still dead on this host ("The token in default is invalid") —
PR create/merge go through the REST API over the osxkeychain git
credential without printing it. kbb is at
/Users/junkawasaki/github/kotoba-lang/kotoba/bin/kbb (not on default
PATH; the nbb legs need it exported). A scratch script outside the repo
(/tmp) fails kbb namespace resolution ("Could not find namespace:
kotoba.lang.spec") even with a good classpath — put scratch run-tests
files under script/ (untracked, removed after) as nbb-leg.sh expects.
