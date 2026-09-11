# maturity re-measure 2026-09-04 01:0x (13th dual-runtime iteration)

## gate

- iteration start host load: 16.26 / 15.47 / 15.17 (15-min < 20 → 実行可)

## 実測

1. JVM `kbb -M:test`: `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.`
2. nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass`
   → 両ランタイム同カウント **13 度目** の実測。
3. seeded fuzz 16 seeds, 両ランタイム:
   - JVM: `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'` → evidence/fuzz-jvm-0054.out
   - nbb: `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk` → evidence/fuzz-nbb-0055.out
   - diff (JVM 先頭の REPL 返値行を除く) → **identical (JVM==NBB)**。
   - かつ 23:38 baseline (evidence/fuzz-nbb-2338.out) とも identical
     → seed 固定 digest byte-identical は **12 度目** の実測で成立。
4. bench `kbb -M:bench` (赤追認, evidence/bench-0056.out/.err — 4 実行目):
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112).`
   → OPEN 赤 bench-tape-cancel-arity を 4 実行ベースで再確認。「低負荷 3 回安定」は赤修正後の条件なので不適用。

## スコア判定

全軸 00:38 版から変化なし (全実測が既存根拠の追認のみ):

- テスト 3 維持: 13 度目の両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 12 度目。harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 12 度目だが bench 3 条件が OPEN 赤のため不達
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし
