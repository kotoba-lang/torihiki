# frame 495 genuine run (2026-09-18 23:09–23:23 JST, cron, no code changes, parity 48 frame)

- Load gate: 23:09 pre-run 26.78 / 17.67 / 14.76 (1-min ≥20 の低下局面) → 23:12 直測 **6.45 / 11.92 / 12.94** 全 window <20 で gate 成立 (frame-397/401 低下局面前例)。23:23 測定後追測 10.35 / 14.86 / 14.45 も全 <20。
- HEAD **366321a** 直測 (23:12), `git diff HEAD -- src/ script/ deps.edn` = 0 行, working tree = maturity.md (M) + evidence のみ。repo bench/torihiki/bench.cljk:112 を実読し **2 引数 `(bk/cancel! b oid)` のまま** 再検収 (3-part fix 未着地)。
- terminal stdout キャプチャ破損 (既知 fault, echo すら空) → 全結果は redirect + read_file 迂回 (/tmp/f495*)。

## parity 48 度目 両 runtime 実測 PASS (NEXT 未実測の最上位を消化)

- **前提整備 (測定 copy 新規再構築, repo 変更なし)**: 測定 copy /tmp/tori-f451 は消滅 (23:12 実測 ENOENT — /tmp 揮発性, 発見 1 件)。repo から /tmp/tori-f495 を新規再構築: copytree (.git 除外) + **.cljk→.cljc 一括変換 152 ファイル** + `kotoba/` 同梱。`kotoba/` コピーは shutil.copytree デフォルト (symlinks=False) だと checkout 内の自己参照 symlink ループ (`kotoba/kotoba/kotoba/…` ELOOP) で落ちるため **symlinks=True 必須** (発見 2 件目, f495-mkcopy.log)。
- **JVM leg**: `cd /tmp/tori-f495 && clojure -M:parity` → **PJ_EXIT=0, FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true** — frame-440/454/456/458/460/461/464/484 baseline と同一 (evidence/f495-parity-jvm.out)。
- **nbb leg**: 規約 (frame-460/476/484 合成): classpath 生成自体に text-src prepend 必需 → `kbb --backend sci --classpath "<gitlibs-text-src 73bdb13a>" script/nbb-classpath.cljc` (CPGEN_EXIT=0, **CP_LEN=333**, frame-476/484 と同一) → 本体 `--classpath "<text-src>:$(CP)"` + `KOTOBA_CHECKOUTS=<orgs/kotoba-lang> NODE_PATH=<superproject>/node_modules AMU_HOME=<orgs/kotoba-lang>/amu` → **fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass, PN_EXIT=0** (evidence/f495-parity-nbb.out)。
- 両 runtime 結果は baseline 一致。**parity 48 度目 完了** (frame-484 parity 47 の次)。

## not-run (budget)

- suite / fuzz / bench: not-run (正本引用 frame-489 (0915) 実測 base 維持: suite **148** 両 runtime PASS / fuzz digest **43 度目** byte-identical / bench f451 copy 3-run green cancelled=153,767 frame-477)。bench は repo bench.cljk:112 が未だ 2 引数のため HEAD 実測不可のまま。**注意: f451 copy 消滅のため次枠以降の suite/bench 測定も本枠の再構築手順 (copytree + rename + kotoba symlinks=True) で copy を作り直す必要がある。**

## スコア

7 軸変更なし: **3/3/3/3/2/1/1** (反証実施 16 件, f8–f17 OPEN)。NEXT 未実測 0 件変化なし (fix 未着手 — cron code-change 禁止)。新規 hypothesis 0 件, 発見 2 件 (環境系: f451 copy 揮発 + kotoba symlink ループでの symlinks=True 必須)。

次ランナー (frame 496): repo への 3-part bench fix 着地 (f15 owner wiring + f16 arg-order + f17 ring-owner, インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。並行で validate-i53-halt fix パッケージ (累算 sum gate 7 site + notional/balance gates + mul-rate/REDUCING 事前境界 + rate 上限) が最高レバレッジのまま。
