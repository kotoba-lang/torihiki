# frame 484 genuine run (2026-09-18 00:5x–01:09 JST, cron, no code changes)

- Load gate: 01:05 pre-run 12.06 / 12.89 / 13.54, 01:09 direct 15.59 / 13.22 / 13.42 — 全測定とも全 window <20, gate passed (frame 483 に続き 2 連続)。
- HEAD **366321a** 直接実測 (frame 477/478/480 と同一), `git diff HEAD -- src/ script/ deps.edn` = 0 行 (01:09 直測), working tree = maturity.md (M) + evidence のみ。src/ 変更なし (cron code-change 禁止遵守)。
- terminal stdout キャプチャ破損 (既知 fault) → 結果は redirect + read_file 迂回 (/tmp/t484*.txt)。

## parity 47 度目 (NEXT 未実測の最上位を 1 件消化)

- **前提整備 (測定 copy への script-only 追加, repo 変更なし)**: /tmp/tori-f451 に `kotoba/` が無く (frame 476 以降の parity 不成立原因) `cp -R kotoba /tmp/tori-f451/` (COPY_EXIT=0)、加えて `script/run-kotoba-wasm.mjs` も欠けていた (nbb 1 回目 `Cannot find module …/script/run-kotoba-wasm.mjs`, evidence/f484-parity-nbb-wasm-missing.err — frame 476 が copy した 3 script に含まれていなかった新規発見) を repo から copy。
- **JVM leg**: `cd /tmp/tori-f451 && clojure -M:parity` → PJ_EXIT=0, **FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true** — frame-440/454/456/458/460/461/464 baseline と同一 (evidence/f484-parity-jvm.out)。
- **nbb leg**: 規約 (frame-460) `AMU_HOME=<amu> kbb … script/kotoba-parity.cljk` に frame-476 迂回を合成: classpath 生成自体が素呼びで落ちる (本枠再確認: bare `kbb … nbb-classpath.cljk` は CP_EXIT=1 `Could not find namespace: kotoba.lang.text`) ので **`kbb --backend sci --classpath "<gitlibs-text-src>" script/nbb-classpath.cljk` で生成 (CPGEN_EXIT=0, CP_LEN=333, text sha 2ee6dce2) → 本体は `--classpath "<text-src>:$(CP)"`** + `KOTOBA_CHECKOUTS=<orgs/kotoba-lang> NODE_PATH=<superproject>/node_modules AMU_HOME=<amu>` → **fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass, PN_EXIT=0** (evidence/f484-parity-nbb.out)。
- 両 runtime 結果は baseline 一致。**parity 47 度目 完了 (46 繰越分含め NEXT 未実測の parity 部分を消化)**。

## not-run (budget/前提)

- suite / fuzz / bench: not-run (frame 476 suite 145 both-runtimes PASS + fuzz 40 度目 byte-identical, frame 477 bench 3-run green series が正本引用のまま)。bench は repo bench/torihiki/bench.cljk:112 が未だ 2 引数 (3-part fix 未着地) のため HEAD 実測不可のまま。
- 発覚 1 件 (環境系): 測定 copy は `run-kotoba-wasm.mjs` を欠く — 今後の nbb parity 前提整備リストに script 4 本 (tests-on-nbb / nbb-classpath / loads-on-nbb / run-kotoba-wasm.mjs) + kotoba/ が必須。

## スコア

7 軸変更なし: **3/3/3/3/2/1/1** (反証実施 16 件, f8–f17 OPEN)。NEXT 未実測 0 件変化なし (fix 未着手 — cron code-change 禁止)。新規 hypothesis なし。

次ランナー (frame 485): repo への 3-part bench fix 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。同時に validate-i53-halt fix パッケージ (累算 sum gate 7 site + notional/balance gates + mul-rate/REDUCING 事前界限 + rate 上限) が最高レバレッジのまま。
