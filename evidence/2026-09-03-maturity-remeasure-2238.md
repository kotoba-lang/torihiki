# maturity re-measure — 2026-09-03 22:37–22:39 JST

cron rank iteration (コード変更なし)。

## load gate

- 22:37 (iteration start): 14.81 / 16.85 / **17.88** — 15-min < 20 → テスト/fuzz 実行可
- 22:39: 19.76 / 18.44 / 18.39 — bench 系は 1-min が 20 に迫ったため **NOT-RUN** (gate 厳守)。
  そもそも OPEN 赤 bench-tape-cancel-arity が未修正のため bench は既定 5M tape で
  クラッシュする既知状態 (コード変更禁止イテレーション)。

## 実測

1. JVM `clojure -M:test`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (22:37, evidence/test-jvm-2237.out)
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass`
   (evidence/test-nbb-2238.out) → 両ランタイム同日同カウント (7 度目の同日実測)。
3. seeded fuzz 16 seeds (evidence/fuzz-seeded.cljk), 両ランタイム:
   - JVM → evidence/fuzz-jvm-2238.out
   - nbb → evidence/fuzz-nbb-2238.out
   - `diff <(grep '^seed' jvm) <(grep '^seed' nbb)` → **差分なし, JVM==NBB 16/16 seed byte-identical (6 度目の実測)**
   - 21:53 の出力 (fuzz-jvm-2153.out) とも byte-identical → seed 固定再現は 6 度目の追認。

## OPEN 赤の追認

- bench-tape-cancel-arity は未修正のまま: `bench/torihiki/bench.cljk:113` が
  `(bk/cancel! b oid)` (2 引数) を呼ぶ一方、`src/torihiki/book.cljk:542` の `cancel!`
  は owner 必須化済み (docstring に越権 cancel 脆弱性の経緯記載)。bench 実行は
  既定 5M tape でクラッシュする。

## スコア判定 (7 軸すべて変動なし)

| 軸 | score | 判定 |
|---|---|---|
| spec/契約 | 3 | 変動素材なし |
| 実装 | 3 | 変動素材なし (損壊は bench harness, engine 本体ではない) |
| テスト | 3 | 22:37–38 両ランタイム 357/915 全緑を再確認。fuzz suite 未接続のため 4 見送り |
| 反証 | 3 | fuzz byte-identical 6 度目。harness 常設化未達のため 4 見送り |
| 再現性 | 2 | seed 固定再現 6 度目追認。bench 3 条件は harness 損壊により不達のまま |
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
