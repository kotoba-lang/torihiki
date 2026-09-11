# maturity remeasure 0657 (2026-09-04 06:57 JST)

反復: torihiki-rank cron (7 軸再測定)。No code changes (HEAD dd55c85, untracked evidence/ + status/ のみ)。 Host load ~17。

## 新規エビデンス (前回 0644 remeasure からの差分)

- **テスト両 runtime 実測 29 度目**: JVM `kbb -M:test` (evidence/test-0657.out, JVM_EXIT=0) と nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk` (evidence/test-nbb-0657.out, NBB_EXIT=0) — 同一カウント 357 tests / 915 assertions, 0 failures。**注意 (既知 pitfall 再確認 0657)**: classpath なしの `kbb --backend sci script/tests-on-nbb.cljk` は `Could not find namespace: torihiki.address-test` で落ちる (この反復で 1 度失敗して classpath 付きで再実行)。classpath は必ず script/nbb-classpath.cljk 経由。
- **seeded fuzz digest 26 度目**: JVM `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'` (evidence/fuzz-jvm-0657.out) vs nbb driver (evidence/fuzz-nbb-driver.cljk → evidence/fuzz-nbb-0657.out) — REPL echo 行 (#'fuzz-seeded/run) を除き byte-identical、かつ 0608 baseline とも byte-identical (diff evidence/fuzz-jvm-0657.out evidence/fuzz-jvm-0608.out = 0)。JVM 呼び出しに `(fuzz-seeded/run)` が無いと echo 行のみで走らない (fuzz-seeded.cljc 先頭の呼び出しコメントがこれを反映していない件、引き続き注意)。
- **bench-tape-cancel-arity 20 実行目の再確認**: `kbb -M:bench` RED (EXIT=1), ArityException `Wrong number of args (2) passed to: torihiki.book/cancel!` at bench.clj:112 (evidence/bench-0657.err)。bench.clj への修正 commit なし (last touch a279427)。
- **validate-i53-halt fix 未着陸**: src/torihiki/api.cljk の :bad-amount 検査は integer?/pos? のまま i53 上限なし (grep 確認 0657)。
- **falsify-11 honest walk 稼働中**: PID 58890, k=300000 時点の walk-progress あり (evidence/falsify11-jvm.err 06:57 更新) — probe 結論には影響なし。

## スコア

全軸変更なし: spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。
- テスト: 29 度目実測に更新。suite 未接続のため 4 据え置き。
- 反証: 26 度目 digest 実測に更新。4 据え置き。
- 再現性: bench red 20 実行目。修正未着陸のため 2 据え置き。

## NEXT (highest-leverage, 変更なし)

validate-i53-halt 総合 fix 一式が最優先:
1. api/validate :bad-amount i53 検査 (:deposit amount / :order qty)
2. :order notional 上限 (level × qty ≤ i53-max) — falsify-7 必須
3. balance-domain gate (collateral + amount > i53-max 拒否, validate 層か no-op) — falsify-8 必須
4. 累算 sum gate (:deficit / :funding-residue / :fees-collected / collateral 引き (fnil − 0), no-op clamp, throw gate 禁止) — falsify-9/10/11 で 4 例確定
5. fx/mul-rate pre-check 積界限 + rate 上限

その後: bench-tape-cancel-arity fix (bench.clj:112 owner 付き 3 引数) + n ≥ 1M 低負荷 3 回安定 → 再現性 3、fuzz suite 常設化 → テスト/反証 4。
