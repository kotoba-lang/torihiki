# 2026-09-11 06:14 frame 414 — not-run (負荷 gate 一時閾値超過 + run budget 枯渇, no code changes)

## 負荷 gate 実測 (本枠, /tmp/tori_cron_uptime.txt + /tmp/tori_diag.txt + /tmp/tori_diag2.txt)

| 時刻 | 1-min | 5-min | 15-min | 判定 |
|---|---|---|---|---|
| 06:14 (cron 起動直後) | 4.55 | 10.53 | 12.97 | <20 成立 |
| 06:17:42 | **20.98** | 12.77 | 13.25 | 1-min >20 |
| 06:18 (probe 1/4) | **22.66** | 13.69 | 13.57 | 1-min >20 |
| 06:18 (probe 2/4) | **27.89** | 14.92 | 14.01 | 1-min >20 |
| 06:18 (probe 3/4) | **28.62** | 15.29 | 14.14 | 1-min >20 |
| 06:18 (probe 4/4) | **29.77** | 15.75 | 14.31 | 1-min >20, 上昇中 |
| 06:26 (probe 1/4) | 7.35 | 13.62 | 14.88 | <20 復帰 |
| 06:26 (probe 2/4) | 7.08 | 13.46 | 14.81 | <20 復帰 |
| 06:26 (probe 3/4) | 7.97 | 13.45 | 14.79 | <20 復帰 |
| 06:26 (probe 4/4) | 7.28 | 13.12 | 14.66 | <20 復帰 |

## 判定

- **not-run**: 06:17–06:18 に 1-min が 20.98→29.77 へ一時超過 (上昇トレンド)。テスト起動時点の gate 不成立につき suite/bench/fuzz 起動は実施せず。06:26 に 1-min 7.0–8.0 へ復帰 (全 window <20) したが、その時点で run budget 枯渇につき起動不能。
- spike の推定原因: PS 実測で 95%-CPU の temurin JVM (PID 62802, elapsed 01-14:23, torihiki runner 由来の可能性) + mediaanalysisd 10%-CPU 56% 等。torihiki 実行プロセス (clojure/nbb) は無し — 412 runner 完了済みと整合。
- 発見 0 件。HEAD **d2b0407** 不変 (git rev-parse 実測 06:18)。untracked は frame-412/413 evidence のみ (412 収納済み, 413 collection は未commit)。src diff なし。
- スコア 7 軸すべて変更なし **3/3/3/3/2/1/1**。正本採用先例に従い frame-412 分 (suite **136** / parity 39 度目 / bench-tape-cancel-arity 32 実行目 / fuzz digest 32 度目のまま) が最新。
- 残作業不変: validate-i53-halt fix パッケージ (累算 sum gate + balance-domain gate + notional 上限 + fx/mul-rate 積 overflow + REDUCING 積事前界限 + rate 上限) / falsify-14 候補 (複数アカウント deficit 合算集計経路) / bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) / fuzz 常設化 + fuzz driver 起動規約 harness 統一 / nbb-classpath bootstrap fix (2 段構成)。
- 次枠 genuine 実測時: suite **137** / parity **40** / bench-tape-cancel-arity **33 実行目** 相当; falsify-14 実測優先。
