# 2026-09-09 0945 frame — maturity re-measure SKIP (load gate)

- **Frame**: 381 度目枠 (load gate skip)
- **Trigger**: cron, 2026-09-09 09:45 JST
- **Maturity**: torihiki L1 (看本)

## Load gate (全 window <20 required to run full gate)

- 2026-09-09 09:45 JST uptime: 1-min **99.56** / 5-min **94.52** / 15-min **64.55**
- (pre-run 09:39: 85.63 / 59.53 / 39.73)
- All windows ≥20 ⇒ 「全 <20」not satisfied ⇒ **high-load continuing** ⇒ test/parity/bench/falsify new measurements NOT run.
- Evidence-only frame (no code changes).

## Canonical reference (cited and re-verified)

直近正本 287 実測 (suite **131 度目実測**, HEAD e81a243) re-verified:
- evidence/test-287-jvm.log — 357/915 全緑 0 failures 0 errors JVM_EXIT=0
- evidence/test-287-nbb.log — 357/915 全緑 17/17 ns TESTS-ON-NBB pass NBB_EXIT=0
- evidence/parity-287.log — 固定 38 + fixed/result 20 cases 0 drift KOTOBA_PARITY_EXIT=0
- evidence/bench-287.log — 同一 ArityException bench.clj:112 (cancel! wrong-args) BENCH_EXIT=1 既知赤
- All consistent.

## HEAD / source status

- HEAD **91e86ede** (380 度目枠 =0937 skip commit; ledger line + evidence)
- src/ 変更なし — `git diff HEAD -- src/ script/` = 0 bytes (正本引用確認済)

## Parallel tangle

- 観測: 無し. HEAD 91e86ede already canonicalized frame 380 (=0937); frame-381 uncommitted evidence not found.
- 本枠 09:45 を重複枠なきよう **381** に正本化. 重複枠なし (fold 不要).

## NEXT / falsify status

- NEXT 未実測リスト **0 件** (falsify-38 完結). 本枠の反証なし, 発覚 0 件, 新規 hypothesis なし.
- スコア 7 軸すべて変更なし (3/3/3/3/2/1/1; 反証実施 **36 件**, 発覚 **30 件**).
- 残作業は不変: fix 実装 (validate-i53-halt スロット falsify-15〜38 統合 + api/validate :withdraw-attest case 追加 + :account/:credit/:builder account-id i53 cap + :amend-market/:list-market spec cap + :authorize-agent :expires cap + :set-referrer :referrer cap + :scale :qty/:step cap) + bench 3 箇所 fix (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化.

## 次ランナー

負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (suite **131 度目実測引用** / parity **38 実測目引用** / bench **90 実行目引用**想定赤; 新規実測時 suite 132 / parity 39 / bench 91).

## Recorded

- This skip md: evidence/2026-09-09-0945-maturity-remeasure-skip.md
- Citation check: evidence/skip-check-0909-0945.txt
- Ledger line appended to status/maturity.md (frame 381)
