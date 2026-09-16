# 2026-09-16 0210 frame 460 genuine run (no code changes, cron)

負荷 02:09–02:10 uptime 直測 1-min **11.07/11.25** / 5-min **11.55/11.52** / 15-min **12.35/12.25** 全 window <20 → gate 成立 → 全 gate 新規実測。

## HEAD / tree
- HEAD **0a99c9c2** (02:10 直測, frame-456〜458 と同一), git status: maturity.md (M) + untracked evidence 記録のみ, src/ script/ deps.edn 変更なし。

## 実測結果
- **JVM suite PASS (suite 142)**: /tmp/tori-f451 `clojure -M:test` → 357 tests / 915 assertions, 0F/0E, EXIT=0 (/tmp/t460_jvm_test.{out,err,exit} → evidence 転記省略, 本ファイルに tail 記録)。same-count **48 度目**。
- **nbb suite PASS (2-stage classpath 恒久構成)**: `kbb --backend sci --classpath '../text/src' script/nbb-classpath.cljk` → `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` → 357/915 0F/0E, TESTS-ON-NBB: pass, NBB_EXIT=0 (/tmp/t460_nbb_test.out)。
- **parity 44 度目**: JVM `clojure -M:parity` → FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3… / PROOF a 10 verifies true, PJ_EXIT=0 (frame-440/454 baseline 同一)。nbb 側は **kotoba-parity.cljk (amu compile Wasm 対 Oracle)** を AMU_HOME 指定付きで実測 → fixed 38 cases 0 drift / fixed/result 20 cases 0 drift / KOTOBA-PARITY: pass, EXIT=0。**発見 1 件 (迂回手順)**: (1) repo には src/torihiki/parity.cljc が無く .cljk 改名後は script/kotoba-parity.cljk が正の入口 (素 src パス指定は ENOENT, /tmp/t460_parity_nbb.err); (2) kotoba-parity.cljk は AMU_HOME/KOTOBA_CHECKOUTS 未設定で exit 2 "cannot measure: no amu" — AMU_HOME=…/orgs/kotoba-lang/amu 指定が必須 (/tmp/t460_parity_nbb2.err → nbb3 完走)。今後の parity 呼び出し規約 = repo copy 側で `AMU_HOME=<amu> kbb … script/kotoba-parity.cljk`。
- **fuzz digest 37 度目 byte-identical**: JVM は /tmp/tori-f451 で `clojure -M -e '(load-file "evidence-fuzz-seeded.cljc") (fuzz-seeded/run)'` (素 `clojure -M -m fuzz-seeded` は FileNotFoundException — copy の src に fuzz ns 無し, exit 1 測定済み) → EXIT=0。nbb は evidence/fuzz-nbb-driver.cljk (repo copy, 2-stage classpath) → EXIT=0。raw diff = JVM echo 行 `#'fuzz-seeded/run` のみ (baseline 形状) → **echo 行除外後 0 bytes**。
- **bench f451 copy n=1M run 1**: BENCH_EXIT=0, placed 450,908 / **cancelled 153,767** / resting 223,196 / 83,107 ops/sec / 12,033 ns/op — frames 451/453/455 と完全一致 (deterministic tape 再確認)。3x stable は frame-451/453 (+455) で 2 度独立実測済みのため本枠は 1 実行で precondition 維持確認。repo copy 着地待ちで再現性 2 のまま。

## falsify / hypothesis
- falsify-14 実測済 (frame-452 CONFIRMED)、falsify-16 測定済 (frame-449/451)。本枠新規仮説なし, 新規発覚は上記 parity 呼び出し規約 1 件 (手順発見, 赤 sp グレードなし)。

## スコア
7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。

## NEXT 不変
① validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 7 site [commit.cljk:115/:113 merkle aggregate 含む] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) ② 3-part bench fix 着地 (f15 owner + f16 arg-order + f17 ring-owner) → repo copy で n≥1M 3x 安定 → 再現性 3 ③ fuzz 常設化 ④ nbb bootstrap 2 段恒久化 ⑤ JVM .cljk runner ⑥ **parity 呼び出し規約恒久化 (AMU_HOME + script/kotoba-parity.cljk) — 本枠追加**。

次枠番号 = **461** (新規実測時 suite 143 / parity 45 / fuzz 38 / bench 37 実行目)。
