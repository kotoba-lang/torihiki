# 2026-09-19 17:10 frame 505 genuine run (no code changes, cron)

負荷 17:09–17:10 uptime 直測: pre-run (torihiki_state.sh) 8.23/12.64/18.67, 直測 17:10 **6.43/11.57/17.97**, 測定後 17:12 5.79/9.53/16.27 — 全 window <20 で gate 成立 → genuine run。

HEAD **694e2ac7** 直測 (frame 503 commit), `git diff HEAD -- src/ script/ deps.edn | wc -l` = **0** 直接実測, working tree = maturity.md (M) + evidence のみ (src/ 変更なし)。測定 copy **/tmp/tori-f495 現存** (kotoba/ + script/*.cljc 同梱, deps.edn diff 0 bytes, HEAD 366321a 由来だが src diff 0 行によりコード同一として引用有効), ../text sibling 現存。

## 新規実測 (全 gate)

- **suite 150 両 runtime PASS (same-count 54 度目)**: JVM /tmp/tori-f495 `clojure -M:test` → **357 tests / 915 assertions, 0 failures / 0 errors, JVM_EXIT=0** (5.2s, evidence/f505-test-jvm.{out,err}); nbb 2 段 classpath (stage-1 `kbb --backend sci --classpath "<gitlibs text-src 73bdb13a>" script/nbb-classpath.cljk` CP_EXIT=0 CP_LEN=648 → stage-2 f495 copy 側 `kbb --backend sci --classpath "<text-src>:<CP>" script/tests-on-nbb.cljc`) → **357/915 0F/0E 17/17 namespaces TESTS-ON-NBB pass NBB_EXIT=0** (evidence/f505-test-nbb.{out,err,exit})。
- **parity 50 度目 両 runtime PASS**: JVM `clojure -M:parity` PJ_EXIT=0 → FLAT ROOT **b4322bed…43445a** / STATE ROOT **d1ebb9d3…e3aed7f** / PROOF a 10 verifies true (frame-440/484/495 baseline 同一, evidence/f505-parity-jvm.out); nbb `script/kotoba-parity.cljc` AMU_HOME=/…/orgs/kotoba-lang/amu + KOTOBA_CHECKOUTS + NODE_PATH + 2 段 classpath → fixed **38 cases 0 drift** / fixed-result **20 cases 0 drift** / **KOTOBA-PARITY: pass** PN_EXIT=0 (evidence/f505-parity-nbb.{out,err,exit})。
- **fuzz digest 46 度目 byte-identical**: JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run))'` FJ_EXIT=0 (2715 bytes) vs nbb driver `evidence/fuzz-nbb-driver.cljc` FN_EXIT=0 (2715 bytes) — **raw diff 0 bytes** (f505-fuzzdiff.txt, echo 行差分なし) かつ **f488 baseline と byte-identical** (f488-fuzz-{jvm,nbb}.out 双方 diff 空; fuzz-jvm-2030.out との差は既知 echo 行形状差 2733 vs 2715 bytes)。

## 環境メモ (nbb fuzz driver 落とし穴, 迂回済み)

f495 copy の evidence/fuzz-nbb-driver.cljc:3 は `evidence/fuzz-seeded.cljk` を readFileSync するため copy 側 .cljc リネームで ENOENT (f505-fuzz-nbb.err 1 回目 FN_EXIT=1) — repo 正本の fuzz-seeded.cljk を copy 側 evidence/ に同名複置して迂回 (copy のみ, repo 変更なし)。修正候補: driver の path を .cljc 対応 (driver は常設ドキュメント化候補)。

## not-run

- bench: repo bench/torihiki/bench.cljk:112 が未だ 2 引数 (3-part fix f15+f16+f17 未着地) のため n=1M HEAD 実測不可 (cron code-change 禁止) — bench 38 実行目繰越。
- falsify 新規: NEXT 未実測リスト変化なし (falsify-14 は frame-452 実測済 CONFIRMED, 累算 site 7 で fix パッケージ待ち)。

## まとめ

発覚 1 件 (環境系: nbb fuzz driver の .cljk path 前提, 迂回実測済み), 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。NEXT 未実測: 3-part bench fix 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。次枠番号 = **506** (新規実測時 suite 151 / parity 51 / fuzz 47 / bench 38 実行目)。
