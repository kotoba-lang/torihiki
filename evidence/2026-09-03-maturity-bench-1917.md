# torihiki maturity bench — 2026-09-03 19:17 JST

Host load at start: 19.99 / 17.71 / 17.60 (below 20 → runs allowed).
Load at end: 17.37 / 17.33 / 17.46.

## `clojure -M:test`
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.

## `clojure -M:bench 1000`
Bench now runs at HEAD (18:34 run's ArityException on `cancel!` is gone —
bench harness matches current `torihiki.book/cancel!` signature):

```
operations       1,000
placed           438
cancelled        0
resting at end   367
elapsed          0.031 s
THROUGHPUT       32,286 ops/sec
latency          30973 ns/op
ratio vs HyperCore reference (~200k ops/sec): 0.2x
```

(Boxed math warnings from torihiki/bench.clj:105/114 — cosmetic, did not fail.)

## Implication for maturity scores
- テスト axis: 357 / 915 / 全緑 re-verified → score 3 stands.
- 再現性 axis: the 18:34 run's "bench NOT reproducible at HEAD" note is now
  stale — bench is reproducible and produces numbers again. Supports keeping
  再現性 at 2 (seeded fuzz jobs still unmanaged).
- First working throughput datapoint recorded: ~32k ops/sec at n=1000.
  n=1000 is a small tape; treat as smoke-level, not a mature benchmark.
