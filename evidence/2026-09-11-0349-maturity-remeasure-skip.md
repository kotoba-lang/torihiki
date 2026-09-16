# 2026-09-11 0349 frame 411 load gate skip (no code changes, cron)

## 負荷測定 (直測)

- pre-run 03:43 (torihiki_state.sh 経由): 1-min **7.42** / 5-min **9.51** / 15-min **10.27** — 全 window <20
- 本枠 03:49 直測 3 回:
  - (1) 1-min 19.37 / 5-min 13.84 / 15-min 11.66
  - (2) 1-min **20.51** / 5-min 14.37 / 15-min 11.89  ← 1-min ≥20
  - (3) 1-min 19.75 / 5-min 14.31 / 15-min 11.88

## 判定

1-min が 3 回中 1 回 **20.51 (≥20)** を記録し 20 を跨いでいるため「全 window <20」を確定できず —
**gate ambiguous 枠** (frame-407 前例: 1-min ≥20 なら不成立; frame-397 前例: 低下局面 1-min <20 なら genuine)。
中間測定 20.51 ≥20 のため保守側で **skip** とし、test/parity/bench/falsify 新規実測は **not-run**。
5-min / 15-min は全測定 <20 で低下局面維持 — 次枠で gate 成立の公算大。

## コード不変 (直接実測)

- HEAD **7ec6411** (d8fb6be から移行: 09-11 00:48, `91d23b8` frames 404-410 ledger landing + `9bea09f` frames 381-403 merge + `7ec6411` repo-bot landing)
- `git diff HEAD --stat -- src/ script/ deps.edn` = 0 行
- working tree: `M evidence/test-jvm-411.out`, `?? evidence/test-nbb-411.{err,out}` のみ (evidence のみ, src 変更なし)

## IN-FLIGHT 検収 (01:08 orphaned runner の出力実読)

- evidence/test-jvm-411.out = **357 tests / 915 assertions, 0 failures 0 errors, JVM_EXIT=0**
- evidence/test-nbb-411.out = **357 tests / 915 assertions, 0 failures 0 errors, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0**
- evidence/test-nbb-411.err = 0 bytes
- 両 runtime 同一カウント成立。ただし本枠は skip 枠のため suite **136 / 39 度目** の正本採用は次 genuine 枠の ledger 登録に委ねる (bench / fuzz / falsify 出力は 411 系に無し)。
- 標記注意: orphaned runner が自枠番号 411 で evidence を命名済み — 本枠 (03:49) が ledger 上の **frame 411** (実走行順序数), 併記により重複枠なし。

## 状態

- 正本引用 base = 7ec6411 landing state (frame-406 d8fb6be 実測 suite **135** / fuzz digest 32 度目 / bench 赤累計 **31 実行目** を引用)
- frame-407 登録の新規 hypothesis (複数アカウント deficit 合算の集計経路 — liq/liquidate wrapper の touched accounts 横断集計 site が (fnil + 0) sum 無検査なら第 7 累算 site, falsify-12 複製候補 = falsify-14 候補) は**未実測のまま** — 次の genuine 枠で実測予定
- 発覚 0 件, 新規 hypothesis なし
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 13 件, 発覚 7 件, falsify-8〜13 累算 overflow クラス OPEN のまま)

## 残作業不変

- validate-i53-halt fix パッケージ (api/validate :bad-amount i53 + notional 上限 + balance-domain gate + 累算 sum gate 6 site + settle-deficit delta clamp + fx/mul-rate pre-check + REDUCING 積 pre-limit + rate 上限)
- bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21)
- fuzz 常設化 + nbb-classpath bootstrap fix (2 段構成)

## 次

- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **136** / parity **39** / bench **96**; 上記 hypothesis 実測) — 次枠番号 = **412**
