# 2026-10-25 02:30 torihiki-fix tick — LANDED: 「notional money math is total」 (resumes + completes 2026-10-25-0026)

Gate: torihiki_state.sh pre-run load **10.26 / 14.31 / 13.73** — all windows < 20 (tick gate "all > 20 → stop" not triggered) → tick proceeded.

Worktree `~/github/wt/fleet/bot-torihiki-fix-20261025-0026`, branch `bot/torihiki-fix-20261025-0026` @ origin/main **9c0721b** (PR #63 base). Same package and same test file as the 00:26 tick; that tick's uncommitted fix state (fixed.cljk `notional`→`pmul`, clearing.cljk unrealized/FLIP/REDUCING differences → `fx/sadd`) was resumed, not re-scoped.

## Broken-first (the same test file that goes green)

- **RED (unfixed src, final test file)**: `kbb -M:test` → **508 tests / 3526 assertions / 3 failures + 8 errors**, exit 1 (evidence/2026-10-25-0026-notional-totality-red2-kbb.out, measured 00:39:46; test file mtime 00:39:21, first src edit 00:41:23 — src was unfixed). All 11 broken assertions are in the new `overflow_gates_test.cljk` section "notional money math is total" (5 notional clamp ERRORs, unrealized ERROR+FAIL, flip-fill ERROR, om/requirement FAIL+ERROR, equity fold FAIL) — enumerated in the 00:26 tick record.

## Fix completed this tick (the 00:26 plan's steps 3–5, mechanical)

- `clearing.cljk`: `equity` / `maintenance-margin` / `initial-margin` cross-position folds → `fx/acc`; `isolated-liquidatable?` `(+ iso (unrealized …))` → `fx/sadd`.
- `order_margin.cljk`: `market-requirement` bq/aq/loss-b/loss-a folds → `fx/acc`, `(± size bq)` and margin+loss sums → `fx/sadd`, the two no-mark order-notional folds → `fx/acc`; `requirement` outer fold → `fx/acc`; `orders-allowed?` filled/cost folds and `:signed` update → `fx/acc`, `:delta` `(update :delta + A B)` → two `fx/acc` steps with the inner difference saturated (`fx/sadd`) so no operand leaves the domain (slightly stricter than the plan's two-step sketch; same verdicts — the pins do not cover this path and the whole body is a refuse-on-throw check).
- `state.cljk`: liquidation `quote-fn`/`take-fn` filled/cost `reduce + 0` folds → `fx/acc` (plan step 5, lines 1549–1550 / 1583–1584).

## GREEN (exact counts, three legs, identical)

- `kbb -M:test` (SCI) → **508 tests / 3528 assertions / 0 failures / 0 errors**, exit 0 (…-green-kbb.out).
- `bash script/jvm-test.sh` (JVM leg, renamed .cljk→.cljc copy + pinned dep shims) → **508 / 3528 / 0F / 0E**, exit 0 (…-green-jvm.out).
- `bash script/nbb-test.sh` with `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang` (worktree layout needs it — the leg defaults to `<repo>/../<name>`, which is `~/github/wt/fleet/<name>` here and resolves nothing; measured fall `unresolvable: pprint @ 5c9d4aea…`) → **508 / 3528 / 0F / 0E, namespaces 28/28, TESTS-ON-NBB: pass**, exit 0 (…-green-nbb.out).

RED 3526 vs GREEN 3528 assertions on the same 508 tests: the errored deftests aborted before their remaining `is` forms in RED; test count identical.

## Left for later ticks (unchanged from the 00:26 record)

`:size` fold clearing.cljk:137; root-preimage/derived sums (clearing.cljk:252 stake-of, :580-581 bonded, :624 volume-of, state.cljk:2321-2323 bonds/unbonding encode folds); `fx/fmul` exact floor(a·b/c) package (pinned values in evidence/2026-10-23-1219); `trg/valid?` trigger-price i53; funding.cljk:68 premium product. Housekeeping: stale `bot/torihiki-fix-*` worktrees/branches from ticks 20261022-1835 … 20261024-2219b still registered.
