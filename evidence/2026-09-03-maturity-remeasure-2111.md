# maturity remeasure 21:11 (2026-09-03)

cron rank iteration による再実測。コード変更なし、スコア変動なし。

## 実測

1. JVM `clojure -M:test` → `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.`
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" script/tests-on-nbb.cljs`
   → `namespaces 17/17` / `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.`
   `TESTS-ON-NBB: pass — the runtime that deploys ran the suite`
   → 両ランタイム同日同カウント (21:11)。
3. seeded fuzz 16 seeds 再実行:
   - JVM: `clojure -M -e "$(cat evidence/fuzz-seeded.cljc) (fuzz-seeded/run)"` → evidence/fuzz-jvm-2111.out
   - nbb: `nbb --classpath "$(nbb script/nbb-classpath.cljs)" -e "..."` → evidence/fuzz-nbb-2111.out
   - diff (`#'` eval echo 行を除く) → 一致: **JVM==NBB 16/16 seed byte-identical (21:11)**
   - さらに 21:00 rerun 出力 (evidence/fuzz-jvm-rerun.out) とも **byte-identical**
     → seed 固定再現が 3 度目の実測でも成立。

## スコア判定 (変動なし)

| 軸 | score | 判定 |
|---|---|---|
| spec/契約 | 3 | 変動素材なし |
| 実装 | 3 | 変動素材なし |
| テスト | 3 | 21:11 再実測で 357/915 両ランタイム緑を追認。fuzz suite 未接続のため 4 は見送り (変わらず) |
| 反証 | 3 | falsify-1/2/3 survived は前反復で確定。harness 常設化未達のため 4 見送り (変わらず) |
| 再現性 | 2 | seed 固定再現を 21:11 にも追認 (JVM==NBB、21:00 出力とも byte-identical)。bench 3 条件 (低負荷 3 回安定) は未達 — 21:11 時点 host load 10.63 で低負荷条件を満たさず bench を実施せず。3 の条件は suite 接続と bench 3 回安定のまま |
| governor 統合 | 1 | 変動素材なし |
| 運用 | 1 | 変動素材なし |

## NEXT (高レバー)

harness を `clojure -M:test` と `script/tests-on-nbb.cljs` に接続して常設化する
(テスト軸 4 と反証軸 4 の両方の条件)。副次的に再現性 3 の bench 3 条件を
低負荷時間帯 (load < 5) に 3 回実測する。
