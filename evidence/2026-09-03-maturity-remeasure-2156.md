# maturity re-measure — 2026-09-03 21:52–21:57 JST

cron rank iteration (コード変更なし)。

## 実測

1. JVM `clojure -M:test`: `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (21:52)
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass` (21:53)
   → 両ランタイム同日同カウント。テスト軸 3 の根拠は再確認。
3. seeded fuzz 16 seeds (load gate 通過後: 21:53 実行直前 1-min load 12.92 < 20):
   - JVM → evidence/fuzz-jvm-2153.out / nbb → evidence/fuzz-nbb-2153.out
   - `#'` eval echo 行を除き diff → **JVM==NBB 16/16 seed byte-identical (4 度目の実測)**
   - 21:11 の両出力 (fuzz-jvm-2111.out / fuzz-nbb-2111.out) とも byte-identical
     → seed 固定再現は 4 度目の追認。

## bench (5-min load gate 通過: 21:55 実行直前 5-min avg 17.83 < 20)

- `clojure -M:bench` (引数なし, 既定 5M tape): **実行時クラッシュ**。
  `ArityException ... Wrong number of args (2) passed to: torihiki.book/cancel!` (bench.clj:112)
  → **新規 OPEN 赤**: bench harness が `cancel!` の owner 引数必須化 (セキュリティ修正,
  book.cljc:542 docstring「no two-argument arity left behind」) に未追従。
  bench/torihiki/bench.clj:112 が `(bk/cancel! b oid)` の 2 引数呼び出しのまま。
- `clojure -M:bench 100000`: 完走したが `cancelled 0` — 小さい tape では cancel 分岐の
  `(pos? oid)` が実質踏まれないため、**過去の bench 実測 (170,382 ops/sec 等) は
  cancel 経路を一切測っていない**ことになる。 crashes 未検出のまま數値だけ引用されていた。
  実測値 48,908 ops/sec / latency 20,447 ns/op (evidence/bench-2157.out) は
  実行中 1-min load 26.18 の高負荷下で、比較価値なし。
- bench 3 条件 (低負荷 3 回安定) は harness 損壊 + 負荷変動により**不達のまま**。

## スコア判定 (7 軸すべて変動なし)

| 軸 | score | 判定 |
|---|---|---|
| spec/契約 | 3 | 変動素材なし |
| 実装 | 3 | 変動素材なし (損壊しているのは bench harness であり engine 本体ではない) |
| テスト | 3 | 21:52–53 両ランタイム 357/915 全緑を再確認。fuzz suite 未接続のため 4 見送り |
| 反証 | 3 | fuzz byte-identical 4 度目。harness 常設化未達のため 4 見送り |
| 再現性 | 2 | seed 固定再現 4 度目追認。bench 3 条件は harness 損壊により遠のいた (新規赤) |
| governor 統合 | 1 | 変動素材なし |
| 運用 | 1 | 変動素材なし |

## OPEN 赤 (新規)

**bench-tape-cancel-arity**: `bench/torihiki/bench.clj:112` が `cancel!` の 2 引数
旧署名を呼び、既定 tape (5M) で即クラッシュ。100k tape は cancelled 0 で完走するため
過去 bench 数値が cancel 経路を未測定のまま出ていたことが判明。

## NEXT (改訂)

次回コード変更許可反復で 2 件をまとめて実施する (1 反復で 3 軸の根拠が動く):

1. **bench-tape-cancel-arity 修正**: bench.clj の cancel 呼び出しに owner を渡す
   (tape 生成側にも owner を持たせる)。修正後 bench 3 条件 (低負荷 3 回安定) を実測し、
   cancel 経路を含む正当な ops/sec を初めて得る → 再現性 2→3 の条件。
2. **fuzz harness 常設化** (falsify-4, 据え置き): fuzz-seeded.cljc を両 suite に接続
   → テスト 3→4 / 反証 3→4 の条件。

governor/運用 (ともに 1) はその後。
