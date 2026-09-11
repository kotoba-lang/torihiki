# 2026-09-03 21:00 maturity re-measure — seeded fuzz cross-runtime parity 実測

## 目的

status/maturity.md の NEXT だった「seeded fuzz harness」が `evidence/fuzz-seeded.cljk`
として存在するようになったため、それを両ランタイムで実測し 7 軸を再評価する。
コード変更はなし（src/ test/ 触らず、測定と記録のみ）。

## 実測

### 1. seeded fuzz（falsify-3 harness, evidence/fuzz-seeded.cljk）

- 内容: xorshift32 seed 固定で adversarial block 列（24 blocks × 48 txs × 16 seeds、
  6 accounts、bad order / wrong-owner cancel / bogus oid / trigger / liquidation 混在、
  block 11 で snapshot round-trip）を folding し、flat root / state root / resting /
  rejection / fill / snapshot-parity を digest として出力。
- JVM: `clojure -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  → evidence/fuzz-jvm.out（20:48 記録済み）と evidence/fuzz-jvm-rerun.out（21:00 再実行）が
  **byte-identical**（diff 空だ= seed 固定再現性の実測）。
- nbb: `nbb --classpath "$(nbb script/nbb-classpath.cljk)" -e "$(cat evidence/fuzz-seeded.cljk)
  (fuzz-seeded/run)"` → evidence/fuzz-nbb-rerun.out。
  **JVM 出力と 16/16 seed 全 digest が byte-identical**（diff は JVM 側の `#'fuzz-seeded/run`
  var echo の 1 行のみ、seed 行は全て一致）。snapshot-parity は 16/16 `"true"`。
- なお evidence/ を classpath に足して `(require '[fuzz-seeded])` する呼び方は
  nbb が namespace を解決せず失敗（evidence/fuzz-nbb-rerun.err に記録）。nbb には
  `load-file` も `slurp` もないため、現状の実行は手組みの `-e` インライン注入が必要。

### 2. 両ランタイム同日テストスイート

- JVM: `clojure -M:test` → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
- nbb: `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`
  → **Ran 357 tests containing 915 assertions. 0 failures.**
  `TESTS-ON-NBB: pass — the runtime that deploys ran the suite`
  （evidence/nbb-tests-rerun.out, 21:00 実測）

### 3. bench（load 残存下, 単発）

- 21:00、load average 15.01 で `clojure -M:bench 200000` → **170,382 ops/sec / 5869 ns/op**
  （evidence/bench-2100.out）。低負荷 3 回安定実測の条件には届かず、再現性 3 は見送り。

## スコア更新（この根拠による）

| 軸 | 旧 | 新(21:00 再計測確定) | 根拠 |
|---|---|---|---|
| spec/契約 | 3 | 3 | 変化なし（consensus 接続仕様は未） |
| 実装 | 3 | 3 | 変化なし（consensus 接続なし） |
| テスト | 3 | **3** | 357/915 両ランタイム同日全緑 + seeded fuzz 実在。ただし suite 未接続のため 4 は付けない（falsify-3 evidence の提案条件そのまま） |
| 反証 | 3 | **3** | 反証 3 連続 survived（falsify-3 は 16 seeds 全 digest 両ランタイム一致）。ただし harness が evidence/ 固定・常設ジョブ化未了のため 4 は見送り |
| 再現性 | 2 | **2** | fuzz の seed 固定再現は実測済み（JVM 再実行 byte-identical、nbb==JVM 16/16）だが、bench 3 条件（低負荷 3 回安定）は未達 — 21:00 load 15.01 で 170,382 ops/sec の単発のみ。suite 接続と常設化が 3 の条件 |
| governor 統合 | 1 | 1 | 変化なし |
| 運用 | 1 | 1 | 変化なし |

注: 初稿でテスト/反証を 4 に上げる提案を書いたが、falsify-3 evidence
(2026-09-03-falsify-3-seeded-fuzz.md) が「suite 接続が条件」と明示しており
その条件が未達のため、再計測では点数を据え置いた。propose された条件を
先取りして点数化しないのが誠実。

## OPEN 赤（新規検出）

1. **fuzz harness が test/ にない**（evidence/ にのみ存在）。実行が手組みの
   nbb `-e` インライン注入を必要とし、CI gate になっていない —
   「recorded instrument が main に無い」失敗形と同型で、置いたまま腐る。
2. （軽微）`script/run-nbb-tests.cljs` は tests-on-nbb.cljs の docstring に書かれているが
   script/ に存在しない（昨日の ADR で指摘済みの形の再発）。
