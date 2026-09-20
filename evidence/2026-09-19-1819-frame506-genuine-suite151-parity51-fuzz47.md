# 2026-09-19 18:19 frame 506 genuine run (no code changes, cron)

負荷 18:14–18:17 直測: pre-run 18:14 19.74/**20.52**/17.82 (5-min のみ >20), 直測 18:15 **14.13/18.13/17.19**, 18:17 **13.00/16.72/16.73** — 直測全 window <20 で gate 成立 → genuine run。

HEAD **694e2ac7** 直測 (frame 503 commit, frame 505 と同一), `git diff HEAD -- src/ script/ deps.edn | wc -l` = **0** 直接実測, working tree = maturity.md (M) + evidence のみ (src/ 変更なし)。

## 環境メモ (measurement copy 再構築)

/tmp/tori-f495 が削除済み (tmp cleanup) のため frame 506 用に再構築: `/tmp/tori-mkcopy-f499.py` の recipe (repo copytree → .cljk→.cljc rename 78 件 → ../kotoba sibling copytree symlinks) を `/tmp/tori-frame506.py` で実行 → **/tmp/tori-f506** 作成。repo 自身が kotoba を同梱するため recipe 初回 FileExistsError → `dirs_exist_ok=True` 追加で迂回 (copy 側変更のみ, repo 変更なし)。frame 505 発見済み pitfall への既知迂回も適用: repo 正本 `evidence/fuzz-seeded.cljk` を copy 側 evidence/ に同名複置 (nbb fuzz driver の readFileSync path 対策)。text-src prepend は gitlibs `73bdb13a` (CPGEN ok, full CP len 438)。

## 新規実測 (全 gate)

- **suite 151 両 runtime PASS (same-count 55 度目)**: JVM /tmp/tori-f506 `clojure -M:test` → **357 tests / 915 assertions, 0 failures / 0 errors, JVM_EXIT=0** (evidence/f506-test-jvm.{out,err,exit}); nbb 2 段 classpath (stage-1 `kbb --backend sci --classpath "<gitlibs text-src 73bdb13a>" script/nbb-classpath.cljc` CP_EXIT=0 → stage-2 `kbb --backend sci --classpath "<text-src>:<CP>" script/tests-on-nbb.cljc`) → **357/915 0F/0E, 17/17 namespaces, TESTS-ON-NBB pass NBB_EXIT=0** (evidence/f506-test-nbb.{out,err,exit})。
- **parity 51 度目 両 runtime PASS**: JVM `clojure -M:parity` PJ_EXIT=0 → FLAT ROOT **b4322bed…43445a** / STATE ROOT **d1ebb9d3…e3aed7f** / PROOF a 10 verifies true (frame-440/484/495/505 baseline と同一, evidence/f506-parity-jvm.*); nbb `script/kotoba-parity.cljc` (AMU_HOME=/…/orgs/kotoba-lang/amu + KOTOBA_CHECKOUTS + NODE_PATH + 2 段 classpath) → fixed **38 cases 0 drift** / fixed-result **20 cases 0 drift** / **KOTOBA-PARITY: pass** PN_EXIT=0 (evidence/f506-parity-nbb.*)。
- **fuzz digest 47 度目 byte-identical**: JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run))'` FJ_EXIT=0 (2715 bytes) vs nbb driver `evidence/fuzz-nbb-driver.cljc` FN_EXIT=0 (2715 bytes) — **raw diff 0 bytes** (f506-fuzzdiff.txt, fuzzdiff.exit=0) かつ **f488 baseline と byte-identical** (f488-fuzz-{jvm,nbb} との diff ともに空)。

## not-run

- bench: repo bench/torihiki/bench.cljk:112 が未だ 2 引数 (3-part fix f15+f16+f17 未着地) のため n=1M HEAD 実測不可 (cron code-change 禁止) — **bench 38 実行目繰越**。
- falsify 新規: NEXT 未実測リスト変化なし (falsify-14 は frame-452 実測済 CONFIRMED, 累算 site 7 で fix パッケージ待ち)。

## まとめ

発覚 0 件 (frame 505 の fuzz driver path 前提は既知 — 同一迂回で通過), 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。NEXT 未実測: 3-part bench fix 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。次枠番号 = **507** (新規実測時 suite 152 / parity 52 / fuzz 48 / bench 38 実行目)。枠記録: evidence/2026-09-19-1819-frame506-genuine-suite151-parity51-fuzz47.md。
