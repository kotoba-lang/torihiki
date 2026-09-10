# maturity remeasure skip — 2026-09-09 10:45 枠 (frame 387, load gate skip)

## 判定
- 負荷ゲート (全 window < 20) 不成立:
  - pre-run 10:39 (pre-run script 実測): 1-min **96.72** / 5-min **115.67** / 15-min **109.58**
  - 直前枠 (10:38:27, skip-check-0909-1037.txt): 1-min **114.98** / 5-min **121.59** / 15-min **110.77**
  - 全 window ≥ 20 が継続 → test/parity/bench/falsify 新規実測は not-run。
- 備考: 本枠では terminal 呼び出しが出力空 (高負荷既知 fault — torihiki_state.sh 300s timeout と同型) のため、
  HEAD hash / git diff の直接実測は not-run。負荷判定は pre-run script 直測値 (10:39) のみに依拠。
  直前枠 (frame 386, 10:37) 時点で HEAD = 33602ff, src/script/deps.edn diff 0 bytes が実測済みであり、
  本枠は cron 枠 (no code changes) のためコード不変を枠間契約として引用。

## スコア
7 軸すべて変更なし (**spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1**)。
fix 未着手のため OPEN 赤 6 件 + bench-tape-cancel-arity は全て OPEN のまま。

## NEXT (不変)
validate-i53-halt fix パッケージ (validate 層 i53/notional/balance-domain gate + 累算 sum gates
:deficit/:funding-residue/:fees-collected/collateral + fx/mul-rate pre-check + REDUCING 積 pre-limit +
rate 上限) → bench 3 箇所 fix (bench.clj:112 / probe.clj:31 / curve.clj:21) → fuzz suite 常設化。

## 次ランナー指示
負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **131** / parity **39** / bench **91**;
引用正本 frame-286 / suite-130 / e81a243 の test-286-*.log)。frame 番号は本枠 387 の次 = **388** から。
