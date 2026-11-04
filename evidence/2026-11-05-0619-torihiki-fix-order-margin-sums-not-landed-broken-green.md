# 2026-11-05 06:1x torihiki-fix tick — NOT LANDED (budget exhausted mid GREEN
# debugging). Implementation + tests WRITTEN and measured BROKEN-first, then a
# GREEN run exposed two implementation bugs that are diagnosed below but NOT
# yet fixed. "not run" != "passed": nothing here is claimed green except the
# baseline. maturity.md untouched (measurement bots' file).

Load gate: uptime direct 06:17 — 2.00 / 1.63 / 1.78, all windows < 20, gate
passed. torihiki_state.sh absent on this host (the script file itself is
missing, not a cwd fault); direct uptime per the chronic-fault precedent.
gh auth WORKS (com-junkawasaki). kbb needs
PATH=/opt/homebrew/opt/openjdk/bin:…/kotoba-lang/kotoba/bin (keg-only java —
the 142x record's note, re-measured here).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-0619, branch
bot/torihiki-fix-20261105-0619 off origin/main 8f4a81d (PR #101). THIS
COMMIT IS THE HANDOFF: the uncommitted src/test changes stay in the worktree
for the next tick (same pattern as the 20261105-0439 handoff). Queue verified
against the 0439 record: maturity.md NEXT is stale; the sole open item is
issue #50 (order-margin running sums), carried forward from the 02:3x design
plus the 04:3x confirmations.

## Measured this tick (exact counts)

- Baseline GREEN kbb/SCI (src unmodified): **645 tests / 5510 assertions /
  0 failures / 0 errors**, exit 0 (/tmp/om-baseline.*). (PR #100 recorded
  644/5460; PR #101's venue :place test accounts for +1/+50.)
- BROKEN run (new tests, unfixed src), kbb/SCI: **649 tests / 5526 assertions
  / 11 fail / 0 error**, exit 1 (/tmp/om-broken.*, kept in /tmp only — rerun
  is cheap). Every new pin is RED before the fix: sums nil, shape pin missing
  :sums. NOTE: kbb/SCI silently accepts extra args on a 6-arity defn, so the
  BROKEN mode on the SCI leg is assertion failure, not ArityException (the
  JVM leg was NOT run this tick).
- GREEN run (implementation applied), kbb/SCI: **649 tests / 5526 assertions
  / 487 fail / 0 error**, exit 1 (/tmp/om-green-kbb.*) — the property test
  `the-cache-equals-a-rebuild-after-anything` is red = the cache≠rebuild
  invariant breaks. NOT LANDED.

## What is written (uncommitted, in the worktree)

- src/torihiki/order_margin.cljk: `:sums {:mark m :bids {:q :lq :loss :sat?}
  :asks {…}}` per entry; `fold-one`/`side-view`/`entry-sums` (all folds through
  fx/acc + fx/pmul — total); `adjust`/`take-consumed` gain a `mark` param
  (delta-fold O(1), rekey on mark mismatch, sat?-on-negative recompute);
  `rebuild` gains a marks 3rd arity; `rekey` for mark moves; `market-requirement`
  reads the sums at their own mark (:lq in the no-mark branch, never :loss) and
  falls back to the pure fold otherwise.
- src/torihiki/state.cljk: reprice! rekeys the affected market's entries when
  the mark VALUE moves (this is what keeps strict in-sync? true — the 02:3x
  design's lazy rekey alone is not enough for the property test); all adjust /
  take-consumed / rebuild call sites thread the mark.
- src/torihiki/snapshot.cljk, src/torihiki/api.cljk: rebuild/adjust call sites.
- test/torihiki/order_margin_test.cljk: in-sync? compares rebuild at current
  marks (sums included); the entry-shape pin updated to pin :sums by VALUE;
  4 new deftests per the 02:3x test plan (requirement VALUE pins 50 at mark
  100 / 181 at mark 80 through sums AND pure fallback; place/cancel/fill/
  amend/restore sequence; saturation clamp VALUE pin + sat?-on-negative
  recompute VALUE pin 9000000000000000; total-fold clamp VALUE pins).
- test/torihiki/liquidation_makers_test.cljk: 2 rebuild call sites gain marks.

## The two GREEN-run bugs (diagnosed, NOT fixed — next tick starts here)

1. **`:sat? nil`, not false.** fold-one's `:sat?` is `(or (:sat? s) … (and f
   (not= (* f qty) t)))`; when `loss-factor` returns nil (level at or through
   the mark) the last `or` term is nil and `or` returns nil. Observed in the
   failure dump as `:sums {:bids {:q 96 :lq 8644 :loss 0 :sat? nil}}`. This
   also silently disables the sat?-on-negative recompute (`(and (neg? qty)
   (:sat? s))` is falsy for nil) — which is why the property test is red.
   Fix: wrap the or-chain in `boolean`.
2. **Ghost entry `{:sums {:mark 100}}`.** adjust runs `prune` BEFORE the sums
   write; a negative delta that removes the last level makes prune drop the
   entry whole, and the nil-sums branch then re-creates it as `{:sums {:mark
   m}}` with no sides (observed in the dump: `1 {1 {:sums {:mark 100}}}`).
   Fix: after prune, if `(empty? (get-in oo [acct mkt]))` return `oo`.
3. ALSO CHECK next tick: the first `the-sums-answer-what-the-fold-answers`
   pin failed with the ENTIRE :sums missing (actual nil) on a pure-adds
   scenario — not yet explained by bugs 1–2; reproduce and read the actual
   form before patching (the failure detail is in /tmp/om-green-kbb.out).

## Not run / not claimed

script/jvm-test.sh and script/nbb-test.sh: NOT RUN. The kbb/SCI GREEN leg ran
and is RED. The 2,000-level benchmark (acceptance 3): NOT RUN. No PR. Branch
+ worktree kept for handoff; next tick: fix bugs 1–2 (and diagnose the sums-nil
pin), re-run kbb GREEN, then the JVM and nbb legs (never claim both runtimes
from one), the bench, then land via PR.
