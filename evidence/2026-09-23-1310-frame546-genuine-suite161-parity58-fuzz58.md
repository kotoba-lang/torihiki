# frame 546 (2026-09-23 13:0x, cron) — genuine run (no code changes)

## 負荷ゲート
- pre-run 13:04 **10.72/13.64/14.73**, 13:06 runner 開始時直測 **8.01/12.08/14.01** — 全 window <20 → gate 成立
- runner START 13:08:04 **25.96/17.27/15.81** (1-min 瞬間上振れ, 5/15-min は <20), END 13:10:26 **32.69/21.92/17.82** — 全 EXIT=0 完走 (frame-397/401 前例の低下/変動局面扱い, 全測定完遂のため genuine 採用)

## HEAD / コード不変
- HEAD **92b9fc9** (frame 541 以来不変, 13:07 直測), `git diff HEAD --stat -- src/ script/ bench/ deps.edn` = **0 bytes**
- 測定 copy **/tmp/tori-f506** 再利用 + 本枠で全量再検証 (verify スクリプト /tmp/tori-f541-verify{,2,3}.sh): src 22 件 verified mismatches=0 + test/bench/script 25 件 (.cljk→.cljc rename 対応) verified mismatches=0 missing=0 + deps.edn identical + fuzz-seeded / fuzz-nbb-driver identical — VERIFY_EXIT=0

## 実測 (runner /tmp/tori-f546-run.sh, f545 runner の 545→546 rename 流用)
- **suite 161 両 runtime PASS (same-count 65 度目)**:
  - JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0F / 0E**, JVM_EXIT=0 (evidence/f546-test-jvm.*)
  - nbb: 2 段 classpath (stage-1 CP_EXIT=0 CP_LEN=333; NODE_PATH=org 絶対 chain/node_modules) → **357/915 0F/0E, 17/17, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/f546-test-nbb.*)
- **parity 58 度目 両 runtime PASS**:
  - JVM: FLAT ROOT **b4322bed…** / STATE ROOT **d1ebb9d3…** / PROOF a 10 verifies **true** (baseline 同一, evidence/f546-parity-jvm.*)
  - nbb: fixed **38 cases 0 drift** / fixed-result **20 cases 0 drift** / KOTOBA-PARITY: pass, PN_EXIT=0 (evidence/f546-parity-nbb.*)
- **fuzz digest 58 度目 byte-identical**: JVM 2733 (core 2715 + echo 行 `#'fuzz-seeded/run`) / nbb 2715, raw diff (f546-fuzzdiff.txt, 680 bytes) = ヘッダ + JVM echo 行 1 行のみ — core digest 全一致; f541 baseline との diff (f546-fuzz-vs541.txt) = **0 bytes** (evidence/f546-fuzz-{jvm,nbb}.out)
- **bench 45 実行目 known-red**: copy `clojure -M:bench 1000000` BENCH_EXIT=1, **ArityException bench.cljc:112 `(bk/cancel! b oid)` 2 引数** (evidence/f546-bench.*) — repo 3-part fix (f15+f16+f17) 未着地, 再現性 2 維持

## 判定
- 発覚 **0 件**, 新規 hypothesis **0 件** (NEXT の全項目はコード変更系 = インタラクティブ枠で実施, cron code-change 禁止)
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 19 件, f14b CLOSED, f8–f18 OPEN)
- NEXT 不変: validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 7 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) → 3-part bench fix 着地 → HEAD 3× n=1M → 再現性 3 → fuzz 常設化
- 環境メモ: 本枠 terminal 前面呼び出しが全て空出力 (既知 chronic fault) — 全実測は PTY background + process poll で迂回

## 次枠
次枠番号 = **547** (新規実測時 suite 162 / parity 59 / fuzz 59 / bench 46 実行目)
