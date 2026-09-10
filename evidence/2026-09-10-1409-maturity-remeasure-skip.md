# 2026-09-10 14:09 frame 410 maturity remeasure — load gate skip (no code changes, cron)

- 負荷 14:09 uptime 直測 1-min **36.70** / 5-min **29.43** / 15-min **26.41** — 全 window ≥20 で「全 <20」不成立のため test/parity/bench/falsify 新規実測は not-run。
- 前回 genuine 枠は **frame-406 (2026-09-10 01:06, HEAD d8fb6be)**: suite 357/915 両 runtime 38 度目一致 (suite 135), bench 赤 31 実行目。以後 skip 枠継続。
- NEXT 未実測のまま: frame-407 登録 hypothesis「複数アカウント deficit 合算集計経路 (liq/liquidate wrapper のアカウント横断 settle-deficit 集計 site) が (fnil + 0) sum 無検査なら第 7 累算 site」— 次の genuine 枠で実測予定。本枠は高負荷 skip のため新規 hypothesis なし, 発覚 0 件。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix (2 段構成)。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **136** / parity 39 / bench **96**)。
- 次枠番号 = **411**。
