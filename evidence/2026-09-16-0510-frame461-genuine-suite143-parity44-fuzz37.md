# 2026-09-16 05:1x frame 461 genuine run (no code changes, cron)

負荷 05:05 uptime 直測 1-min **12.36** / 5-min **12.57** / 15-min **14.67** 全 window <20 → gate 成立 → 全 gate 新規実測。

## HEAD / tree
- HEAD **0a99c9c** (frame-456〜460 と同一), git status: maturity.md (M) + untracked evidence のみ, src/ script/ deps.edn 変更なし。/tmp/tori-f451 測定 copy 現存確認済。

## 実測結果 (出力 /tmp/t461_*.{out,err,exit} — evidence 転記は本ファイルに要約)
- **JVM suite PASS (suite 143)**: /tmp/tori-f451 `clojure -M:test` → 357 tests / 915 assertions, 0F/0E, JVM_EXIT=0。same-count **49 度目**。
- **nbb suite PASS (2-stage classpath 恒久構成)**: `kbb --backend sci --classpath '../text/src' script/nbb-classpath.cljk` → `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` → 357/915 0F/0E 17/17, TESTS-ON-NBB pass, NBB_EXIT=0。
- **parity 44 度目**: JVM `clojure -M:parity` PJ_EXIT=0 → FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3… / PROOF a 10 verifies true (frame-440/454/458 baseline 同一)。nbb は規約通り `AMU_HOME=<amu> kbb … script/kotoba-parity.cljk` (frame-460 追加規約) → fixed 38 cases 0 drift / fixed/result 20 cases 0 drift / KOTOBA-PARITY: pass, PN_EXIT=0。
- **fuzz digest 37 度目 byte-identical**: JVM load-file run FJ_EXIT=0 vs nbb driver FN_EXIT=0, seed 14/15 digest 全一致, diff 0 bytes。
- **bench f451 copy n=1M**: BENCH_EXIT=0, placed 450,908 / **cancelled 153,767** / resting 223,196 — frames 451/453/455/456/460 と完全一致 (deterministic tape 再確認, 3x stable 前提維持)。再現性 2 のまま (repo copy 着地待ち)。

## falsify / hypothesis
- falsify-14 実測済 (frame-452 CONFIRMED), falsify-16/17 測定済 (frame-449/451)。本枠新規仮説なし, 発覚 0 件。

## スコア
7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。

## NEXT 不変
① validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 7 site [commit.cljk:115/:113 merkle aggregate 含む] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) ② 3-part bench fix 着地 (f15 owner + f16 arg-order + f17 ring-owner) → repo copy で n≥1M 3x 安定 → 再現性 3 ③ fuzz 常設化 ④ nbb bootstrap 2 段恒久化 ⑤ JVM .cljk runner ⑥ parity 呼び出し規約恒久化 (AMU_HOME + script/kotoba-parity.cljk)。

次枠番号 = **462** (新規実測時 suite 144 / parity 45 / fuzz 38 / bench 37 実行目)。
