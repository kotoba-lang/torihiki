# test + bench 実測 2026-09-04 00:46 JST (cron iteration)

## JVM test
`kbb -M:test` → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
緑。host load 15.30 (開始時) / 16.39 (終了時) のため実測実施 (閾値 20 未満)。所要 34s。
前回 cron iteration (2026-09-04 00:19) および remeasure-0038 (00:36) と同一カウントで引き続き全緑。

## bench
`kbb -M:bench` → **赤のまま再現 (4 度目の再確認)**。
ArityException at torihiki.bench/run-tape (bench.clj:112): Wrong number of args (2) passed to: torihiki.book/cancel!
既知の bench-tape-cancel-arity (maturity.md OPEN 赤)。5M tape 生成・JIT warmup 後 112 行目でクラッシュし、throughput は計測不能。所要 13s。
stderr は evidence/bench-0046.err に保存。

## 所見
- 357/915 は本日 6 度目の同一カウント実測で全緑を維持。
- 再現性 2→3 の条件 (bench 修正 + 低負荷 3 回安定) は未達のまま: bench.clj:112 の cancel! 呼び出しが現行 owner 必須署名に未追従。コード変更禁止の本 iteration では修正しない。
