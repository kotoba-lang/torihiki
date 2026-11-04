# 2026-11-04 22:4x torihiki-fix tick — NOT LANDED (run budget exhausted after
# the queue check + the venue-:place totality probe). No src/test change, no
# PR. "not run" != "passed": nothing is claimed green here. maturity.md
# untouched (measurement bots' file).

Load gate: 22:18 uptime direct — 1.88 / 1.58 / 1.57, all windows < 20, gate
passed (torihiki_state.sh absent in this profile's scripts dir — direct
uptime per the chronic-fault precedent). Terminal default cwd points at a
removed worktree (exit 126 on every bare call) — explicit workdir on every
call (known).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-2240, branch
bot/torihiki-fix-20261104-2240 off origin/main 94ba793 (PR #100 tip).
Branch + worktree KEPT for handoff — the next tick starts here.

## Queue check this tick (measured by code read at 94ba793)

maturity.md NEXT verifies fully LANDED (same conclusion as the 0028 queue
check, re-verified at 94ba793): validate i53/notional caps (api.cljk:311/318
and the store doors), accum sum gates via fx/acc/fx/sadd, fmul/exact
products, rate caps, bench 3-part fix (bench.cljk:123 ring-owner wiring),
fuzz in-suite (fuzz_seeded_test.cljk registered), jvm/nbb legs in-repo
(script/*.sh), settle-deficit delta clamp (torihiki-core c59d8fd). PR #100's
landing record closed the last carried finding ("Findings carried forward:
None"). So the queue head is a NEW probe target, not a scoped package.

## Finding (measured this tick): the venue :place door coerces on kbb/SCI and
## would throw on the JVM — one door, two failure modes

torihiki.venue-fake's :place (src/torihiki/venue_fake.cljk) executes any
request shape: `execute!` computes `n = (v/count-of volume step :exact)`
which is nil for a nil / non-string / off-grid / negative / zero / past-i53
volume or an unknown symbol's nil step, then feeds n into the position sum
`(update-in [:positions symbol] (fnil + 0) (signed side n))` and records the
order ANYWAY. The venue contract (torihiki.venue/contract ns docstring) says
every function answers {:ok v}, {:error kind} or {:timeout true}; a request
the venue cannot execute must be refused, never filled as a coercion.

PROBE (kbb --backend sci --classpath src, /tmp/probe-venue-place.cljk, this
tick, exit 0 — every garbage request "filled"):

  good market order => {:ok {:venue-order-id "V1", :status :filled}}
  garbage side :hold => {:ok {:venue-order-id "V2", :status :filled}}   ;; filled as a SELL
  missing side      => {:ok {:venue-order-id "V3", :status :filled}}   ;; filled as a SELL
  off-grid "0.015"  => {:ok {:venue-order-id "V4", :status :filled}}
  nil volume        => {:ok {:venue-order-id "V5", :status :filled}}
  non-string 0.3    => {:ok {:venue-order-id "V6", :status :filled}}
  exponent "1e2"    => {:ok {:venue-order-id "V7", :status :filled}}
  negative "-0.10"  => {:ok {:venue-order-id "V8", :status :filled}}
  zero "0.00"       => {:ok {:venue-order-id "V9", :status :filled}}
  past-i53          => {:ok {:venue-order-id "V10", :status :filled}}
  orders:   all ten comments recorded (a full phantom book)
  positions: {"EURUSD" -40}   ;; coerced arithmetic: +30 good fill, then garbage

Cross-runtime asymmetry (the falsify class): kbb/SCI silently coerces nil
through the position sum (SCI arithmetic treats the nil n as zero-ish and
the fake reports success), while the JVM's `(- nil)` / `(+ 0 nil)` throw out
of the swap! — the JVM mode is a mid-place THROW (swap! aborts, state
unchanged) and is NOT yet measured this tick (budget); the next tick's
BROKEN run must record it. Fix must refuse BEFORE any arithmetic so both
runtimes answer the same literal.

## Designed fix (not implemented — next tick)

src/torihiki/venue_fake.cljk :place only: judge the request before the fault
machinery (a request never sent triggers no terminal fault):
1. (not (#{:buy :sell} (:side req)))            -> {:error :bad-side}
2. unknown :symbol                              -> {:error :unknown-symbol} (kept)
3. n = (v/count-of (:volume req) (step-of sym) :exact); not (pos? n)
                                                  -> {:error :bad-volume}
4. :price present but (parse-decimal px) not positive
                                                  -> {:error :bad-price}
5. else the existing fault case + execute! unchanged.
Kinds mirror ->venue-order's vocabulary (:bad-side/:bad-price) and the
:bad-<field> pattern (venue_mt rejects). count-of/parse-decimal are total
(nil-safe) so the door itself cannot throw.

## Test pins (not written — next tick), test/torihiki/venue_test.cljk

New deftest a-request-the-fake-cannot-execute-is-refused-whole, pinning the
REASON literal {:error reason} per row (side over the set / missing /
fractional -> :bad-side; off-grid / nil / non-string / exponent / negative /
zero / past-i53 volume -> :bad-volume; non-decimal / non-positive price ->
:bad-price) and the VALUE pins after each refusal: orders [] and positions
{} (nothing a refusal refuses ever lands). Positive control last: the
representable request fills {:ok {:venue-order-id "V1" :status :filled}} and
moves positions {"EURUSD" 30} (the door still opens; the V1 id pins that no
refused request consumed a seq number). BROKEN-first: run the rows against
unmodified venue_fake on kbb (red = coerced :ok answers + corrupted state),
record the JVM BROKEN mode (expected: ArityException-class throw from the
nil sum), then land the fix and GREEN kbb -M:test / script/jvm-test.sh /
KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh
— never claim both runtimes from one.

## Not run / not claimed

BROKEN runs, all three GREEN legs, the JVM-mode measurement: NOT RUN this
tick (budget). No tests written to the tree, no src change, no PR. Branch
kept for handoff.
