# Frame 480 — genuine gate passed, budget exhausted (no code changes, cron)

- 負荷 17:09 uptime 直測 1-min **15.20** / 5-min **15.47** / 15-min **18.24** 全 window <20 → gate 成立 (genuine 枠)。pre-run torihiki_state.sh stdout 空 (既知 fault) のため uptime/git 直叩き + redirect 迂回。
- 実測 budget 枯渇のため parity 47 / suite 146 / fuzz 41 / bench 新規実施なし (枠内 work: 状態検収のみ)。
- HEAD **366321a** 直接実測 (frame 477/478 と同一), working tree = maturity.md (M) + evidence のみで src/ 変更なし。
- /tmp/tori-f451 現存確認 (bench/deps.edn/script/src/test/evidence あり)。ただし **kotoba/ なし** — frame 476 検収どおり parity legs は copy 内で :project-link-failed、kotoba/ コピー or in-repo 実行が前提。../text sibling checkout 現存 (frame-476 recipe の KOTOBA_CHECKOUTS / gitlibs-text-src prepend 対象)。
- 正本引用 **frame-477 (2026-09-17 05:10, HEAD 366321a) 実測 base** 維持:
  - bench **37 = first 3-run green series**: n=1M BENCH_EXIT=0 ×3, cancelled **153,767** 3 回同一, placed 450,908 (frames 451/453/455/460/461/464 deterministic tape と一致)。repo bench/torihiki/bench.cljk:112 は未だ 2-arg、3-part fix (f15+f16+f17) 未着地のため再現性 3 は未 claim。
  - suite **145** 両 runtime PASS (frame 476 evidence/f476-test-{jvm,nbb}.out, 357/915 0F/0E) + fuzz digest **40 度目** byte-identical。
  - parity **45** (FLAT ROOT b4322bed / STATE ROOT d1ebb9d3)。
- 発覚 0 件, 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- NEXT 未実測: parity **47** 相当 (46 は frame 478 枠で未実施繰越 → 次回 genuine 枠で parity 実行; copy に kotoba/ コピー or in-repo + frame-476 nbb recipe)。HEAD への 3-part bench fix 着地 (f15+f16+f17) → HEAD 3 実測 → 再現性 3。
- 次枠番号 = **481** (優先: parity + HEAD bench fix 着地済みなら HEAD base 3 実測)。
