# frame 561 (2026-09-30 14:4x, cron) — load gate skip (no code changes by runner)

- 負荷: 14:23 (pre-run script) **38.14 / 46.57 / 48.02**, 14:48 直測 (uptime) **24.85 / 35.87 / 45.47** — 両測定とも全 window ≥20 (低下局面だが 1-min でも 24.85 ≥20) で「全 window <20」不成立 → test/parity/bench/fuzz/falsify 新規実測 not-run。genuine-lite 前例 (frame-397/401: 低下局面 1-min <20) の適用条件も満たさず、厳密 skip。
- terminal 前面呼び出し空出力 fault (既知 chronic) — redirect→read_file 迂回実測。
- **HEAD 移行を直接実測: 1ceb357233c528c0d40409cb6d4a6f69ed1864ea** (2026-09-25 11:23, "Merge kotoba-migrate-all-20260925") — 正本凍結 base 92b9fc9 から逸脱。`92b9fc9..1ceb357` の src/script/bench 変更 6 commit 実測: ebe8f4f/b06a3a8 (clojure.*/java.* codemod), e0edcef (clojure.edn→kotoba.lang.edn), d3ebe68 (clojure.test→kotoba.test 20 file), c2dd13b (**the order book, native, without a JVM**), 66c206f (**bench: book bench runs again**)。→ 正本実測 base (frame-548: suite 162 / parity 59 / fuzz 59) は 1ceb357 に対し**引用不能 (コード不変性破れ)** — 次 genuine 枠は 1ceb357 上での全 gate 再確立 (suite 163 リセットではなく再計測系の再起点) が必要。
- IN-FLIGHT 未commit 実測: `git diff --stat -- src/` = `src/torihiki/fixed.cljk +36 / src/torihiki/slab.cljk 1` (M x 2, 現存) — 累算/固定小数系への未着地変更があるため, 次枠の判定前に landing または revert 確認必須。
- 発覚 0 件 (実測なき枠), 新規 hypothesis 0 件。スコア 7 軸は frame-548 base 引用のまま変更なし (**3/3/3/3/2/1/1**)。
- 残作業不変: validate-i53-halt fix パッケージ (accum 6 site + commit aggregate + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) + bench 3-part fix 着地 + HEAD 3× n=1M → 再現性 3 + fuzz 常設化 + **新規: 1ceb357 移行後の全 gate 再起点 + nbb 呼び出し規約の kotoba.test 移行再検収**。
- 次枠番号 = **562**。枠記録: evidence/2026-09-30-1448-frame561-host-load-notrun.md。
