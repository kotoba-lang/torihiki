# maturity re-measure — 2026-09-03 23:24–23:31 JST

cron rank iteration (コード変更なし, HEAD dd55c85)。前回 remeasure-2312 に続く追認 iteration。

## load gate

- 23:24 (iteration start): 17.39 / 18.64 / 17.70 — 15-min < 20 → 実行可
- 23:31 (bench 実行後): 13.04 / 15.73 / 16.64

## 実測

1. JVM `kbb -M:test`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (23:24, evidence/test-jvm-2324.out)
2. nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass` (23:26, evidence/test-nbb-2326.out)
   → 両ランタイム同日同カウント **10 度目** の実測。
3. seeded fuzz 16 seeds (evidence/fuzz-seeded.cljk), 両ランタイム:
   - JVM: `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
     → evidence/fuzz-jvm-2328.out
   - nbb: **load-file は nbb で unresolved** (evidence/fuzz-nbb-2329.out, 2330/2331/2333/2336/2337 の試行錯誤:
     load-file 未定義 / .cljc require 不可 / quote 引数数エラー / fs は文字列形 `["fs" :as fs]` で require)。
     正解は fuzz80-driver.cljs と同型の pre-require + load-string:
     `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
     → evidence/fuzz-nbb-2338.out (23:38)。driver を **evidence/fuzz-nbb-driver.cljk として常置化**。
   - seed 行 diff → identical (JVM==NBB)。かつ 2311 baseline (8 度目) の seed 行とも一致
     → seed 固定 digest byte-identical は **9 度目** の実測で成立。
4. bench `kbb -M:bench` (赤追認, 1 回のみ):
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112). Wrong number of args (2) passed to: torihiki.book/cancel!`
   (23:31, evidence/bench-2340.err) → OPEN 赤 bench-tape-cancel-arity を実行ベースで再確認。
   「低負荷 3 回安定」は赤修正後の条件なので不適用。

## スコア判定

全軸 23:12 版から変化なし (全実測が既存根拠を追認するのみ):

- テスト 3 維持: 10 度目の同日両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 9 度目。harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 9 度目だが bench 3 条件が OPEN 赤のため不達
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし

## 追記 (harness 知見)

- nbb 16-seed fuzz driver を `evidence/fuzz-nbb-driver.cljk` として常置化した。
  nbb は `load-file` を持たないため JVM と同じ 1 ライナーは使えない。driver は
  `["fs" :as fs]` の文字列形 require → torihiki.state/book/snapshot の pre-require →
  load-string、という fuzz80-driver.cljs と同じ構成。
