# torihiki maturity remeasure — 2026-09-03 ~19:52 (cron iteration 6)

## Host load
`19:51 up 10 days, load averages: 19.31 15.70 15.81` — 1min は閾値 20 未満 → full run allowed。

## `clojure -M:test` (JVM)
```
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.
```

## nbb クロスランタイム (script/tests-on-nbb.cljk, classpath は pins から生成)
```
0 failures, 0 errors.
namespaces 17/17
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.
TESTS-ON-NBB: pass — the runtime that deploys ran the suite
```
→ **両ランタイムが同一カウント (357/915) で全緑**。README 記載の 2026-08-31 実測
(325/847) から suite が成長しており、今回が新カウントの両サイド実測初記録。
falsify-2 提案の「nbb で snapshot round-trip oid 一貫性 parity」は
torihiki.snapshot-test が nbb suite に含まれる (17/17) ため、**この実行で既に充足**。

## `clojure -M:bench 100000` (JVM)
```
THROUGHPUT       109,592 ops/sec
latency          9125 ns/op
ratio vs HyperCore reference: 0.5x
```
同日 19:17 (bench-1917) / 19:45 (bench-1948) 実測と同桁帯 → 再現性安定
(load ~16-19 下でも変動なし)。

## 反証の進捗 (evidence/ より)
- falsify-1 (binding race): survived — engine 認証層のみで、proposer の順序操作のみ。
- falsify-2 (snapshot free-slot / oid replica 一貫性): survived — 検証は
  test/snapshot_test.cljc に既に存在 (4 テスト)、churned fixture 上で JVM 実測グリーン。
  さらに本 remeasure で nbb サイドも同一 suite グリーン → OPEN 赤 #2 完全解消。

## スコア変更
- 反証 2 → **3**: 反証ループが 2 回回り (falsify-1, falsify-2)、双方 harness 実測 +
  evidence/ 固定 + survived 判定。単発実測から反復可能な手順になった。
- テスト 3 の根拠を更新 (スコア据え置き): 両ランタイム同日実測 357/915。
  property/fuzz ベースは未整備のため 4 にはしない。
- 他 5 軸は変化なし (根拠の日付・カウントのみ更新)。

## Changes
コード変更なし。evidence 記録と status/maturity.md 更新のみ。

## NEXT (提案 — highest leverage)
seeded fuzz harness (現行 NEXT を維持、かつ今回で最短距離になった):
- falsify-1/2 が示したのは「特定の疑いは個別に潰せた」こと。次の lever は
  疑いを挙げる作業自体の機械化: seed 固定の adversarial block 列を fold し、
  JVM と nbb が同一 seed で同一 state root / 同一 :rejected を出すことを
  常設ジョブにする。テスト軸 (3→4) と 再現性軸 (2→3) を直接押す。
