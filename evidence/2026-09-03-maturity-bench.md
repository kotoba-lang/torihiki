# torihiki maturity bench — 2026-09-03 18:34 JST

Host load at start: 12.48 / 13.75 / 15.52 (below 20 → runs allowed).
HEAD: dd55c85 (Merge agent/kotoba-book-hot-representation)

## `clojure -M:test`
Ran 357 tests containing 915 assertions.
0 failures, 0 errors.
(Matches maturity.md baseline: 357 / 915 / 全緑.)

## `clojure -M:bench` — BROKEN
```
generating a 5,000,000-operation tape...
warming up (JIT)...
Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112).
Wrong number of args (2) passed to: torihiki.book/cancel!
```
The bench harness is stale against the current `torihiki.book/cancel!` signature
(book hot-representation work, HEAD dd55c85, changed it). Throughput numbers
could not be collected. No code changes made per job scope — this needs a
bench.clj fix by the owner / a code-change iteration.

## Implication for maturity scores
- テスト axis still supported at 3 (same counts re-verified).
- 再現性 axis: `clojure -M:bench` currently NOT reproducible at HEAD →
  argues against raising 再現性 above 2; the recorded "bench reproducible"
  claim is now stale.
