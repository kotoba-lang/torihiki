# 2026-09-04 06:14 maturity remeasure (JVM test + bench)

## Host load
- 06:14: load averages **19.61 / 20.60 / 20.94** (1-min が閾値 20 直下まで低下したため test 実行を試行)。10-day uptime。

## JVM test — ✅ 緑
- `kbb -M:test` → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
- 実行時間 20.4s wall (37.2s user)。
- evidence: test-0614.out / test-0614.err
- 備考: 0551 remeasure と同一カウント (357/915)。nbb 側は本回未実施。

## Bench — ❌ 既知の赤を 18 実行目で再確認
- `kbb -M:bench` → tape 生成後 warm-up で
  `ArityException at torihiki.bench/run-tape (bench.clj:112): Wrong number of args (2) passed to: torihiki.book/cancel!`
- bench-tape-cancel-arity は未修正のまま (cancel! owner 必須化に bench が未追従)。NEXT の fix 項目のまま変化なし。
- evidence: bench-0614.out / bench-0614.err
- 5M-op tape を生成した後の失敗につき throughput 計測は不達。再現性 3 の条件 (n ≥ 1M 低負荷 3 回安定) は引き続き未達。

## 結論
- テスト軸: 変化なし (357/915 全緑を維持)。コード変更なし。
- 再現性軸: bench harness fix まで 2 のまま。
