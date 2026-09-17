# 2026-09-16 1712 frame 469 load gate skip (no code changes, cron)

負荷 pre-run 17:04 (script block) 1-min **123.22** / 5-min **98.13** / 15-min **84.18**, 直測 17:11 **72.86** / **80.64** / **81.64** — 全 window ≥20 で「全 window <20」不成立のため test/parity/fuzz/bench/falsify 新規実測 not-run。

- terminal 空出力 fault (既知高負荷型, `bash torihiki_state.sh` / `echo` も stdout 空) のため redirect + read_file 迂回実測 (/tmp/tori469-state.txt)
- HEAD **56a4ad0c** 直測 (17:11, frame-467 と同一) — `git diff HEAD --stat -- src/ script/ deps.edn` = 0 行, working tree は maturity.md (M) + frame 467/468 evidence のみで src/ 変更なし, /tmp/tori-f451 copy 現存確認済 (次 genuine 枠で再利用可)
- **OPEN 赤引用行 10 site を 56a4ad0c で直接行読み再検収** — api.cljk:68 (:order qty integer?/pos? のみ) / api.cljk:202 (:deposit :bad-amount i53 なし) / clearing.cljk:157 (REDUCING `(* entry-notional closed)` 無検査) / clearing.cljk:410・560 (:fees-collected (fnil + 0)) / clearing.cljk:711 (:deficit (fnil + 0)) / clearing.cljk:726 (:deposit collateral (fnil + 0)) / bench.cljk:112 (2-arg cancel!) は全文一致で不シフト, funding.cljk (140 行, :138 site 存在) と commit.cljk (175 行, :113/:115 site 存在) は行数整合まで確認 (該当行の詰め読みは budget 未達) — 全引用有効
- **前枠 frame-468 (14:09 skip) の REMEASURE LOG 未登録を検出 → 本枠で追認登録** (evidence/2026-09-16-1409-frame468-loadgate-skip.md + skip-check-0916-1409.txt: 負荷 28.36/46.81/58.06 全 ≥20, terminal fault, 正本引用 frame-464 base 維持, スコア不変)
- 正本引用 **frame-464 (2026-09-16 0906, HEAD 0a99c9c2) 実測 base** 維持: suite **144** (same-count 50 度目, 357/915 0F/0E 両 runtime) / parity **45** (FLAT ROOT b4322bed / STATE ROOT d1ebb9d3) / fuzz digest **38** byte-identical / bench f451 copy n=1M BENCH_EXIT=0 cancelled=153,767 3x stable — HEAD 0a99c9c2→56a4ad0c は src/script/deps.edn 不変 (frame-467 直接実測 + 本枠 diff 0 行) のためコード同一として引用有効
- NEXT 未実測 0 件変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)

次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **145** / parity **46** / fuzz **39** / bench **37 実行目**)。次枠番号 = **470**。
