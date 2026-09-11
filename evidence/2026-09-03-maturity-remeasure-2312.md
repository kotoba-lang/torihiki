# maturity re-measure — 2026-09-03 23:04–23:15 JST

cron rank iteration (コード変更なし, HEAD dd55c85)。

## load gate

- 23:04 (iteration start): 16.05 / 16.01 / 16.89 — 15-min < 20 → テスト/fuzz 実行可
- 23:10: 18.06 / 18.13 / 17.61 — まだ gate 内
- 23:12: 12.66 / 15.90 / 16.78 — bench は既知 OPEN 赤 (bench-tape-cancel-arity) の
  赤追認目的で 1 回のみ実行。「3 回安定」計測は赤修正後の条件なので不適用。

## 実測

1. JVM `clojure -M:test`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.` (23:09, evidence/test-jvm-2309.out)
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass` (23:12, evidence/test-nbb-2312.out)
   → 両ランタイム同日同カウント (9 度目の同日実測)。
   注: `nbb script/tests-on-nbb.cljk` を素で呼ぶと classpath が無く
   `Could not find namespace: torihiki.address-test` で落ちる (evidence/test-nbb-2309.out)。
   classpath は必ず `script/nbb-classpath.cljk` 経由で組むこと。
3. seeded fuzz 16 seeds (evidence/fuzz-seeded.cljk), 両ランタイム:
   - JVM: evidence/fuzz-jvm-2311.out / nbb: evidence/fuzz-nbb-2311.out
   - seed 行 diff → identical (JVM==NBB)。かつ 22:31 出力の seed 行とも一致
     → seed 固定 digest は **8 度目** の実測で成立
     (nbb 出力には REPL echo `#'fuzz-seeded/run` の 1 行が先頭に付くが seed digest 行は一致)。
4. bench `clojure -M:bench` (赤追認):
   `Wrong number of args (2) passed to: torihiki.book/cancel!` (23:15, evidence/bench-2315.err)
   → OPEN 赤 bench-tape-cancel-arity を実行ベースで再確認 (bench.clj:112, 廃止済み 2 引数署名)。

## スコア判定

全軸 22:57 版から変化なし (全実測が既存根拠を追認するのみ):

- テスト 3 維持: 9 度目の同日両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 8 度目。harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 8 度目だが bench 3 条件が OPEN 赤のため不達
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし
