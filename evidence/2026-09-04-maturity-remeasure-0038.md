# maturity re-measure — 2026-09-04 00:35–00:38 JST

cron rank iteration (コード変更なし, HEAD dd55c85)。前回 remeasure-0016 に続く追認 iteration。

## load gate

- 00:35 (iteration start): 14.19 / 14.66 / 15.16 — 15-min < 20 → 実行可

## 実測

1. JVM `kbb -M:test`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (00:36, evidence/test-jvm-0036.out)
2. nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass` (00:36, evidence/test-nbb-0036.out)
   → 両ランタイム同カウント **12 度目** の実測。
3. seeded fuzz 16 seeds, 両ランタイム:
   - JVM: `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
     → evidence/fuzz-jvm-0036.out (先頭行は REPL 返値 `#'fuzz-seeded/run`)
   - nbb: `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/fuzz-nbb-driver.cljk`
     → evidence/fuzz-nbb-0036.out
   - seed 行 diff (先頭の REPL 返値 1 行を除く) → **identical (JVM==NBB)**。
   - かつ 23:38 baseline (evidence/fuzz-nbb-2338.out) とも一致
     → seed 固定 digest byte-identical は **11 度目** の実測で成立。
4. bench `kbb -M:bench` (赤追認, evidence/bench-0036.out/.err — 3 実行目):
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112). Wrong number of args (2) passed to: torihiki.book/cancel!`
   → OPEN 赤 bench-tape-cancel-arity を 3 実行ベースで再確認。「低負荷 3 回安定」は赤修正後の条件なので不適用。

## スコア判定

全軸 00:16 版から変化なし (全実測が既存根拠を追認するのみ):

- テスト 3 維持: 12 度目の両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 11 度目。harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 11 度目だが bench 3 条件が OPEN 赤のため不達
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし
