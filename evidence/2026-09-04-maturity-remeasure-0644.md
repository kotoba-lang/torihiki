# maturity remeasure 0644 (2026-09-04 06:44 JST)

反復: torihiki-rank cron (score 再測定 + falsify-11 正式登録)。No code changes.

## 新規エビデンス (前回 0622 remeasure からの差分)

- **falsify-11 JVM probe 着地** → `fees-collected-accum-overflow` 正式登録 (第 4 例確定)。
  - evidence/falsify11-jvm.out (0621): seed root b264b448… (nbb 0505 と一致) → cross1 fees JVM 9007199254740993 vs nbb …0992 (double 丸め)、cross1–4 root 全不一致 (d13ec02e… / 16b23892… / ca50c7cc… / 9716dbe3… vs nbb 2c059f52… / ad546332… / 1133eace… / b33a2253…)。
  - probe 設計は falsify10 の境界距離 trick を踏襲 (p=9000000013 odd, prod=9000000013500000000 < 2^63 → mul-rate bit-identical)。falsify-10 予測どおり。
  - falsify11-jvm.err は後続 JVM honest walk (k=200000 時点で走行中, PID 58890) — probe 結果には影響なし。

## スコア

全軸変更なし: spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。
- 反証: 発覚件数を 5 件に更新 (falsify-11 追加)。suite 未接続のため 4 は据え置き。
- テスト/再現性: 新計測なし (最新 0614/0622 のまま)。bench red 未修正のため据え置き。

## OPEN 赤

5 件発覚 + 1 件常設 (falsify-11 を fees-collected-accum-overflow として追加)。

## NEXT (highest-leverage)

validate-i53-halt 総合 fix 一式が最優先のまま:
1. api/validate :bad-amount i53 検査 (:deposit amount / :order qty)
2. :order notional 上限 (level × qty ≤ i53-max) — falsify-7 必須
3. balance-domain gate (collateral + amount > i53-max 拒否, validate 層か no-op) — falsify-8 必須
4. 累算 sum gate (:deficit / :funding-residue / :fees-collected / collateral 引き (fnil − 0), no-op clamp, throw gate 禁止) — falsify-9/10/11 で 4 例確定
5. fx/mul-rate pre-check 積界限 + rate 上限 (両 runtime deterministic)

その後: bench-tape-cancel-arity fix (bench.clj:112 3 引数) + n ≥ 1M 低負荷 3 回安定 → 再現性 3、fuzz suite 常設化 → テスト/反証 4。
