# falsify/fuzz iteration — NOT RUN (host load gate)

- Date: 2026-09-03 (JST)
- Trigger: scheduled maturity falsify iteration
- NEXT item at read time: seeded fuzz harness — adversarial block 列を seed 固定で
  folding し、JVM と nbb が同一 seed で同一 state root を出すことを機械検証する。
- Gate: host load averages 43.72 / 34.04 / 26.17 (20:02 JST) — 閾値 20 を超過。
- Decision: 実測 (fuzz fold 実行 / bench / test) を行わない。負荷下の実測は
  throughput・タイミング系の観測を污染し、反証証跡として不正確になるため。
- Action taken: 本 not-run 記録のみ。コード変更なし。status/maturity.md 変更なし
  (スコア・OPEN 赤 ともに前回実測から動かす根拠がない)。
- Next run: load < 20 を確認して seeded fuzz harness の実装・実測に着手。
  成功すれば テスト軸 3→4 / 再現性軸 2→3 の根拠になる。
