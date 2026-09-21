# frame 512 (2026-09-20 06:1x, cron) — genuine: suite 154 both runtimes PASS (same-count 58th) / fuzz 51st byte-identical / parity+bench not-run (budget)

- 負荷ゲート (06:14–06:15 直測, terminal 空出力 fault 既知のため redirect→read_file 迂回): 06:14 **9.22/9.93/10.92**, 06:15 **8.09/9.61/10.76** — 全 window <20 で gate pass (frame-511 の低下局面が完了した形, 05:30 の 18.99/18.78/21.60 から全 window 低下)。
- HEAD **694e2ac** (frame 503/505–511 と同一), `git diff HEAD --stat -- src/ script/ deps.edn` = 0 bytes (直接実測, 空出力 = 差分なし)。working tree = maturity.md (M) + evidence のみ, src/ 変更なし。
- 測定 copy **/tmp/tori-f506 現存**を再利用 (frame 507–511 測定コピー; bench.cljc:112 2 引数のまま)。
- **suite 154 両 runtime PASS (same-count 58 度目)**:
  - JVM: copy 上 `clojure -M:test` (/opt/homebrew/bin/clojure — /usr/local/bin には無い点に注意, 今枠で 1 回 PATH ミス後成功) → **357 tests / 915 assertions / 0 failures / 0 errors**, JVM_EXIT=0 (evidence/f512-test-jvm.{out,err,exit})
  - nbb: 2 段 classpath (stage-1 `kbb --backend sci --classpath "<org 絶対>/text/src" script/nbb-classpath.cljc` EXIT=0, CP=334 bytes → stage-2 `NODE_PATH=<org 絶対>/chain/node_modules kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljc`) → **357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f512-test-nbb.{out,err,exit}, cp は f512-cp.{out,err,exit})
- **fuzz digest 51 度目 byte-identical**: JVM `clojure -M -e "(load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"` FJ_EXIT=0 (2733 bytes = echo 行のみ差) vs nbb driver `kbb --backend sci --classpath "$CP" evidence/fuzz-nbb-driver.cljc` FN_EXIT=0 (2715 bytes) — raw diff は JVM echo 行 `#'fuzz-seeded/run` のみ (baseline 形状), nbb 出力は f510 baseline と diff 空 (evidence/f512-fuzz-{jvm,nbb}.{out,err} + f512-fuzzdiff.{txt,exit} + f512-vs510-baseline-diff.txt)。
  - **発見 1 件 (環境系, fuzz 枠の NODE_PATH)**: fuzz-nbb 枠の初回実行は classpath 指定済みでも **NODE_PATH 未設定だと `Could not find namespace: torihiki.state` で EXIT=1** (evidence は /tmp/cron20_f512_fuzznbb.out 破棄, 再実行で PASS)。frame-510 の「suite 枠 NODE_PATH 絶対パス必須」は **fuzz 枠にも同様に適用** — kbb classpath 引数と NODE_PATH は別経路 (classpath は clojure ソース解決, NODE_PATH は @noble/hashes 等 node モジュール解決)。以後 nbb 経由の全実行 (suite/fuzz/parity) は classpath + 絶対 NODE_PATH の 2 点セット。
- parity / bench not-run (budget 枯渇)。bench は repo bench/torihiki/bench.cljk:112 2 引数のまま 3-part fix (f15 owner wiring + f16 arg-order + f17 ring-owner) 未着地のため引き続き不可 — bench 39 実行目繰越。
- 発見 1 件 (環境系 NODE_PATH fuzz 枠, 上記)。新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- NEXT 未実測: 3-part bench fix HEAD 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。
- 次枠番号 = **513** (新規実測時 suite 155 / parity 52 / fuzz 52 / bench 39 実行目)。
