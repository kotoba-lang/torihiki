# bench iteration 2026-09-03 22:51

- `clojure -M:bench` (default 5M tape), host load ~16.6
- Result: RED — `ArityException` at torihiki.bench/run-tape (bench.clj:112):
  "Wrong number of args (2) passed to: torihiki.book/cancel!"
- Confirms open red **bench-tape-cancel-arity**: bench uses the retired 2-arg
  `book/cancel!` signature. Throughput not measurable until fixed.
- No code changes made (per iteration rule).
