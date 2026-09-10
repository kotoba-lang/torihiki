# 2026-09-10 0210 maturity remeasure skip — frame 407 load gate skip (no code changes, cron)

負荷 02:10 直測 uptime 1-min **24.39** / 5-min **13.91** / 15-min **13.59** — 1-min ≥20 で「全 window <20」不成立のため (前枠 01:06 frame-406 genuine 実測直後の上昇局面, JIT/実測直後スパイク型) test/parity/bench/falsify 新規実測は **not-run**。

- HEAD **d8fb6be** (frame 404/405/406 と同一), `git diff HEAD --stat -- src/ script/ deps.edn` = 0 行 (直接実測, redirect 迂回 — terminal backend 空出力 fault は今回は不発)。
- 正本引用: **frame-406 (2026-09-10 0106, HEAD d8fb6be) 実測 base** — suite **135** (両 runtime 357/915 0F/0E 同一カウント 38 度目, nbb は 2 段 classpath `nbb --classpath "$(nbb --classpath '../text/src' script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`), fuzz digest 32 度目, bench 赤累計 **31 実行目** (bench-406.err, bench.clj:112 cancel! 2-arg ArityException)。frame-406 の evidence (2026-09-10-0106-maturity-remeasure-frame406.md) は本枠で maturity.md REMEASURE LOG に正式記録。
- 本枠の falsify hypothesis (not-run のため未実測): **複数アカウント deficit 合算の集計経路** — `liq/liquidate` wrapper が touched account 各々に settle-deficit を回す (liquidation.cljc:252) 際、アカウント横断の deficit 集計/合算 site が sum 無検査で 2^53+1 crossing するか (falsify-12 の単一アカウント k3 分岐の複数アカウント版)。falsify-9/12 と同一クラス想定だが、集計経路が per-account map fold であれば (fnil + 0) 無検査累算の第 7 site になり得る。負荷 gate 成立後の次の genuine 枠で実測。
- 発覚 0 件 (not-run 枠), 新規 hypothesis 1 件 (上記, 未実測)。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **136** / parity 39 / bench **96**)。
- 次枠番号 = **408**。
