# 2026-10-20 06:17-06:45 torihiki-fix tick — NOT LANDED (worktree directories vanish)

Load gate: pre-run 6:17 12.00/26.12/40.32 (not all >20 -> stop rule not triggered);
6:21 direct 16.15/21.02/34.64 declining, 1-min <20. Tick proceeded.

## Finding 1 — maturity.md NEXT is stale vs origin/main (all code items landed)
fetched origin/main = faab00a. PR #52 (3e7ec56 "i53 overflow fix package: saturating
accumulators, exact mul-rate, validate caps", falsify-6..13 class), PR #53 (7385332
"fuzz suite 常設化"), PR #54 (dcbdf61 "falsify-14 fix: saturate the merkle-sum
aggregate") are MERGED 2026-10-18/19. bench cancel! arity landed at all 3 sites
(bench.cljk:123 / probe.cljk:31 / curve.cljk:26, 3-arg with owner). Local checkout
1ceb357 is stale (detached, pre-#52). NEXT's remaining unlanded item is only the
reproducibility measurement (HEAD bench n>=1M 3x low-load stable, cancelled>0
-> 再現性 2->3).

## Finding 2 — residual `(fnil + 0)` accumulators (same class as falsify-8..13)
on origin/main src/torihiki/clearing.cljk: 215 [:bonds acct validator],
294-295 [:vaults vault :shares acct]/[:total-shares], 408 [:open-interest mkt],
541 [:committed acct asset], 564 [:balances buyer asset], 642 (rec :cur).
These post-date PR #52's 6-site sweep and violate fixed.cljk's stated invariant
("every multi-block accumulator in the engine goes through acc"). Candidate next
fix package: route through torihiki.fixed/acc with overflow_gates_test-style
reason/value pins. NOT modified this tick.

## Blocker — worktree dirs in ~/github/wt/fleet unreadable/vanishing from tool view
3/3 creations became invisible to `ls`/`stat`/`cd` within ~1-5 min of creation
(bot/torihiki-fix-20261020-0626, -0628, -0634-wd; the last was visible for 300 s
inside a single terminal call and gone ~40 s after it ended). A plain mkdir probe
in the same directory persisted; pre-existing 20261019-0819/1029/2027 stay
visible. Contradiction measured in one call at 06:47: `ls` reports the three
dirs missing while `git worktree list` (same call, same host main-2.local)
registers all three as valid worktrees at faab00a, and `git worktree prune`
finds nothing stale — i.e. an inconsistent filesystem view of the path
(sandbox/TCC or mount flakiness), NOT a confirmed deleting process (no hooks;
torihiki_state.sh is read-only; disk 97% used, 32Gi free; disk-resource bot is
proposal-only). No code changed, no tests run, nothing landed. Next tick:
either re-verify the dirs with git tools (they may still hold content), or work
in a worktree outside ~/github/wt/fleet and note the deviation in the PR.
