# maturity remeasure 0745 (2026-09-04 07:45–07:50 JST)

反復: torihiki-rank cron (7 軸再測定)。No code changes (HEAD dd55c85, untracked evidence/ + status/ のみ)。

## host load gate

- load averages 07:44: 26.79/25.52/23.58 → 07:49: 23.75/23.69/23.27 — 3 平均とも > 20 (tamaki fleet / cloud.itonami server 等の他ジョブが主因)
- **標準 battery (kbb -M:test / nbb tests / seeded fuzz / bench) は not-run** — 0721 remeasure で確立した load > 20 gate に従う。最新緑実測は 0712 (357 tests / 915 assertions JVM==nbb 30 度目, fuzz digest byte-identical 27 度目), 最新 bench 赤 (cancel-arity) も 0712 が 22 実行目。スコア根拠は 0712 のまま据え置き。

## 新規エビデンス (前回 0712/0721 からの差分)

- **falsify-11 実測確定 (発覚)**: probe A/B の JVM (evidence/falsify11-jvm.out 07:30) vs nbb (evidence/falsify11-nbb.out 05:05) 突合 → **cross 1 で state root 分岐 (d13ec02e… vs 2c059f52…), throw 皆無**。JVM は fees-collected = 2^53+1 (奇数, 正確), nbb は double 丸めで ...992 — 分岐の直接原因は nbb が非可逆値に着地できないこと。fee は両 runtime bit-identical (9000000013, 奇数; nbb boundary-dist +256 は商不変)。verdict 文書: evidence/2026-09-04-falsify-11-fees-collected-accum-overflow.md。falsify-8/9/10 に続き**累算 sum 無検査クラスの第 5 例が実測で確定**。
- falsify-11 probe C (honest-walk, JVM のみ seed なし ~1e6 cross) は実行中 (PID 54913, walk-progress k=100000 fees=9.18e14 @ 07:48, 2^53 crossing まで ~900k cross 残)。完了は次反復以降。

## スコア

全軸変更なし: spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。
- テスト/反証/再現性: battery not-run のため実測カウント更新なし (0712 根拠を維持)。falsify-11 発覚確定は反証軸の「稼働実績」を 1 件積み増すが、4 の条件 (harness 常設化) は未達のまま 3 据え置き。

## NEXT (highest-leverage, 変更なし)

validate-i53-halt 総合 fix 一式が最優先 (falsify-11 実測確定により累算 sum gate の対象に :fees-collected が第 5 例として実証済み):
1. api/validate :bad-amount i53 検査 (:deposit amount / :order qty)
2. :order notional 上限 (level × qty ≤ i53-max) — falsify-7 必須
3. balance-domain gate (collateral + amount > i53-max 拒否, validate 層か no-op) — falsify-8 必須
4. 累算 sum gate (:deficit / :funding-residue / :fees-collected / collateral 累算, no-op clamp, throw gate 禁止) — falsify-8/9/10/**11** の 5 例実測
5. fx/mul-rate pre-check 積界限 + rate 上限

その後: bench-tape-cancel-arity fix (bench.clj:112 owner 付き 3 引数) + n ≥ 1M 低負荷 3 回安定 → 再現性 3、fuzz suite 常設化 → テスト/反証 4。
