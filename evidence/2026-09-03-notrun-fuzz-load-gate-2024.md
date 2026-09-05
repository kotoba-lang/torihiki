# falsify/fuzz iteration — NOT RUN (host load gate, 3rd)

- Date: 2026-09-03 (JST, 20:24)
- Trigger: scheduled maturity falsify iteration
- NEXT item at read time: seeded fuzz harness — adversarial block 列を seed 固定で
  folding し、JVM と nbb が同一 seed で同一 state root を出すことを機械検証する。
- Gate: host load averages 18.26 / 21.34 / 28.05 (20:24 JST 実測)。
  1 分平均は 20 を下回ったが、5 分平均 21.34 / 15 分平均 28.05 が閾値超過のまま。
  先行 not-run (20:13) の判定基準「load < 20」は全平均に適用して解釈した。
- Decision: 実測 (fuzz fold 実行 / bench / test) を行わない。負荷が残存している
  あいだの実測は throughput・タイミング系の観測を污染する。
- 併記: 本ジョブは "No code changes" 制約付きのため、seeded fuzz harness の
  実装自体も本 run では行えない (実装待ちではなく制約待ち)。
- Action taken: 本 not-run 記録のみ。コード変更なし。status/maturity.md 変更なし
  (スコア・OPEN 赤ともに前回実測 remeasure-1952 から動かす根拠がない)。
- 備考: 負荷は明確に下降中 (20:13 実測 36.53/42.09/37.43 → 20:24 実測
  18.26/21.34/28.05)。次回 run ではゲート通過の可能性が高い。
- Next run: 全 load average < 20 を確認のうえ、seeded fuzz harness の
  実装・実測に着手。成功すれば テスト軸 3→4 / 再現性軸 2→3 の根拠になる。
