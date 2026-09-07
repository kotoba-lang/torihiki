# bench iteration 2026-09-04 0823 — NOT RUN (host load > 20)

- 判定: host load 1-min **22.68** > 20 → not-run evidence only (task rule)
- 実測時刻: 2026-09-04 08:23:44 +0900
- `uptime`: up 11 days, 12 mins, 11 users, load averages: 22.68 19.99 22.68
- `sysctl vm.loadavg`: { 22.68 19.99 22.68 } / hw.ncpu = 10
- 事前スクリプト実測 (08:14): 19.54 25.00 26.93 — 5/15-min が 20 超で持続。0823 時点で 1-min も 20 超に再上昇
- 実行: `clojure -M:test` / `clojure -M:bench` は **未実行**。コード変更なし
- 前回計測値 (参考, 変更なし): test-0712 = 357 tests / 915 assertions 両 runtime 全緑 (30 度目), bench は bench-tape-cancel-arity で 0757 が 23 実行目の連続赤
