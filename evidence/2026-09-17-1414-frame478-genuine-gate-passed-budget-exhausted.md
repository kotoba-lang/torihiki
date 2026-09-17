# Frame 478 — genuine gate passed, budget exhausted (no code changes, cron)

- 負荷 14:14 uptime 直測 1-min **11.52** / 5-min **13.83** / 15-min **13.64** 全 window <20 → gate 成立 (genuine 枠)
- 実測 budget 枯渇のため parity 46 度目を含む test/parity/fuzz/bench/falsify 新規実測 not-run (枠前半は torihiki_state.sh 空出力 fault の redirect 迂回 + 状態検収に費やした)
- HEAD **366321a** 直接実測 (frame 477 と同一), `git diff HEAD --stat -- src script deps.edn` = 0 行, working tree は maturity.md (M) + evidence のみで src/ 変更なし
- /tmp/tori-f451 現存確認 (bench/torihiki/bench.cljc 108–116 行実読: ring-owner 配列 + `(bk/cancel! b oid (aget ring-owner slot))` = f15+f16+f17 3-part fix 測定コピーのまま)
- ../text sibling checkout 現存 (frame-476 recipe の KOTOBA_CHECKOUTS / classpath prepend 対象)

正本引用 **frame-477 (2026-09-17 05:10, HEAD 366321a) 実測 base** 維持:
- bench **37 = first 3-run green series**: n=1M BENCH_EXIT=0 ×3, cancelled **153,767** 3 回同一, placed 450,908 (frames 451/453/455/460/461/464 deterministic tape と一致)
- suite **145** 両 runtime PASS + fuzz digest **40 度目** byte-identical (frame 476)
- parity **45** (FLAT ROOT b4322bed / STATE ROOT d1ebb9d3) — 46 度目は本枠未実施繰越

NEXT 未実測: parity 46 (copy に kotoba/ をコピーして実行 or in-repo; bare `kbb --backend sci script/nbb-classpath.cljk` は engine resolver regression で CP_BYTES=0 — gitlibs-text-src prepend 迂回 frame-476 recipe)。HEAD への 3-part bench fix 着地 (f15+f16+f17) → HEAD 3 実測 → 再現性 3。

発覚 0 件, 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。

次枠番号 = **479** (優先: parity 46 — kotoba/ コピーで解消, 新規実測時 fuzz 41 度目は frame 476 済のため parity のみ; bench fix 着地済みなら HEAD bench 3 実測)。
