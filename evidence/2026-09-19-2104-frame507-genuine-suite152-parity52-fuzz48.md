# 2026-09-19 21:10 frame 507 genuine run (no code changes, cron)

負荷 21:04–21:06 直測: 1-min **14.23/17.70** / 5-min **16.94/17.45** / 15-min **17.18/17.35** — 全 window <20 で gate 成立 → genuine run。

HEAD **694e2ac7** 直測 (frame 503 commit, frame 505/506 と同一), `git diff HEAD -- src/ script/ deps.edn` = **0 行** 直接実測, working tree = maturity.md (M) + evidence のみ (src/ 変更なし)。

## 環境メモ

measurement copy **/tmp/tori-f506 現存** (frame 506 で構築済み) をそのまま再利用。既知迂回 2 点を再適用: (1) nbb classpath stage-1 の text-src prepend は gitlibs **root ではなく `/src` まで** (`…/text/73bdb13a…/src` — root 指定は `Could not find namespace: kotoba.lang.text` で EXIT=1, /tmp/f507-cp.err 実測; CPGEN EXIT=0 後 full CP = src:test + .nbb-deps 6 pin)。f506 記録の "CP_LEN 438" との差は相対/絶対 path 表記のみ。(2) nbb stage-2 は **NODE_PATH=…/orgs/kotoba-lang/chain/node_modules 必須** (無指定は `Cannot find module '@noble/hashes/sha2.js'` で EXIT=1, /tmp/f507-test-nbb.err 1 回目実測)。frame 506 のメモに NODE_PATH 前提が明示されていない点は f508 以降の pitfall。

## 新規実測 (全 gate)

- **suite 152 両 runtime PASS (same-count 56 度目)**: JVM /tmp/tori-f506 `clojure -M:test` → **357 tests / 915 assertions, 0 failures / 0 errors, JVM_EXIT=0** (evidence/f507-test-jvm.{out,err,exit}); nbb 2 段 classpath (stage-1 `kbb --backend sci --classpath "<gitlibs text-src 73bdb13a>/src" script/nbb-classpath.cljc` CP_EXIT=0 → stage-2 NODE_PATH=chain/node_modules + `kbb --backend sci --classpath "<text-src>:<CP>" script/tests-on-nbb.cljc`) → **357/915 0F/0E, 17/17 namespaces, TESTS-ON-NBB pass NBB_EXIT=0** (evidence/f507-test-nbb.{out,err,exit})。
- **parity 52 度目 両 runtime PASS**: JVM `clojure -M:parity` PJ_EXIT=0 → FLAT ROOT **b4322bed…43445a** / STATE ROOT **d1ebb9d3…e3aed7f** / PROOF a 10 verifies true (frame-440/484/495/505/506 baseline と同一, evidence/f507-parity-jvm.*); nbb `script/kotoba-parity.cljc` (AMU_HOME=…/amu + KOTOBA_CHECKOUTS=…/kotoba-lang + NODE_PATH=chain/node_modules + 2 段 classpath) → fixed **38 cases 0 drift** / fixed-result **20 cases 0 drift** / **KOTOBA-PARITY: pass** PN_EXIT=0 (evidence/f507-parity-nbb.*)。
- **fuzz digest 48 度目 byte-identical**: JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run))'` FJ_EXIT=0 (2715 bytes) vs nbb driver `evidence/fuzz-nbb-driver.cljc` FN_EXIT=0 (2715 bytes) — **raw diff 0 bytes** (f507-fuzzdiff.txt 0 bytes) かつ **f488 baseline と byte-identical**。

## not-run

- bench: repo bench/torihiki/bench.cljk:112 が未だ 2 引数 (3-part fix f15+f16+f17 未着地) のため n=1M HEAD 実測不可 (cron code-change 禁止) — **bench 39 実行目繰越**。
- falsify 新規: NEXT 未実測リスト変化なし (falsify-14 は frame-452 実測済 CONFIRMED, 累算 site 7 で fix パッケージ待ち)。

## まとめ

発覚 0 件, 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。NEXT 未実測: 3-part bench fix 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。次枠番号 = **508** (新規実測時 suite 153 / parity 53 / fuzz 49 / bench 39 実行目)。枠記録: evidence/2026-09-19-2104-frame507-genuine-suite152-parity52-fuzz48.md。
