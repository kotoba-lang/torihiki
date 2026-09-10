# maturity remeasure skip — 2026-09-09 10:37 枠 (frame 386, load gate skip)

## 判定
- 負荷ゲート (全 window < 20) 不成立:
  - pre-run 10:34 (pre-run script 実測): 1-min **129.99** / 5-min **102.86** / 15-min **100.85**
  - 直測 10:36:09: 1-min **152.08** / 5-min **120.62** / 15-min **108.21**
  - 直測 10:37:42: 1-min **134.25** / 5-min **125.42** / 15-min **111.47**
  - 3 回の測定全て全 window ≥ 20 → test/parity/bench/falsify 新規実測は not-run。

## 正本引用 (再検収)
- HEAD = **33602ff** (frame 384 / skip 1028 の commit, 10:29)。10:36–10:37 の間で不変。
- 直近実測正本は frame-286 (commit e81a243, 09-08 12:06) **suite 130** のみで不変 (frame-381 の phantom frame-287/suite-131 引用欠陥は frame-382 で確定済)。
- git diff HEAD -- src/ script/ deps.edn = **0 bytes** → コード変更なし。
- working tree untracked 915 行の内訳を実測: `_diag*.py` / `_ins*.py` / `_mat_279.sh` / `_mbench_run.sh` / `_verify279.py` / `precheck_ls.txt` の計 12 ファイル + `evidence/` 配下 902 ファイル (過去枠の evidence / staging 中間物)。src/, script/, status/ への未commit変更は無し。
- 前枠の記録重複を実検収: 9657965 (10:29) と 33602ff (10:29) が**双方「frame 384」を名乗る**。1027 枠 = 真 frame 384、1028 枠 = 実走行順序で frame 385 (誤記 384)。本枠は実走行順序数に従い **frame 386** に正本化。

## スコア
7 軸すべて変更なし (**spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1**)。fix 未着手のため OPEN 赤 6 件 + bench-tape-cancel-arity は全て OPEN のまま。

## NEXT (不変)
validate-i53-halt fix パッケージ (validate 層 i53/notional/balance-domain gate + 累算 sum gates :deficit/:funding-residue/:fees-collected/collateral + fx/mul-rate pre-check + REDUCING 積 pre-limit + rate 上限) → bench 3 箇所 fix (bench.clj:112 / probe.clj:31 / curve.clj:21) → fuzz suite 常設化。

## 次ランナー指示
負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **131** / parity **39** / bench **91**; 引用正本 frame-286 / suite-130 / e81a243 の test-286-*.log)。frame 番号は本枠 386 の次 = **387** から。
