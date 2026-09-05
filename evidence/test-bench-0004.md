# test + bench 実測 2026-09-04 00:19 JST (cron iteration)

## JVM test
`clojure -M:test` → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
緑。host load 12.09 (実行時) のため実測実施 (閾値 20 未満)。

## bench
`clojure -M:bench` → **赤のまま再現**。
ArityException at torihiki.bench/run-tape (bench.clj:112): Wrong number of args (2) passed to: torihiki.book/cancel!
既知の bench-tape-cancel-arity (maturity.md NEXT 項目)。5M tape 生成・ウォームアップ後 112 行目でクラッシュ。
stderr は evidence/bench-0004.err に保存。

## 所見
- 357/915 は前回実測 (2026-09-03 23:24–26) と同一カウントで引き続き全緑。
- 再現性 2→3 の条件 (bench 修正 + 低負荷 3 回安定) は未達: bench.clj:112 の cancel! 呼び出しが現行 owner 必須署名に未追従のまま。コード変更禁止の本 iteration では修正しない。
