# bench iteration 2026-09-04 0924 — NOT RUN (host load > 20)

- 判定: host load 1-min **23.19** > 20 (5-min 25.96 / 15-min 23.29 も 20 超) → not-run evidence only (task rule)
- 実測時刻: 2026-09-04 09:24:02 +0900
- `uptime`: up 11 days, 1:11, 10 users, load averages: 23.19 25.96 23.29
- `sysctl vm.loadavg`: { 23.19 25.96 23.29 } / hw.ncpu = 10
- 事前スクリプト実測 (09:14): 25.89 23.88 20.28 — 3 平均すべて 20 超で持続。0924 時点も全平均 20 超
- 実行: `clojure -M:test` / `clojure -M:bench` は **未実行**。コード変更なし
- 前回計測値 (参考, 変更なし): test-0839 = 357 tests / 915 assertions 両 runtime 全緑 (31 度目), bench は bench-tape-cancel-arity で 0843 が 24 実行目の連続赤
