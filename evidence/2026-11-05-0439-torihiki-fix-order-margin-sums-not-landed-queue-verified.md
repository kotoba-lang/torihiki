# 2026-11-05 04:3x torihiki-fix tick — NOT LANDED (run budget exhausted after
# queue re-verification + implementation-surface read). No src/test change, no
# PR. "not run" != "passed": nothing is claimed green here. maturity.md
# untouched (measurement bots' file).

Load gate: uptime direct 04:17 — 1.09 / 1.35 / 1.30, all windows < 20, gate
passed. torihiki_state.sh fails at shell startup (its cwd — a removed
bot-torihiki-fix worktree — is gone; exit 126), direct uptime per the
chronic-fault precedent. gh auth WORKS on this host (com-junkawasaki), unlike
the jacob.local note in the 142x record.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261105-0439, branch
bot/torihiki-fix-20261105-0439 off origin/main 8f4a81d (PR #101 merge). This
commit is the handoff. 11 stale worktrees from prior ticks remain in
~/github/wt/fleet (cleanup is a human call — none touched).

## Queue re-verification at origin/main 8f4a81d (measured, exact)

maturity.md NEXT is STALE — every fix item it lists is landed on main:

- validate-i53-halt fix package: LANDED (api.cljk validate-order-shape carries
  the i53 cap + `level x qty <= i53-max` notional cap; test/torihiki/
  overflow_gates_test.cljk present, 65 deftests).
- bench-tape-cancel-arity: LANDED (bench/torihiki/bench.cljk:123
  `(bk/cancel! b oid (aget ring-owner slot))` — 3-arity with owner).
- fuzz suite 常設化: LANDED (test/torihiki/fuzz_seeded_test.cljk).
- wire-number-canon: LANDED as PR #93 (a07691f landing record: BROKEN JVM 7E
  incl. the new deftest, GREEN kbb 644/5445, JVM 644/5445 +1 pre-existing
  venue-mt E, nbb 609/5259 36/36). The eight stale "not landed (budget)"
  scoping branches (20261103-0222/1621/1837/2031/2229, 20261104-1439) are
  superseded — including the validated digit-x5 "d" expansion route and the
  BigInt-`*`-corrupt-on-kbb isolation (135ab86), all already on main.
- venue :place totality: LANDED as PR #101 (d77610b) — supersedes the
  20261104-2240 scoping branch.

## The sole in-scope open item: issue #50 (order-margin running sums)

Scoped by tick 2026-11-05 02:3x on branch bot/torihiki-fix-20261105-0237
(26cf857). That design is carried forward UNCHANGED; this tick's reads add
four implementation confirmations against the current tree (8f4a81d):

1. Implementation surface read: src/torihiki/order_margin.cljk 272 lines.
   Call sites to thread `mark`/`marks` through (all hold `ex`):
   state.cljk:850-852 (take-consumed + adjust), :1602/:1620/:1652 (adjust),
   :1913 (take-consumed), :1495/:1993 (rebuild), snapshot.cljk:107 (rebuild),
   api.cljk:997 (adjust). `clear` sites (:812/:1575/:1903) drop the entry
   whole — :sums rides along for free. `in-sync?` lives at
   test/torihiki/order_margin_test.cljk:40.
2. The cache-equals-rebuild invariant holds by CONSTRUCTION with rebuild
   folding `adjust` from empty: per-order terms (pmul l qi) and per-level
   terms (pmul l Σqi) are distributively equal, both saturate the same real
   sum to the same i53 edge (sign-fixed terms), so the :sat?-on-negative-
   delta canonical recompute keeps the incremental side exactly equal to a
   fresh fold in every reachable case. (This closes the loose end in the
   02:3x record's "single definition" phrasing: rebuild may fold adjust per
   order; side-view per level agrees with it.)
3. `prune` must drop the per-side `:sums` child when it dissocs an empty
   side and the entry's `:sums` with the entry (today's `(empty? e)` test
   would never see an empty entry once :sums rides in it).
4. The no-mark branch (mark 0) must consume `:lq` per side, never `:loss`:
   at mark 0 the loss formula would return Σ l·q and change the answer.
   Sum entries built at :mark 0 are fine as-is.

Fixed helpers for every fold: fx/acc / fx/sadd / fx/pmul (torihiki.fixed,
torihiki-core git dep) — total, never throw, per the #41 lesson.

## Not run / not claimed

Implementation, BROKEN runs, GREEN runs on kbb -M:test / script/jvm-test.sh /
script/nbb-test.sh, the 2,000-level benchmark, and the same-count claims:
NOT RUN (budget). No PR. Branch kept for handoff; the next tick starts from
the 02:3x design plus the four confirmations above.
