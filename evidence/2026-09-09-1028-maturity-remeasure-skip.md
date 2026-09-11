# 2026-09-09 1028 frame — maturity re-measure SKIP (load gate)

- **Frame**: 384 度目枠 (load gate skip; no code changes)
- **Trigger**: cron, 2026-09-09 10:25–10:28 JST
- **Maturity**: torihiki L1 (正本)

## Load gate (全 window <20 required to run full gate)

- pre-run 10:25 JST uptime: 1-min **87.58** / 5-min **92.92** / 15-min **101.89**
- 直測 10:28 JST uptime: 1-min **82.15** / 5-min **88.72** / 15-min **98.79**
- All windows ≥20 ⇒ 「全 <20」not satisfied ⇒ **high-load continuing** ⇒ test/parity/bench/falsify new measurements NOT run.
- Evidence-only frame. No code changes (`git diff HEAD -- src/ script/ deps.edn` = 0 lines).

## Canonical reference

Latest full-gate canonical = **frame-286 (commit `e81a243`, 09-08 12:06), suite 130** — as re-established at frames 382/383 (frame-381's phantom "287/suite-131" citation already flagged and corrected at frame 382). Content re-verified present in git `e81a243`:

- `evidence/test-286-jvm.log` — 357 tests / 915 assertions, 0 failures / 0 errors, 17/17 ns, JVM_EXIT=0
- `evidence/test-286-nbb.log` — 357 tests / 915 assertions, 0 failures / 0 errors, 17/17 ns TESTS-ON-NBB pass, NBB_EXIT=0
- `evidence/parity-286.log` — fixed **38** cases + fixed/result **20** cases, 0 drift, KOTOBA_PARITY_EXIT=0
- `evidence/bench-286.log` — same **ArityException bench.clj:112 cancel! 2-arg** — known red

⇒ next fresh measurement (after load <20) = **suite 131 / parity 39 / bench 91**.

## HEAD / source status

- HEAD **9f8d9b9** (= frame-383 skip commit, 09-09 ~10:21) — unchanged 10:25→10:28, no `.git/*.lock`, no parallel tangle.
- src/ 変更なし — `git diff HEAD -- src/ script/ deps.edn` = 0 lines.
- No new non-bench merges since frame-383: OPEN-red cited lines still valid (spot re-verified below).

## OPEN-red citations re-verified

- **api.cljc:202** `:deposit` — amount `integer?`/`pos?` only, **no i53 upper cap** (falsify-6/7/8).
- **api.cljc:68** `:order` qty — `integer?`/`pos?` only, **no i53 / notional cap** (falsify-7).
- **clearing.cljc:711** `settle-deficit` sum `(fnil + 0)` unchecked (falsify-9, 12).
- **clearing.cljc:726** `deposit` collateral `(fnil + 0) (- amount repaid)` unchecked (falsify-8).
- **funding.cljc:138** `:funding-residue` `(fnil + 0) p` unchecked (falsify-10).
- **bench/torihiki/bench.cljk:112** `(bk/cancel! b oid)` — re-read at 9f8d9b9, still the retired 2-arg signature → known red (bench-tape-cancel-arity).

## NEXT / falsify status

- NEXT 未実測 0 件のまま (fix 未着手). 本枠の反証なし, 発覚 0 件, 新規 hypothesis なし.
- スコア 7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 36 件照合, 累算 overflow クラス falsify-8〜12 は OPEN のまま).
- 残作業は不変: fix 実装 (validate-i53-halt パッケージ = api/validate i53/notional/balance-domain gates + 累算 sum gates :deficit/:funding-residue/:fees-collected/collateral + fx/mul-rate pre-check + REDUCING 積 pre-limit) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化.

## 次ランナー

負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **131** / parity **39** / bench **91**; 引用正本は frame-286 / suite-130 / e81a243 の test-286-*.log)。

## Recorded

- This skip md: evidence/2026-09-09-1028-maturity-remeasure-skip.md
- Citation check: evidence/skip-check-0909-1028.txt
- Ledger line appended to status/maturity.md (frame 384)
