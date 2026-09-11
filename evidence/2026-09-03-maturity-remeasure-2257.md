# maturity re-measure — 2026-09-03 22:49–22:57 JST

cron rank iteration (コード変更なし)。

## load gate

- 22:50 (iteration start): 10.73 / 16.02 / 17.66 — 15-min < 20 → テスト/fuzz 実行可
- 22:57: 14.43 / 16.68 / 17.58 — bench は本来低負荷条件 (3 回安定) の対象だが、
  既知の OPEN 赤 bench-tape-cancel-arity の赤追認目的のため 1 回のみ実行 (gate の
  「3 回安定」計測は赤修正後に実施するものなので本 iter では不適用)。

## 実測

1. JVM `kbb -M:test`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (22:50, evidence/test-jvm-2250.out)
2. nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass`
   (evidence/test-nbb-2251.out) → 両ランタイム同日同カウント (8 度目の同日実測)。
3. seeded fuzz 16 seeds (evidence/fuzz-seeded.cljk), 両ランタイム:
   - JVM: `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
     → evidence/fuzz-jvm-2257.out
   - nbb: `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" -e "(require '[nbb.core :refer [load-file]]) (load-file \"evidence/fuzz-seeded.cljk\") (fuzz-seeded/run)"`
     → evidence/fuzz-nbb-2257.out
   - diff → byte-identical (JVM==NBB)。かつ 22:31 / 21:53 出力の seed 行とも一致
     → seed 固定 digest byte-identical は **7 度目** の実測で成立。
4. bench `kbb -M:bench` (赤追認):
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112).`
   `Wrong number of args (2) passed to: torihiki.book/cancel!`
   (evidence/bench-2257.err / bench-2257.out) → OPEN 赤 bench-tape-cancel-arity を
   実行ベースで再確認。22:39 の bench-tape-cancel-arity 記録と同一。

## スコア判定

全軸 22:38 版から変化なし (全実測が既存根拠を追認するのみ):

- テスト 3 維持: 8 度目の同日両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 7 度目。harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 7 度目だが bench 3 条件が OPEN 赤のため不達
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし
