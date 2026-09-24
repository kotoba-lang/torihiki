# frame 548 (2026-09-23 21:2x, cron) — genuine run (no code changes)

## 負荷ゲート
- pre-run 21:04 (torihiki_state.sh block) **5.70/12.33/17.34**, 21:08 直測 (verify 前後) **14.45/11.13/15.23** — 全 window <20 → gate 成立
- runner START 21:23:51 **4.15/6.07/10.24**, END 21:24:17 **4.37/5.97/10.08** — 全 window <20, 全測定 EXIT=0 完走 (低負荷帯で全 slot 完遂; 完了後 21:38 に 19.26/18.65/14.84 に反発 — 測定完了後のため本枠判定に影響なし)

## HEAD / コード不変
- HEAD **92b9fc9211e6583d38627856f6e482af84bfd2a6** (21:08 直測, frame 541 以来不変, 542/546 と同一), `git diff HEAD --stat -- src/ script/ bench/ deps.edn` = **0 bytes**
- 測定 copy **/tmp/tori-f506** 再利用 + 本枠で全量再検証 (verify /tmp/tori-f541-verify{,2,3}.sh 流用): src 22 件 verified mismatches=0 + test/bench/script 25 件 (.cljk→.cljc rename 対応) verified mismatches=0 missing=0 + deps.edn identical + fuzz-seeded / fuzz-nbb-driver identical — VERIFY_EXIT=0 (v1: verified=22, v2: extras-verified=25, v3: verified=25 missing=0)

## 実測 (runner /tmp/tori-f548-run.sh, f546 runner の 546→548 rename 流用, vs541 diff 行は vs546 baseline 対応)
- **suite 162 両 runtime PASS (same-count 66 度目)**:
  - JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0F / 0E**, JVM_EXIT=0 (evidence/f548-test-jvm.*)
  - nbb: 2 段 classpath (stage-1 CP_EXIT=0 CP_LEN=333; NODE_PATH=org 絶対 chain/node_modules) → **357/915 0F/0E, 17/17, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f548-test-nbb.*)
- **parity 59 度目 両 runtime PASS**:
  - JVM: FLAT ROOT **b4322bedd406…** / STATE ROOT **d1ebb9d30cd5…** / PROOF a 10 verifies **true** (baseline 同一, evidence/f548-parity-jvm.*)
  - nbb: fixed **38 cases 0 drift** / fixed-result **20 cases 0 drift** / KOTOBA-PARITY: pass, PN_EXIT=0 (evidence/f548-parity-nbb.*)
- **fuzz digest 59 度目 byte-identical**: JVM 2733 (core 2715 + echo 行 `#'fuzz-seeded/run`) / nbb 2715, raw diff (f548-fuzzdiff.txt, 680 bytes) = ヘッダ + JVM echo 行 1 行のみ — core digest 全一致; f541 baseline との diff (f548-fuzz-vs541.txt) = **0 bytes** (evidence/f548-fuzz-{jvm,nbb}.out)
- **bench 46 実行目 known-red**: copy `clojure -M:bench 1000000` BENCH_EXIT=1, **ArityException bench.cljc:112 `(bk/cancel! b oid)` 2 引数** (evidence/f548-bench.*) — repo 3-part fix (f15+f16+f17) 未着地, 再現性 2 維持

## 判定
- 発覚 **0 件**, 新規 hypothesis **0 件** (NEXT の全項目はコード変更系 = インタラクティブ枠で実施, cron code-change 禁止)
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 19 件, f14b CLOSED, f8–f18 OPEN)
- NEXT 不変: validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 7 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) → 3-part bench fix 着地 → HEAD 3× n=1M → 再現性 3 → fuzz 常設化
- 環境メモ: 本枠 terminal 前面呼び出しが全て空出力 (既知 chronic fault) — 全読み取りは redirect → read_file, runner は background + process poll で迂回。runner 実走 26 秒 (f545 49 秒) は JVM/nbb の warm 状態による高速完走で、全 .out の内容・サイズ (test-jvm 603B / test-nbb 207B / fuzz 2733+2715B / parity 252+128B) が f546 実測と整合し 357/915 計数・baseline root を確認済 → genuine 採用。

## 次枠
次枠番号 = **549** (新規実測時 suite 163 / parity 60 / fuzz 60 / bench 47 実行目)
