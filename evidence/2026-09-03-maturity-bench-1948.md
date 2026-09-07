# torihiki maturity bench — 2026-09-03 ~19:45 (cron iteration 5)

## Host load
`19:44 up 10 days, load averages: 15.95 16.32 16.44` — below the 20 threshold → full run allowed.

## `clojure -M:test` (JVM)
```
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.
```
→ 357 tests / 915 assertions 全緑。status/maturity.md の記載 (357/915) と一致。

## `clojure -M:bench 100000` (JVM)
```
operations       100,000
placed           45,263
cancelled        0
resting at end   37,859
elapsed          0.887 s
THROUGHPUT       112,744 ops/sec
latency          8870 ns/op
ratio vs HyperCore reference: 0.6x
```

## Prior run same day (evidence/2026-09-03-maturity-bench-1917.md と比較)
同日 19:17 実測: 357/915 全緑, throughput は同桁帯。今回 112.7k ops/sec —
reproduce 軸の `clojure -M:test` / `clojure -M:bench <n>` 再現性は確認済み。
負荷 (load ~16) 下でも結果は安定。

## Changes
なし (no code changes, evidence 記録のみ)。

## NEXT (maturity.md より)
seeded fuzz harness — adversarial block 列を seed 固定で folding。
