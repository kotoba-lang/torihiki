# torihiki maturity remeasure — 2026-09-03 20:36-20:47 (cron iteration, load-gate 通過後)

## Host load ゲート
- 20:34-20:36 実測: 18.88/20.03/23.65 → 12.97/18.10/22.66 (15min が閾値超過で待機)
- 20:43 実測: **19.93 / 17.02 / 19.66 — 全平均 < 20 → ゲート通過**, full run 実施。
- 先行 not-run 3 件 (20:02 / 20:13 / 20:24, evidence/2026-09-03-notrun-*.md) の
  続き。20:24 の予告どおり次回 run で通過した。

## `clojure -M:test` (JVM) — 20:44 実測
```
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.
```

## nbb クロスランタイム — 20:45 実測
classpath は `nbb script/nbb-classpath.cljs` の出力 (pins 由来, .nbb-deps) を使用:
```
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.
namespaces 17/17
TESTS-ON-NBB: pass — the runtime that deploys ran the suite
```
→ 両ランタイム同一カウント (357/915) 全緑。remeasure-1952 (19:52) と同カウント再現。

補記: `script/run-nbb-tests.cljs` は存在しない (README 相当のヘッダコメントが参照)。
実際の入口は `nbb script/nbb-classpath.cljs` → classpath を渡して
`nbb ... script/tests-on-nbb.cljs`。class 生成は `nbb script/nbb-classpath.cljs` で再現可。

## `clojure -M:bench 100000` (JVM) × 3 — 20:46-20:47 実測
```
188,031 ops/sec (5318 ns/op)
 77,815 ops/sec
124,867 ops/sec
```
- 同日帯ではあるが、ばらつきが大きい (78k-188k)。先行実測 (bench-1917/1948/
  remeasure-1952: 109-113k で安定) と異なり、本 run は load 15-19 残存下での実測。
- 判定: 「同桁帯で再現」までは維持するが「安定」の主張は弱める。再現性軸 2 据え置き
  の根拠として、低負荷時の安定実測 (load < 10 等) が別途要ることを明記。

## スコア判定 (status/maturity.md 7 軸)
- 全軸**据え置き**: spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。
- 根拠の日付・カウント更新: テスト 3 は本実測 (357/915 両ランタイム, 20:44-20:45)。
- fuzz/property 未整備のまま → テスト 4 は付けない。反証ループは falsify-1/2
  survived のまま変化なし。
- OPEN 赤: なし。
- 新たな観察 (赤としないが記録): bench の負荷感度 — load ~15-19 残存で
  78k-188k にばらつく。再現性軸の 3 付与条件に「低負荷環境での 3 回安定実測」を足すのが妥当。

## NEXT (維持 — highest leverage)
seeded fuzz harness: seed 固定の adversarial block 列を fold し、JVM と nbb が
同一 seed で同一 state root / 同一 :rejected を出す常設検証。
テスト 3→4 / 再現性 2→3 の直接根拠。本 run は No code changes 制約のため未実装。
