# frame 541 (2026-09-23 06:2x, cron) — genuine run (no code changes)

## 負荷ゲート
- pre-run 06:15 (cron script block) load averages **8.77/9.19/8.98** 全 window <20 → gate 成立
- runner 開始 06:26:55 直測 **6.57/6.90/7.72**, 完了 06:27:22 **7.11/7.01/7.73** — 全 window <20 (frame 538 と同一形状: 開始時 <20 + 全 run EXIT=0 完走)

## HEAD / コード不変
- HEAD **92b9fc9** (frame 540 以来不変, repo-bot-drain :landed merge 92b9fc9 = ledger/evidence のみ)
- 測定 copy **/tmp/tori-f506** 再利用 + 本枠で全量再検証: src 22 件 + test 17 件 + bench 4 件 + script 4 件 (.cljk→.cljc rename 対応) + deps.edn + evidence/fuzz-seeded.cljk + evidence/fuzz-nbb-driver.cljk を逐次 shasum 照合 → **全 byte-identical, 0 mismatch, 0 missing** (verify スクリプト /tmp/tori-f541-verify{,2,3}.sh)
- `git diff HEAD --stat -- src/ script/ bench/ deps.edn` = 0 bytes (worktree clean on code)
- 枠番号: frame 540 = 2026-09-23 05:1x genuine (0517/0519 両記録, 並行重複) → 本枠 = **541**

## 実測 (全て EXIT 確認)
- **suite 159 両 runtime PASS (same-count 63 度目)**:
  - JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0F / 0E**, JVM_EXIT=0 (evidence/f541-test-jvm.*)
  - nbb: 2 段 classpath (stage-1 text/src prepend + script/nbb-classpath.cljc, CP_EXIT=0 CP_LEN=333; stage-2 NODE_PATH=chain/node_modules + script/tests-on-nbb.cljc) → **357/915 0F/0E, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f541-test-nbb.*)
- **parity 56 度目 両 runtime PASS**:
  - JVM: FLAT ROOT **b4322bedd406112e…** / STATE ROOT **d1ebb9d30cd51516…** / PROOF a 10 verifies **true** (baseline 同一, evidence/f541-parity-jvm.*)
  - nbb: fixed **38 cases 0 drift** / fixed-result **20 cases 0 drift** / KOTOBA-PARITY: pass, PN_EXIT=0 (evidence/f541-parity-nbb.*)
- **fuzz digest 56 度目 byte-identical**: JVM 2733 (core 2715 + echo 行 `#'fuzz-seeded/run`) / nbb 2715, raw diff = **JVM echo 行 1 行のみ (24 bytes)** — core 全 digest 一致; f540 baseline との diff (f541-fuzz-vs540.txt) = **0 bytes** (evidence/f541-fuzz-{jvm,nbb}.out + f541-fuzzdiff.txt + f541-fuzz-vs540.txt)
- **bench 43 実行目 known-red**: copy `clojure -M:bench 1000000` BENCH_EXIT=1, **ArityException bench.cljc:112 `(bk/cancel! b oid)` 2 引数** (evidence/f541-bench.*) — f540-bench.err と diff すると report パス 1 行のみ差 (同型再帰). repo 3-part fix (f15+f16+f17) 未着地, 再現性 2 維持

## 判定
- 発覚 **0 件**, 新規 hypothesis **0 件** (NEXT の全項目はコード変更系 = インタラクティブ枠で実施, cron code-change 禁止)
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 19 件, f8–f18 OPEN, f14b CLOSED)
- NEXT 不変: validate-i53-halt fix パッケージ (インタラクティブ枠) → 3-part bench fix 着地 → HEAD 3× n=1M → 再現性 3 → fuzz 常設化

## 次枠
次枠番号 = **542** (新規実測時 suite 160 / parity 57 / fuzz 57 / bench 44 実行目)
