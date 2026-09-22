# frame 538 (2026-09-23 01:0x JST, cron) — genuine: suite 157 both runtimes PASS (same-count 61st, 357/915 0F/0E) / parity 54th both runtimes PASS (canonical roots) / fuzz digest 54th byte-identical / bench known-red repr (ArityException, repo 3-part fix unlanded). Scores unchanged 3/3/3/3/2/1/1 (falsifications 19 + f14b CLOSED, f8–f18 OPEN). No code changes.

- load gate passed: 01:06 direct **10.75 / 17.01 / 19.28** (all windows <20; pre-run 12.31/19.82/20.46 15-min just over, declining) → suite/parity/fuzz/bench 新規実測実施。全 run 完了時点で 01:11 負荷 38.96/27.39/23.11 に反発 (frame 535 の「着手時全 window <20 + 全 run EXIT=0 完走」前例の同一形状 — 測定はゲート通過時開始、完了は反発中)。
- HEAD **e819d694f05408851e4811432e0e7b4c9a109fc1** (git rev-parse 直測; frame 523 以来 16 枠連続不変), `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** → コード不変, frame-513/535 canonical 引用有効。
- measurement copy **/tmp/tori-f506** 再利用 (frame 536 で現存確認済; rename-only .cljk→.cljc, HEAD に対して code-identical)。

**suite 157 両 runtime PASS (same-count 61 度目)**:
- JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0 failures / 0 errors**, JVM_EXIT=0 (evidence/f538-test-jvm.{out,err,exit})。
- nbb: 2 段 classpath (stage-1 `kbb --backend sci --classpath "/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/text/src" script/nbb-classpath.cljc` EXIT=0 → stage-2 `NODE_PATH=…/chain/node_modules kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljc`) → **357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f538-test-nbb.{out,err,exit})。

**parity 54 度目 両 runtime 実測 PASS**:
- JVM `clojure -M:parity` PJ_EXIT=0 → **FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true** (frame-440/484/495/513/535 baseline 同一, evidence/f538-parity-jvm.{out,err})。
- nbb `AMU_HOME=…/orgs/kotoba-lang/amu KOTOBA_CHECKOUTS=…/orgs/kotoba-lang kbb --backend sci --classpath "text/src:$CP" script/kotoba-parity.cljc` → **fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass**, PN_EXIT=0 (evidence/f538-parity-nbb.{out,err})。pitfall 新確認: parity スクリプトは `kotoba.lang.text` を require するため nbb-classpath.cljc の出力 CP だけでは namespace 不解決 (PN_EXIT=1) — **text/src を CP 先頭 prepend が必須** (frame-513/535 は同じ 2 段構成、本枠で明示再確認)。

**fuzz digest 54 度目 byte-identical**:
- JVM `clojure -M -e "(load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"` FJ_EXIT=0 (2733 bytes = core 2715 + echo 行; evidence/f538-fuzz-jvm.out); nbb driver FN_EXIT=0 (2715 bytes; evidence/f538-fuzz-nbb.out) — **raw diff = JVM echo 行 (`#'fuzz-seeded/run`) 1 行のみ, core 2715 byte-identical** (evidence/f538-fuzzdiff.txt)。frame-513/535 baseline と同一形状。

**bench (repo 3-part fix 未着地) — known-red repr**:
- copy 上 `clojure -M:bench 1000000` → **BENCH_EXIT=1**, `ArityException at torihiki.bench/run-tape (bench.cljc:112) — Wrong number of args (2) passed to: torihiki.book/cancel!` (evidence/f538-bench.{out,err,exit})。repo bench/torihiki/bench.cljk:112 `(bk/cancel! b oid)` 2 引数、3-part fix (f15 owner + f16 arg-order + f17 ring-owner) 未着地不変 → 再現性 2 の条件 (HEAD 3× n≥1M cancelled>0) 未成立。

- 反証実施 **19 件** (f1〜f15 + f18 分解系等), **f14b multi-account deficit aggregation = CONFIRMED/CLOSED** (frame 535), f8–f18 OPEN (validate-i53-halt / cumulative-deposit-divergence / 累算 sum gates / bench cancel 系)。
- 発覚 0 件, 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**)。
- NEXT 未実測不変: validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 全 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp — 全てコード変更系, cron 禁止・インタラクティブ枠; 裏付け実測は f14b で完結) → 3-part bench fix 着地 → HEAD 3× n≥1M cancelled>0 → 再現性 3 → fuzz 常設化で テスト/反証 4。
- 次枠番号 = **539** (新規実測時 suite 158 / parity 55 / fuzz 55 / bench 42 実行目)。負荷は 01:11 に反発済み (38.96/27.39/23.11) だが 15-min は 23.11 と frame 537 の 24.38 から継続下降 — 次枠のゲート通過は引き続き現実的。
