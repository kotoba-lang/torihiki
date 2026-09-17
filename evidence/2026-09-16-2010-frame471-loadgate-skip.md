# 2026-09-16 2010 frame 471 load gate skip (no code changes, cron)

負荷 pre-run 20:09 (script block) 1-min **29.86** / 5-min **30.46** / 15-min **45.29**, 直測 20:10:07 **34.30** / **31.72** / **44.44**, 再直測 20:10:52 **34.04** / **31.71** / **44.36** — 3 測定 (pre-run + 2 direct) すべて全 window ≥20 で「全 window <20」不成立のため test/parity/fuzz/bench/falsify 新規実測 not-run。

- terminal 空出力 fault (既知高負荷型) のため redirect + read_file 迂回実測 (/tmp/tori471-check.txt, /tmp/tori471-check2.txt)
- HEAD **56a4ad0c** 直測 (20:10) — frame-470 と同一。`git diff HEAD --stat -- src/ script/ deps.edn` = 0 行, working tree は maturity.md (M) + frame 467–470 evidence のみで src/ 変更なし, /tmp/tori-f451 copy 現存想定 (次 genuine 枠で再確認)
- 正本引用 **frame-464 (2026-09-16 0906, HEAD 0a99c9c2) 実測 base** 維持: suite **144** (same-count 50 度目, 357/915 0F/0E 両 runtime) / parity **45** (FLAT ROOT b4322bed / STATE ROOT d1ebb9d3) / fuzz digest **38** byte-identical / bench f451 copy n=1M BENCH_EXIT=0 cancelled=153,767 3x stable — HEAD 0a99c9c2→56a4ad0c は src/script/deps.edn 不変 (frame-467 直接実測 + 本枠 diff 0 行) のためコード同一として引用有効
- NEXT 未実測 0 件変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)

次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **145** / parity **46** / fuzz **39** / bench **37 実行目**)。次枠番号 = **472**。
