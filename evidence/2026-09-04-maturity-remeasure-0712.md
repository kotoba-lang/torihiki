# maturity remeasure 0712 (2026-09-04 07:12 JST)

反復: torihiki-rank cron (7 軸再測定)。No code changes (HEAD dd55c85, untracked evidence/ + status/ のみ)。Host load ~30 (高負荷, 実測時間には反映なし)。

## 新規エビデンス (前回 0657 remeasure からの差分)

- **テスト両 runtime 実測 30 度目**: JVM `kbb -M:test` (evidence/test-0712.out, JVM_EXIT=0) と nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk` (evidence/test-nbb-0712.out, NBB_EXIT=0) — 同一カウント 357 tests / 915 assertions, 0 failures。
- **seeded fuzz digest 27 度目**: JVM (evidence/fuzz-jvm-0712.out) vs nbb driver (evidence/fuzz-nbb-0712.out) — REPL echo 行を除き byte-identical、かつ 0657/0608 baseline とも byte-identical (diff vs fuzz-jvm-0608.out = 0)。**新規 pitfall 再確認 (0712)**: 素 `kbb --backend sci evidence/fuzz-nbb-driver.cljk` は `Could not find namespace: torihiki.state` で落ちる (本反復で 1 度失敗し classpath 付きで再実行) — nbb 側も `--classpath "$(kbb --backend sci script/nbb-classpath.cljk)"` が必須。maturity.md 反証軸の根拠に反映済み。
- **bench-tape-cancel-arity 22 実行目の再確認**: `kbb -M:bench` RED (BENCH_EXIT=1), ArityException `Wrong number of args (2) passed to: torihiki.book/cancel!` at bench.clj:112 (evidence/bench-0712.err)。
- **validate-i53-halt fix 未着陸**: api.cljc の :bad-amount 検査は integer?/pos? のまま i53 上限なし (grep 確認 0712, :202 他)。
- **falsify-11 honest walk 継続中**: JVM probe PID 58890, k=400000 時点の walk-progress (evidence/falsify11-jvm.err 07:10 更新) — 登録済み結論には影響なし。

## スコア

全軸変更なし: spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。
- テスト: 30 度目実測に更新。suite 未接続のため 4 据え置き。
- 反証: 27 度目 digest 実測に更新。4 据え置き。
- 再現性: bench red 22 実行目。修正未着陸のため 2 据え置き。

## NEXT (highest-leverage, 変更なし)

validate-i53-halt 総合 fix 一式が最優先:
1. api/validate :bad-amount i53 検査 (:deposit amount / :order qty)
2. :order notional 上限 (level × qty ≤ i53-max) — falsify-7 必須
3. balance-domain gate (collateral + amount > i53-max 拒否, validate 層か no-op) — falsify-8 必須
4. 累算 sum gate (:deficit / :funding-residue / :fees-collected / collateral 引き (fnil − 0), no-op clamp, throw gate 禁止) — falsify-9/10/11 で 4 例確定
5. fx/mul-rate pre-check 積界限 + rate 上限

その後: bench-tape-cancel-arity fix (bench.clj:112 owner 付き 3 引数) + n ≥ 1M 低負荷 3 回安定 → 再現性 3、fuzz suite 常設化 → テスト/反証 4。
