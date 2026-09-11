# maturity remeasure not-run — 2026-09-04 03:36 JST (load gate)

## 判定: NOT-RUN (host load > 20)

cron 設定ルール「Host load > 20 → not-run evidence only」に基づき、
`kbb -M:test` / nbb tests / seeded fuzz / `kbb -M:bench` は実行せず記録のみ。

## 実測 load averages

- 03:34 (pre-run script): 33.41 / 24.59 / 21.48 (1/5/15-min)
- 03:36 (再計測):        31.20 / 27.37 / **23.05** (1/5/15-min)

15-min average が 2 回の計測でいずれも 20 を超過 (21.48 → 23.05)。
持続的な高負荷と判断しテスト/bench/fuzz を実行しない。

## 補足 (コード変更なし, HEAD dd55c85 不変)

- スコア変更の根拠となる新実測は本イテレーションでは生成されなかった。
  7 軸スコアはすべて **status/maturity.md 現行のまま据え置き** (最終実測は
  2026-09-04 03:14 remeasure: 両 runtime 357 tests / 915 assertions 全締め,
  18th JVM==nbb parity, 16th fuzz digest byte-identical)。
- falsify-9 (:fees-collected 累算 overflow, evidence/2026-09-04-falsify-9-fees-collected-accum-overflow-notrun.md
  の仮説) は未確定のまま: `evidence/falsify9-jvm.err` に単発の JVM 実行痕
  (ArithmeticException long overflow at Math/multiplyExact) があるが、
  falsify9-jvm.out は 0 byte で falsify9-nbb.* が存在せず、両 runtime verdict
  が取れていないため maturity.md への登録はしない。NEXT の falsify-9 実測は
  load < 20 の次回イテレーションで行うこと (driver は falsify9-{jvm-driver.clj,
  driver.cljs} / falsify9-accum-paths.cljc 常置済み)。
- OPEN 赤 (validate-i53-halt, cumulative-deposit-divergence,
  bench-tape-cancel-arity) はいずれも未修正のまま。

## NEXT (変わりなし, 最小の確定版)

1. validate-i53-halt fix: api/validate :bad-amount i53 上限 + notional 上限
   (level × qty ≤ i53-max) + balance-domain gate (`collateral + amount > i53-max`
   拒否, validate 層か no-op で実装 — cl/deposit 内 fx/check throw は halt を作る)
2. bench-tape-cancel-arity fix (bench.clj:112 owner 付き 3 引数) → n ≥ 1M 低負荷
   3 回安定実測で 再現性 2 → 3
3. fuzz suite 常設化 (両 suite 接続) で テスト/反証 3 → 4
4. falsify-9: :fees-collected 累算 site を同型 probe で両 runtime 実測
