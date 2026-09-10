# 2026-09-09 1027 frame — maturity re-measure SKIP (load gate)

- **Frame**: 384 度目枠 (load gate skip; no code changes)
- **Trigger**: cron, 2026-09-09 10:24–10:27 JST
- **Maturity**: torihiki L1 (正本)

## Load gate (全 window <20 required to run full gate)

- pre-run (torihiki_state.sh) 10:24 JST uptime: 1-min **83.45** / 5-min **93.62** / 15-min **102.74**
- 直測 10:27 JST uptime: 1-min **84.55** / 5-min **89.62** / 15-min **99.34**
- All windows ≥20 ⇒ 「全 <20」not satisfied ⇒ **high-load continuing** ⇒ test/parity/bench/falsify new measurements NOT run.
- Evidence-only frame. No code changes (`git diff HEAD -- src/ script/ deps.edn` = 0 lines).

## Canonical reference

Latest full-gate canonical = **frame-286 (commit `e81a243`, 09-08 12:06), suite 130** — unchanged from frames 381–383:

- `evidence/test-286-jvm.log` — 357 tests / 915 assertions, 0 failures / 0 errors, 17/17 ns, JVM_EXIT=0
- `evidence/test-286-nbb.log` — 357 tests / 915 assertions, 0 failures / 0 errors, 17/17 ns TESTS-ON-NBB pass, NBB_EXIT=0
- `evidence/parity-286.log` — fixed 38 + fixed/result 20 cases, 0 drift, KOTOBA_PARITY_EXIT=0
- `evidence/bench-286.log` — ArityException bench.clj:112 cancel! 2-arg — known red

⇒ next fresh measurement (after load <20) = **suite 131 / parity 39 / bench 91**.

## HEAD / source status

- HEAD **9f8d9b9** (= frame-383 skip commit, 09-09 10:23) — unchanged 10:24→10:27, no `.git/*.lock`.
- src/ 変更なし — `git diff HEAD -- src/ script/ deps.edn` = 0 lines.

## OPEN-red citations re-verified (sed line reads, all still live)

- **api.cljc:68** `:order` qty — `(not (and (integer? qty) (pos? qty))) :bad-quantity`, no i53/notional cap (falsify-7)
- **api.cljc:202** `:deposit` — integer?/pos? only, no i53 cap (falsify-6/7/8)
- **clearing.cljc:711** `:deficit` sum `(fnil + 0)` unchecked (falsify-9, 12)
- **clearing.cljc:726** `deposit` collateral `(fnil + 0)` unchecked (falsify-8)
- **funding.cljc:138** `:funding-residue` `(fnil + 0)` unchecked (falsify-10)
- **bench/torihiki/bench.clj:112** `(bk/cancel! b oid)` 2-arg — known red (bench-tape-cancel-arity)

## NEXT / falsify status

- 本枠の反証なし (load gate), 発覚 0 件, 新規 hypothesis なし. NEXT 未実測 0 件のまま (fix 未着手).
- スコア 7 軸すべて変更なし (**3/3/3/3/2/1/1**).

## 次ランナー

負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **131** / parity **39** / bench **91**; 引用正本は frame-286 / suite-130 / e81a243 の test-286-*.log)。

## Recorded

- This skip md: evidence/2026-09-09-1027-maturity-remeasure-skip.md
- Citation check: evidence/skip-check-0909-1027.txt
- Ledger line appended to status/maturity.md (frame 384)
