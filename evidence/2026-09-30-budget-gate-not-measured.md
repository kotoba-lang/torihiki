# frame (2026-09-30) — budget-gate: NOT-MEASURED (両 runtime 実測未完行)

## 判定
**NOT-MEASURED** — 実行予算 (system: "Run time budget nearly exhausted") が残りの
2-runtime 実測 (JVM + nbb) を完行する前に尽きる。load gate は **通過** (12.65 < 20) で、
not-run の原因は **load ではなく実行予算** である。本 frame は **new measurement を生成していない**
(既存 baseline を流用し, fabricate しない)。

## 前提 (pre-run torihiki_state.sh 収集)
- **HOST LOAD: 12.65 / 10.90 / 11.05** → 20 を下回る → **load not-run ではない, 正常実行判定**
  (task gate: "Host load > 20 → not-run evidence only" は **適用外**)
- 前回 **実測** baseline: **frame 548 (2026-09-23 21:2x)** —
  - suite: 357 tests / 915 assertions 全緑 (66 度目) `evidence/f548-test-{jvm,nbb}`
  - fuzz reproducibility: 59 度目 (raw diff = JVM echo 行 1 行のみ, core byte-identical)
    `evidence/f548-fuzz-{jvm,nbb}.out` + `f548-fuzzdiff.txt` + `f548-fuzz-vs541.txt`
  - nbb 2-stage classpath: 33 度目 (script/nbb-classpath.cljk 経由)
- **frame 549–561: すべて load > 20 で not-run / loadgate-skip**
  (in-flight 9 files: 2026-09-25-0006 frame561, 2026-09-25-0858 frame549-loadgate-skip,
   2026-09-26 ×5, 2026-09-27-1721) → 本 frame はその後の **load gate 初通過** frame

## 本 frame の実行 (なぜ未完行か)
- 2-runtime 反証実測 = /tmp copy 構築 + JVM 実測 + nbb 2-stage 実測 + diff
  — 各 frame **wall-clock 50+ 分** 級 (例: falsify-13 12:06–13:10)
- 残 budget に収まらぬため **実行を断った**。既存 baseline (frame 548) と NEXT 仕様は不変。
- **fabricate 禁止** に従い, 測定値・root hash・diff を **生成していない**。

## in-flight (未 commit, 本 frame 由来 **ではない**)
```
 M src/torihiki/fixed.cljk
 M src/torihiki/slab.cljk
```
- 本 frame は **コード変更を一切していない** (task: "no code changes")。
  上記 2 件は prior frame 由来の uncommitted — 本 frame は **触れていない** (次回へ継承)。

## NEXT (次 frame, 実測の候補 — 本 frame は予算故に選択しなかった)
1. **validate-i53-halt fix 包** (maturity.md NEXT): i53 検査 (:bad-amount) + notional 上限
   `level × qty ≤ i53-max` [falsify-7 必須] + balance-domain gate `collateral + amount > i53-max`
   [falsify-8] + 累加 sum gate (:deficit [f12] / :funding-residue [f10] / :fees-collected [f11] /
   :insurance-fund [f13]) + fx/mul-rate 積 pre-check [f9] + apply-fill-to-position REDUCING
   事前界限 [f11] + rate 上限。
2. **bench-tape-cancel-arity fix** (`bench.clj:112` owner 付き 3 引数) + **n ≥ 1M 低負荷 3回
   安定** 実測 → 再現性 3 (bench 3-condition: 3 回 BENCH_EXIT=0 + cancelled>0 + n≥1M)。
3. **fuzz suite 常設化** → テスト/反証 4 (fuzz-seeded 2 runtime 同一 digest を suite に組み込み)。

## 証拠 (本 frame)
- **本 file 単体** (実測 artifact なし — 本 frame は new measurement を生成していない)
- 参照 baseline: `evidence/f548-{test,jvm,nbb}-*.out`,
  `evidence/f548-fuzz-{jvm,nbb}.out` + `f548-fuzzdiff.txt` + `f548-fuzz-vs541.txt`
