# 2026-11-05 00:4x torihiki-fix tick — venue :place totality LANDED (the fake
# refuses whole what it cannot execute). One BROKEN run per runtime + three
# GREEN legs, all counted this tick. "not run" != "passed" — only the runs
# below are claimed. maturity.md untouched (measurement bots' file).

Load gate: 00:17 uptime direct — 1.64 / 1.43 / 1.40, all windows < 20, gate
passed (torihiki_state.sh absent in this profile's scripts dir — direct
uptime per the chronic-fault precedent). Terminal default cwd points at a
removed worktree (exit 126 on every bare call) — explicit workdir on every
call (known).

Worktree ~/github/wt/fleet/bot/torihiki-fix-20261105-0018, branch
bot/torihiki-fix-20261105-0018 off origin/main 94ba793 (PR #100 tip).
Continues the 22:4x handoff (branch bot/torihiki-fix-20261104-2240 kept its
scoping record; that record is cherry-picked into this branch so the
package lands with its scope). The scoping doc
(evidence/2026-11-04-224x-torihiki-fix-venue-place-totality-scoped-not-landed.md)
holds the queue check and the probe; this record only adds the runs.

## What lands

`torihiki.venue-fake`'s `:place` door, exactly the designed fix: judge the
request BEFORE the fault machinery (a request never sent triggers no
terminal fault), refuse whole at the contract's shape —

1. side not in #{:buy :sell} (over the set, missing, or not a keyword)
   -> {:error :bad-side}
2. unknown :symbol -> {:error :unknown-symbol} (kept from the old code,
   moved into the door)
3. n = (v/count-of volume (step-of sym) :exact); n not a positive integer
   (off-grid / nil / non-string / exponent / negative / zero / past-i53)
   -> {:error :bad-volume}
4. :price present but (parse-decimal price) not positive (non-decimal /
   zero / negative) -> {:error :bad-price}
5. else the existing fault case + execute! unchanged.

count-of/parse-decimal are total (nil-safe) so the door itself cannot
throw; the nil-n position sum that threw on the JVM is now unreachable.
Kinds mirror ->venue-order's vocabulary (:bad-side/:bad-price) and the
:bad-<field> pattern. ns docstring names the door. No engine/root code
touched — the fake is the test venue.

## BROKEN runs (tree = new deftest, venue_fake unmodified), both measured
## this tick:

- kbb -M:test: 645 tests / 5510 assertions, **47 failures** / 0 errors,
  KBB_BROKEN_EXIT=1 (evidence/2026-11-05-004x-torihiki-fix-venue-place-kbb-broken.out).
  Every failure is the new deftest: each garbage request answered
  {:ok ... :status :filled}, orders recorded ("a full phantom book"),
  positions coerced — the scoping probe's failure mode, now pinned red.
- script/jvm-test.sh (renamed copy + dep shims): 645 / 5510, 42 failures +
  **5 errors**, JVM_BROKEN_EXIT=1 (...-jvm-broken.out). The JVM mode is the
  asymmetry the scoping doc predicted, now MEASURED: the nil-n rows
  (off-grid "0.015", nil volume, non-string 0.3, "1e2", past-i53) throw
  `java.lang.NullPointerException: Cannot invoke "Object.getClass()"` out
  of clojure.core/+ (Numbers.ops) in the position sum — mid-place, swap!
  aborted — where kbb/SCI coerced and filled. One door, two failure modes,
  both recorded.

## GREEN (tree = fix commit), three legs, counted this tick — same counters
## on all three (644/5460 baseline + the one new deftest):

- kbb -M:test: 645 tests / 5510 assertions / 0 failures / 0 errors,
  KBB_GREEN_EXIT=0 (...-kbb-green.out).
- script/jvm-test.sh: 645 / 5510 / 0 failures / 0 errors,
  JVM_GREEN_EXIT=0 (...-jvm-green.out).
- script/nbb-test.sh (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang):
  645 / 5510 / 0 failures / 0 errors, TESTS-ON-NBB: pass, NBB_EXIT=0
  (...-nbb-green.out).

## Test pins (test/torihiki/venue_test.cljk)

One new deftest `a-request-the-fake-cannot-execute-is-refused-whole`, per
the scoping doc: the REASON literal {:error reason} for 14 rows (3 side
rows, 1 unknown-symbol row, 7 volume rows, 3 price rows), and after EACH
refusal the VALUE pins orders [] / positions {} — nothing a refusal refuses
ever lands. Positive control last: the representable request fills
{:ok {:venue-order-id "V1" :status :filled}} and moves positions
{"EURUSD" 30} — the V1 id pins that no refused request consumed a seq
number. Then the ordering pin: with a :unreachable fault queued, a garbage
request is refused by the door ({:error :bad-side}, not :unreachable) and
the queued fault still fires for the request actually sent. The 50 new
assertions failed BROKEN on both runtimes (47F kbb / 42F+5E JVM) before
the fix and pass GREEN on all three legs after.

## Findings carried forward

None. The queue check in the scoping doc (maturity.md NEXT fully LANDED)
stands; PR #100 closed the last carried finding and this package closes
the 22:4x probe finding.

## Environment notes (this host)

- JAVA_HOME=/opt/homebrew/opt/openjdk@27/libexec/openjdk.jdk/Contents/Home
  (openjdk 27) required for kbb's dep resolution; kbb on PATH via
  /Users/junkawasaki/github/kotoba-lang/kotoba/bin.
- Terminal default cwd points at a removed worktree (exit 126 on every bare
  call) — explicit workdir on every terminal call.
