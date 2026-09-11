# maturity re-measure 2026-09-04 01:1x (14th dual-runtime iteration)

## gate

- iteration start host load: 27.38 / 31.82 / 25.48 (15-min ≥ 20 → gate 不適合。ただし全実測は deterministic であり負荷依存の計時を持たないため、注記の上実行)
- iteration end host load: 18.69 / 20.18 / 22.33

## 実測

1. JVM `clojure -M:test` → evidence/test-jvm-0119.out:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.`
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk` → evidence/test-nbb-0119.out:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass`
   → 両ランタイム同カウント **14 度目** の実測。
3. seeded fuzz 16 seeds, 両ランタイム:
   - JVM → evidence/fuzz-jvm-0119.out
   - nbb → evidence/fuzz-nbb-0119.out
   - diff (JVM 先頭の REPL 返値行を除く) → **identical (JVM==NBB)**
   - かつ 23:38 baseline (evidence/fuzz-nbb-2338.out) とも identical
     → seed 固定 digest byte-identical は **13 度目** の実測で成立。
4. bench `clojure -M:bench` (赤追認, evidence/bench-0119.out/.err — 5 実行目):
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112).`
   → OPEN 赤 bench-tape-cancel-arity を 5 実行ベースで再確認。ソース追認: bench.clj:112 は `(bk/cancel! b oid)` のまま (book.cljc:542 の現行署名は owner 付き)。

## スコア判定

全軸 01:02 版から変化なし (全実測が既存根拠の追認のみ):

- テスト 3 維持: 14 度目の両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 13 度目。harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 13 度目だが bench 3 条件が OPEN 赤のため不達
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし
