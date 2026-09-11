# maturity bench iteration 2026-09-04 01:21 (cron, 測定のみ・コード変更なし)

## gate

- iteration start host load: 10.06 / 14.79 / 19.23 (1-min < 20 → 実行可)
- iteration end host load: 19.62 / 16.22 / 18.87

## 実測

1. JVM `kbb -M:test` → evidence/test-jvm-0121.out:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (exit 0, 緑)
2. bench `kbb -M:bench` (既定 5M tape) → evidence/bench-0121.out/.err — **6 実行目の赤追認**:
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112).`
   `Wrong number of args (2) passed to: torihiki.book/cancel!` (exit 1)
   → ウォームアップ後の計測本体に到達する前にクラッシュするため **throughput 数値は取得不可**。
   ソースは bench.clj:112 `(bk/cancel! b oid)` のまま (book.cljc の現行署名は owner 付き)。
   OPEN 赤 bench-tape-cancel-arity は未修正のまま (本 iter は no-code-change が指示)。

## スコア判定

- 0114 版 (14th dual-runtime iteration) からの変化なし。本 iter は追認のみ:
  - テスト 3 維持: JVM 緑の追認 (JVM 単独。nbb 半分は本 iter の範囲外 — 0119 で 14 度目実測済み)
  - 再現性 2 維持: bench 3 条件 (低負荷 3 回安定) は bench-tape-cancel-arity 赤のため引き続き不達
- spec 3 / 実装 3 / 反証 3 / governor 1 / 運用 1: 動きなし

## NEXT (前回からの据え置き)

bench-tape-cancel-arity 修正 (bench.clj:112 に owner を渡す) → bench 3 回安定実測で
再現性 3。fuzz suite 常設化 (tests への接続) でテスト/反証 4 への道。
