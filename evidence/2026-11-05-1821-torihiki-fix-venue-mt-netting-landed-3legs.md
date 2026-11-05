# 2026-11-05 18:2x torihiki-fix tick — venue-mt netting sum landed: the
# per-symbol position fold goes through fx/acc in sorted order, and a netting
# that cannot be held exactly is refused whole. "not run" != "passed": every
# green below was run and counted this tick.

Load gate: torihiki_state.sh absent on this host (chronic-fault precedent);
uptime direct 18:18 — 1.41 / 1.42 / 1.42, all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-1821, branch
bot/torihiki-fix-20261105-1821 off origin/main 3a521cd (PR #104 tip).
maturity.md untouched (measurement bots' file).

## Queue check (measured by code read at 3a521cd)

maturity.md NEXT verifies fully LANDED (same verdict as the 0028/224x/1427
queue checks, re-verified this tick): validate i53/notional caps, accum sum
gates via fx/acc, fmul/exact products, rate caps, bench 3-part fix
(bench.cljk:123 3-arity with ring-owner), fuzz in-suite, jvm/nbb legs
in-repo (script/*.sh), settle-deficit delta clamp. Open issues: #76 only
(terminal:, out of scope). So the queue head is a NEW probe target.

## Finding (measured this tick): the fifth raw accumulator — venue-mt's
## position net was the falsify-8 class PR #104's sweep missed

src/torihiki/venue_mt.cljk "pos-end" folded the per-symbol position net with
`(update acc symbol (fnil + 0) …)` — "one more (fnil + 0), one more fork",
the only raw numeric accumulator left in src/ (grep-verified). Three defects
ride on it:

1. The sum walks past 2^53 with no throw: the crossing net feeds
   `v/string-of`, whose i53 guard answers nil — the venue then replies
   {:ok [{:symbol … :volume nil}]} (a corrupt record in a contract answer).
2. Cross-runtime fork (falsify-8's class): with a raw fold the intermediate
   2^53 + 1 is held exactly on longs and rounded on doubles, so a later
   cancellation lands on different remainders (2 vs 1) — same frames, two
   answers, no throw.
3. Fold-order sensitivity: a position list is a set, but the raw fold's
   answer (and whether the intermediate escapes at all) depended on the
   order the terminal happened to send.

## The fix (one site, three rules)

src/torihiki/venue_mt.cljk "pos-end":
- each symbol's signed steps fold through `torihiki.fixed/acc` (fx/acc =
  sadd = saturating (fnil + 0), total, never throws — torihiki-core git dep,
  unchanged) in SORTED order (the determinism contract's sorted fold; mixed
  signs make fold order observable — the same argument as PR #104's router
  fix);
- a step where fx/acc clamps is a step whose exact sum left the i53 domain
  (the clamp branch is runtime-stable per torihiki.fixed/sadd) — this mirror
  refuses the list whole (:unrepresentable-position) rather than states a
  clamped net. Every accepted partial sum stays inside the domain, so the
  fold is exact on both runtimes;
- a final net the wire cannot state (`v/string-of` answers nil) is not a
  position — refused whole with the same reason.

Nothing a refusal refuses is ever recorded: :pending nil, the last complete
:list stands (the existing :bad path's shape; ns docstring bullet extended).
No throw is introduced anywhere on the apply path.

## Measured (exact counts)

- BROKEN (tests present, fix withheld), kbb/SCI `kbb -M:test`:
  **656 tests / 5565 assertions / 7 failures / 0 errors**, KBB_EXIT=1
  (/tmp/tori-1821-broken-kbb.{out,err}). All 7 reds are exactly the new
  pins (6 in a-net-the-fold-cannot-hold-is-refused-whole, 1 in
  the-netting-does-not-depend-on-the-order-the-terminal-sent); the BROKEN
  answers show the corruption live: {:ok [{:symbol "EURUSD" :volume nil}]}
  accepted with NO rejection at both edges and at the wire-state row
  ({:ok [{:symbol "XAUUSD" :volume nil}]}), and the order row answers
  "0.01" (doubles: the rounded remainder) where "0.02" is the net.
- GREEN kbb/SCI (`kbb -M:test`): **656 / 5565 / 0F / 0E**, KBB_EXIT=0
  (/tmp/tori-1821-green-kbb.{out,err}).
- GREEN JVM (script/jvm-test.sh): **656 / 5565 / 0F / 0E**, JVM_EXIT=0
  (/tmp/tori-1821-green-jvm.{out,err}).
- GREEN nbb (script/nbb-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/
  github/kotoba-lang): **656 / 5565 / 0F / 0E**, NBB_EXIT=0,
  TESTS-ON-NBB: pass (/tmp/tori-1821-green-nbb.{out,err}).
  All three legs carry identical counters — the cross-runtime claim is
  measured, not cited.

Baseline was 654 tests / 5554 assertions (PR #104 landing record); this
package adds 2 deftests / 11 assertions → 656/5565 on every leg.

## Not run / not claimed

JVM BROKEN leg and nbb BROKEN leg: NOT run (optional divergence record; the
BROKEN verdict is measured on kbb/SCI and the divergence is visible in its
answers). Bench: not run (not touched by this package).

## Test pins (overflow_gates pattern: REASON literal for refusals, VALUE for
## kept state)

test/torihiki/venue_mt_test.cljk, 2 deftests / 11 assertions:
- a-net-the-fold-cannot-hold-is-refused-whole — top edge (crossing value
  2^53 + 1), bottom edge, and the wire-state row (2 x 900719925474099 steps
  of a "0.10" step: in-domain sum, unstatable wire decimal) each pin the
  REASON :unrepresentable-position and the VALUE (last complete list stands,
  :pending nil); positive control last (the door still opens).
- the-netting-does-not-depend-on-the-order-the-terminal-sent — the same
  three positions in two arrival orders net to "0.02" both ways (the sorted
  fold; with the raw fold the first order forks: longs 2, doubles 1).
