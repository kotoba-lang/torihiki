# frame 509 (2026-09-20 00:1x, cron) — genuine run: suite 152 both runtimes PASS / fuzz 49th byte-identical / parity+bench not-run (budget)

- 負荷ゲート (00:16 直測, terminal 空出力 fault 既知のため uptime 直叩き + redirect→read_file 迂回): pre-run 16.33/17.33/19.80 → 直測 17.52/17.50/19.79 — 全 window <20 で gate 成立 (15-min は 19.79 でギリギリ下回る; 2 測定整合)。
- HEAD **694e2ac7** (00:16 直測, frame 503/505/506/507/508 と同一), `git diff HEAD --stat -- src/ script/ deps.edn` = 0 bytes (空ファイル直接実測), working tree = maturity.md (M) + evidence のみ (porcelain 213 行 untracked 全部 evidence/skip 記録, src/ 変更なし)。
- 測定 copy **/tmp/tori-f506 現存**を再利用 (frame 507/508 測定コピー; kotoba/ 同梱, script 5 ファイル, evidence/fuzz-seeded.{cljc,cljk} 複置済 + fuzz-nbb-driver.cljc, bench/torihiki/bench.cljc:112 は 2 引数のまま (3-part fix 未着地, bench 不可))。
- **suite 152 両 runtime PASS (same-count 56 度目)**:
  - JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0 failures / 0 errors**, JVM_EXIT=0 (evidence/f510-test-jvm.{out,err,exit})
  - nbb: 2 段 classpath (stage-1 `kbb --backend sci --classpath "/Users/…/orgs/kotoba-lang/text/src" script/nbb-classpath.cljc` CP_EXIT=0 CP_LEN=334 [text 73bdb13a 由来 .nbb-deps prepend] → stage-2 `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljc` + NODE_PATH=chain/node_modules) → **357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f510-test-nbb.{out,err,exit})
- **fuzz digest 49 度目 byte-identical**: JVM `clojure -M -e "(load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"` FJ_EXIT=0 (2733 bytes = echo 行 1 行分差のみ) vs nbb driver FN_EXIT=0 (2715 bytes) — seed 0–15 全 digest 行一致, 差分は JVM 側 echo 行のみ (baseline 形状, f488/f506 baseline と同 2715 bytes 構成)。
- parity / bench not-run (budget 枯渇)。bench は repo copy bench.cljc:112 2 引数のまま 3-part fix (f15+f16+f17) 未着地のため引き続き不可 — bench 39 実行目繰越。
- 発見 1 件 (環境系, 迂回メモ): stage-1 classpath 生成に相対 `../text/src` は copy (workdir /tmp/tori-f506) からは /tmp/text を指して落ちる (CP1_EXIT=1 kotoba.lang.text 不解決, /tmp/tori_cp_0920.err) — **絶対パス指定 `/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/text/src` で解決** (frame 507 規約の copy 上での具体化; 以後の枠は絶対パスを使用)。
- 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- NEXT 未実測: 3-part bench fix (f15 owner wiring + f16 arg-order + f17 ring-owner) HEAD 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。
- 次枠番号 = **510** (新規実測時 suite 153 / parity 53 / fuzz 50 / bench 39 実行目)。
