# frame 540 (2026-09-23 05:1x JST, cron) — genuine: suite 158 both runtimes PASS (same-count 62nd, 357/915 0F/0E) / parity 55th both runtimes PASS (canonical roots) / fuzz digest 55th byte-identical / bench 42nd run known-red (ArityException, repo 3-part fix unlanded). Scores unchanged 3/3/3/3/2/1/1 (falsifications 19, f14b CLOSED, f8–f18 OPEN). No code changes.

> 並行重複枠注記: 同一 frame 540 に 2 件の並行 runner が実測・記録 (本記録 = 05:17 系, 他記録 = evidence/2026-09-23-0519-frame540-genuine-suite158-parity55-fuzz55.md 05:31 系 — REMEASURE LOG 登録済の正本). 両記録の計測値 (suite 158 / 62 度目, parity 55, fuzz 55, bench 42 実行目, f540-* evidence 共有) は完全一致. frame-384/385 前例に倣い両記録を併存.

- load gate passed: pre-run 05:04 (cron script block) **15.51/11.75/11.81** → 2nd read **14.41/11.65/11.77** → direct 05:07 **11.26/10.60/11.17** (all 3 reads all windows <20, declining) → suite/parity/fuzz/bench 新規実測実施. 完了時 05:17 負荷 15.19/17.60/15.49 に反発 (frame 538 と同一形状: 開始時 <20 + 全 run EXIT=0 完走).
- HEAD **92b9fc9211e6583d38627856f6e482af84bfd2a6** (git rev-parse 直測) — frame 539 skip 記録時 (02:10) の e819d69 から移行: repo-bot-drain :landed merge (3d8827a maturity ledger frame 538 + evidence 537–539). **`git diff e819d69..92b9fc9 --stat -- src/ script/ deps.edn` = 0 bytes → コード不変**, frame-513/535/538 canonical 引用有効. `git diff HEAD --stat -- src/ script/ deps.edn` = 0 bytes (worktree clean on code).
- measurement copy **/tmp/tori-f506** 再利用 + 新規検証: repo tracked `.cljk` 26 件 + deps.edn を逐次 cmp で新 HEAD 92b9fc9 に対して照合 → **COPY-CODE-IDENTICAL** (rename-only .cljk→.cljc, 内容 byte-identical). 26 files, 0 mismatch, 0 missing.

**suite 158 両 runtime PASS (same-count 62 度目)**:
- JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0 failures / 0 errors**, JVM_EXIT=0 (evidence/f540-test-jvm.{out,err,exit}).
- nbb: 2 段 classpath (stage-1 `kbb --backend sci --classpath "/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/text/src" script/nbb-classpath.cljc` CP_EXIT=0 CP_LEN=333 → stage-2 `NODE_PATH=/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/chain/node_modules kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljc`) → **357/915 0F/0E, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f540-test-nbb.{out,err,exit}).

**parity 55 度目 両 runtime 実測 PASS**:
- JVM `clojure -M:parity` PJ_EXIT=0 → **FLAT ROOT b4322bedd406112e6c2c7339e93d646eb7852959def143901961b3be4f43445a / STATE ROOT d1ebb9d30cd51516c14bc9e3c0007d806b423e33cd3bd64200362c7e7e3aed7f / PROOF a 10 verifies true** (frame-440/484/495/513/535/538 baseline 同一, evidence/f540-parity-jvm.{out,err,exit}).
- nbb `AMU_HOME=/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/amu KOTOBA_CHECKOUTS=/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang kbb --backend sci --classpath "text/src:$CP" script/kotoba-parity.cljc` → **fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass**, PN_EXIT=0 (evidence/f540-parity-nbb.{out,err,exit}).

**fuzz digest 55 度目 byte-identical**:
- JVM `clojure -M -e '(load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run)'` FJ_EXIT=0 (2733 bytes = core 2715 + echo 行 `#'fuzz-seeded/run`; evidence/f540-fuzz-jvm.out); nbb driver `kbb --backend sci --classpath "$CP" evidence/fuzz-nbb-driver.cljc` FN_EXIT=0 (2715 bytes; evidence/f540-fuzz-nbb.out) — **raw diff = JVM echo 行 1 行のみ (24 bytes, f538 と同一形状), core 2715 byte-identical** (evidence/f540-fuzzdiff.txt). かつ f538 baseline (f538-fuzz-nbb.out = core 2715) とも diff 空.

**bench 42 実行目 (repo 3-part fix 未着地) — known-red repr**:
- copy 上 `clojure -M:bench 1000000` → **BENCH_EXIT=1**, `ArityException at torihiki.bench/run-tape (bench.cljc:112) — Wrong number of args (2) passed to: torihiki.book/cancel!` (evidence/f540-bench.{out,err,exit}). repo bench/torihiki/bench.cljk:112 `(bk/cancel! b oid)` 2 引数、3-part fix (f15 owner + f16 arg-order + f17 ring-owner) 未着地不変 → 再現性 2 の条件 (HEAD 3× n≥1M cancelled>0) 未成立.
- 備考: copy 側 script 名は rename 済 `.cljc` (nbb-classpath.cljc / tests-on-nbb.cljc 等) — 本枠で素 `.cljk` 名指定を 1 回誤用し ENOENT (frame 513/535 規約の再確認).

- 反証実施 **19 件** (f1〜f15 + f18 分解系等), **f14b multi-account deficit aggregation = CONFIRMED/CLOSED** (frame 535), f8–f18 OPEN (validate-i53-halt / cumulative-deposit-divergence / 累算 sum gates / bench cancel 系).
- 発覚 0 件, 新規 hypothesis 0 件. スコア 7 軸変更なし (**3/3/3/3/2/1/1**).
- NEXT 未実測不変: validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 全 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp — 全てコード変更系, cron 禁止・インタラクティブ枠; 裏付け実測は f14b で完結) → 3-part bench fix 着地 → HEAD 3× n≥1M cancelled>0 → 再現性 3 → fuzz 常設化で テスト/反証 4.
- 枠番号注: frame 538 (01:0x genuine) → frame 539 (02:10 load gate skip, 2026-09-23-0210-frame539-loadgate-skip.md) → 本枠 **540**.
- 次枠番号 = **541** (新規実測時 suite 159 / parity 56 / fuzz 56 / bench 43 実行目).
