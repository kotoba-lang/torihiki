# torihiki-fix landing: fdiv zero-divisor is total; impact-price affords level 0

Branch `bot/torihiki-fix-20261026-2218b` (worktree ~/github/wt/fleet/…), base origin/main b0bfbd1.

## What

Division by zero was the one way the single floor-division primitive could still
fail two different ways: JVM `Math/floorDiv` threw a bare
`java.lang.ArithmeticException` (a halted chain at the call site) where JS
divided to `Infinity` and kept computing (silent fork). Reachable from a legal
signed transaction: `bk/impact-price` divided the reference notional by the
price LEVEL (`(fx/fdiv left level)`) and level 0 is a valid resting level
(`validate-order-shape`: `int-in? level 0 (dec n-levels)`) — a resting order at
level 0 put a bare / by zero under the funding-premium walk (falsify-6's class).

Fix: `fx/fdiv` saturates on a zero divisor (sign-aware clamp in `sadd`'s family,
0/0 = 0); `fmod` keeps its identity through the formula (a − 0·q = a);
`bk/impact-price` treats a zero-priced level as affordable in full (what the JS
engine already did by accident) — all clamps, no new throws.

## Measured

RED first, same final test file (6 new assertions in `overflow_gates_test.cljk`
`a-zero-divisor-saturates-the-same-way-everywhere` + `a-zero-priced-level-is-affordable-not-a-division`):

| leg | counts | failures/errors |
|---|---|---|
| `kbb -M:test` (JS engine) | 534 / 3624 | **6 failures** (Infinity / ##NaN vs pinned values) |
| `bash script/jvm-test.sh` | 534 / 3624 | **0 failures / 7 errors**, all `java.lang.ArithmeticException: / by zero` (Math.java:1539) — the 7th is the impact-price pin, i.e. the legal-tx-series halt, live |

The 6-vs-7 gap IS the fork: JS computed silently where the JVM threw.

GREEN measured: `kbb -M:test` **534 tests / 3624 assertions / 0 failures / 0 errors** (EXIT=0).

NOT RUN this tick (runtime budget): post-fix `script/jvm-test.sh`,
`script/nbb-test.sh`, parity diff. Do not read "merged" as both-runtimes-green;
the JVM green leg needs one re-measure tick.

Regression guards in the same tests (pass on both engines pre-fix, pin the
rewrite): floor semantics for negative quotients, i53-max/2, and the 2^52-divisor
boundary pair (i53-max / 2^52 = 1) — the PR#68-deferred "cljs fdiv rounds ≥ 2^52"
hypothesis did not reproduce in-domain; the boundary pins are now permanent.
