# maturity re-measure — 2026-09-03 22:22–22:31 JST

cron rank iteration (コード変更なし)。

## load gate

- 22:22 (pre-run script): 19.13 / 16.27 / **18.98** — 15-min < 20 → テスト/fuzz 実行可
- bench 系は 1-min が 22:26 時点で **20.43** に達したため **NOT-RUN** (gate 厳守)。
  OPEN 赤 bench-tape-cancel-arity は未修正のまま (コード変更禁止イテレーション)。

## 実測

1. JVM `clojure -M:test`: `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (22:23, evidence/test-jvm-2222.out)
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass`
   (22:24, evidence/test-nbb-2224.out) → 両ランタイム同日同カウント (6 度目の同日実測)。
   備考: classpath なしの `nbb script/tests-on-nbb.cljs` は
   `Could not find namespace: torihiki.address-test` で落ちる (evidence/test-nbb-2223.out)。
   正しい呼び出しは pins 由来 classpath 必須 — これは失敗ではなく使い方の記録。
3. seeded fuzz 16 seeds (evidence/fuzz-seeded.cljc):
   - JVM: `clojure -M -e '(load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run)'`
     → evidence/fuzz-jvm-2231.out
   - nbb: `nbb --classpath "$(nbb script/nbb-classpath.cljs)" -e "(require '[nbb.core :refer [load-file]]) (load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"`
     → evidence/fuzz-nbb-2229.out
   - `diff <(grep '^seed' jvm) <(grep '^seed' nbb)` → **差分なし, JVM==NBB 16/16 seed byte-identical (5 度目の実測)**
   - 21:53 の出力 (fuzz-jvm-2153.out) とも byte-identical → seed 固定再現は 5 度目の追認。

## bench

NOT-RUN (1-min load 20.43 > 20 傾向, 15-min 19.66 と境界)。bench-tape-cancel-arity
(`bench/torihiki/bench.clj:112` の 2 引数 `(bk/cancel! b oid)`) は未修正のため、
実行しても既定 5M tape でクラッシュする既知状態。3 回安定実測の前提は harness 修正。

## スコア判定 (7 軸すべて変動なし)

| 軸 | score | 判定 |
|---|---|---|
| spec/契約 | 3 | 変動素材なし |
| 実装 | 3 | 変動素材なし (損壊は bench harness, engine 本体ではない) |
| テスト | 3 | 22:23–24 両ランタイム 357/915 全緑を再確認。fuzz suite 未接続のため 4 見送り |
| 反証 | 3 | fuzz byte-identical 5 度目。harness 常設化未達のため 4 見送り |
| 再現性 | 2 | seed 固定再現 5 度目追認。bench 3 条件は harness 損壊により不達のまま |
| governor 統合 | 1 | 変動素材なし |
| 運用 | 1 | 変動素材なし |

## NEXT (据え置き — 21:56 改訂版を維持)

次回コード変更許可反復で 2 件をまとめて実施する (1 反復で 3 軸の根拠が動く):

1. **bench-tape-cancel-arity 修正**: bench.clj の cancel 呼び出しに owner を渡す
   (tape 生成側にも owner を持たせる)。修正後 bench 3 条件 (低負荷 3 回安定) を実測し、
   cancel 経路を含む正当な ops/sec を初めて得る → 再現性 2→3 の条件。
2. **fuzz harness 常設化**: fuzz-seeded.cljc を両 suite に接続
   → テスト 3→4 / 反証 3→4 の条件。

governor/運用 (ともに 1) はその後。
