# 2026-10-28 14:1x torihiki-fix tick — NOT LANDED (run budget exhausted before
# the implementation/GREEN runs). Tests written AND run BROKEN (pin-level red
# recorded); no src change landed, no PR. maturity.md untouched.
# "not run" != "passed": nothing is claimed green here.

Load gate: torihiki_state.sh 14:17 — 8.84 / 8.94 / 8.40, all windows < 20,
gate passed.

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261028-1224, branch
bot/torihiki-fix-20261028-1224 off origin/main 1cdb4a2 (PR #77 merge). KEPT as
handoff. State inside: test/torihiki/apply_totality_test.cljk (the 12:24 tick's
file, corrected this tick) + src/torihiki/api.cljk with FOUR TEMPORARY TDD
STUBS (order-tx-shape/amend-tx-shape/cancel-tx-shape/trigger-tx-shape, each
`[_ _] nil` = pre-fix "door absent" semantics; clearly labelled in-file,
MUST be replaced by the real shape gates before landing).

## What ran (kbb -M:test, the SCI leg — the only leg run this tick)

- BROKEN #1 (tests as written, unmodified engine): ns load fails,
  `Unable to resolve symbol: api/order-tx-shape` at the first missing var
  (apply_totality_test.cljk:104), exit 1, no counters. (/tmp/broken1.out,err)
- BROKEN #2–#4 (stubs in place, engine otherwise unmodified): the full file
  RUNS and fails at pin level. Final red run BROKEN #4:
  **525 tests / 3709 assertions / 43 failures / 0 errors, exit 1** — all 43
  failures in torihiki.apply-totality-test; the 516-test baseline stayed green
  (raw: evidence/2026-10-28-1417-torihiki-fix-broken4.{out,err}).

## Test-file corrections this tick (found BY the broken run)

The 12:24 draft shared one mutable-slab fixture across doseq cases —
`bk/cancel!`/`bk/reduce-to!`/`bk/place!` mutate the book every `ex` in scope
shares, and a case that threw mid-apply had already cancelled half an amend.
Observed contamination (BROKEN #2): "nil account" cancel showed level-qty 0
(case 1's fractional-id cancel had emptied the book). Fixed: fixture rebuilt
PER CASE in the amend/cancel/fire-time tests (case tables now take
`(fn [oid] …)` overrides); fire-time pin strengthened with a
`bk/resting-count` assertion (a garbage order must not rest either).

## BROKEN #4 pin-level failure modes (kbb/SCI), exactly as observed

- order gate (14 red): out-of-domain qty / off-ladder level / negative level
  → apply-tx THROWS (falsify-6/7 class); string qty → RESTED 5 (silent
  coercion), fractional qty → RESTED 2 (truncation), string level / side 5 /
  nil account / string account / fractional flags → RESTED (qty 5 or 1).
  "string flags" case green-both on SCI (bit-and coerces to the reduce-only
  path, which clamps to 0) — JVM leg expected different modes, NOT run.
- notional boundary (2 red): engine door's reason nil instead of :bad-amount;
  qty inc(quot i53-max 63) at level 63 RESTED 142971416741921.
- amend (3 red): out-of-domain new size and off-ladder new level → THROWS
  AFTER the cancel half executed (order lost mid-amend — the half-execution
  the ns docstring names); fractional id → order SHRUNK 10→5 ((long 2.5) is
  2). "string id" amend green-both on SCI (NaN coercion → order-of nil →
  no-op) — JVM expected to throw, NOT run.
- cancel (2 red): fractional id cancelled the live order (10→0). "string id" /
  "nil account" / "string account" green-both on SCI ((long nil) is 0,
  (long "one") is NaN — both owner-mismatch no-ops); the JVM ((long nil)
  throws) was NOT run.
- trigger submission (5 red): every garbage variant was STORED (string qty,
  out-of-domain embedded qty, off-ladder embedded level, string trigger
  price, string account all armed).
- fire-time (1 red): off-ladder embedded level → THROWS inside apply-block.
  "string qty" and "non-map order" green-both on SCI (reducing-qty coerces /
  clamps to a de-facto no-op) — they guard the gate ORDER post-fix.
- twap mirror (1 red): out-of-domain-slice schedule validated nil instead of
  :bad-amount. scale mirror (2 red): flags 99 and over-notional rung validated
  nil. shape-gates REASON pins (13 red): every new public fn answered nil.

## Planned but NOT done (next tick starts here)

1. Replace the stubs (api.cljk): public order-tx-shape / amend-tx-shape /
   cancel-tx-shape / trigger-tx-shape over the existing private pieces
   (account-ok?, validate-order-shape — which already carries the falsify-7
   notional cap); n-levels default 0 (api.cljk `(dec nil)` throw on a bookless
   market); validate :scale mirror doors (:bad-flags int-in? 0 7 + per-in-
   bounds-rung :bad-amount via `(> qty (quot fx/i53-max l))`) and :twap mirror
   door (:bad-amount at slice level: n-levels-1 bids / 0 asks) at the END of
   each cond. validate :order/:amend/:trigger reason mapping is already
   exactly what the shape fns answer — refactor optional, precedence preserved.
2. state.cljk engine gates: no-op gate at the TOP of apply-tx
   :order/:amend/:cancel/:trigger (before trg/valid? and before any
   destructuring arithmetic) + fire-time gate in fire-triggers on the RAW
   embedded order before cl/reducing-qty (`(and (map? o) (nil? (api/order-tx-shape
   e (assoc o :account (:account t) :market market))))` — the assoc makes the
   embedded order an :order for the shared gate; triggers carry the account).
   Refusals are no-ops with reasons from api/reasons; never a throw.
   Mirror invariant audited this tick (read): internal :order dispatchers are
   fire-twaps:885 (level n-levels-1/0, flag-ioc), :scale rungs:1119 (in-bounds
   rungs, flags (or flags 0)), :amend re-dispatch:1374 (flags 0) — all pass
   the gate unchanged IF validate gained the mirror doors (twap total-qty cap
   quot(i53-max, slice-level) covers every slice since slice ≤ total).
3. GREEN `kbb -M:test` (expected 525 tests / 3709 assertions 0F/0E), then
   script/jvm-test.sh and KOTOBA_CHECKOUTS=<checkouts> script/nbb-test.sh —
   never claim both runtimes from one; the JVM leg runs on the renamed copy.
4. script/tests-on-nbb.cljk: add torihiki.apply-totality-test to require +
   run-tests; expected-namespaces 26 -> 27 with provenance.
5. Land: commit, push, PR, merge --delete-branch, worktree remove.

Known scope residual (documented, not fixed here): apply-tx :twap/:scale take
raw garbage via direct apply-tx ((range count*) on a string throws) — user
path is validate-gated; pre-fix :twaps with string :remaining throws in
fire-twaps arithmetic. Same class, same residual as the 12:24 note.

JVM leg BROKEN run: NOT run this tick (budget). The JVM-specific red modes
((long nil) throw, (long "oid") throw, (min "20" 20) ClassCastException) are
EXPECTED but UNMEASURED — do not cite them as measured.

Host load at tick end: not re-measured. maturity.md untouched (measurement
bots' file).
