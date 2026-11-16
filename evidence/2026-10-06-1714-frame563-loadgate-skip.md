# frame 563 — load gate skip (2026-10-06 17:14 JST)

- Gate 条件: 全 3 loadavg < 20。
- 実測 (script 2 回採取): 17:04 → 74.09 / 56.20 / 51.31、17:14 → **145.40 / 104.66 / 75.33**。いずれも全値 ≥ 20 で gate 不成立。
- 09-30 以降 sub-20 枠は継続して出現せず (frames 549/561/562 に続き frame 563 も skip)。
- 計測 (suite/parity/fuzz/bench の 1ceb357 全再計測) は実施せず、maturity.md スコアは据え置き。
- IN-FLIGHT (M fixed.cljk +36 / slab.cljk ±1 / maturity.md) は本 frame 時点で未解消のまま unchanged。
- NEXT 変更なし: ① 1ceb357 上での gate 枠待ち全再計測が最優先のまま。
