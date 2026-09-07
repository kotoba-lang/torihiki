# torihiki maturity bench iteration — 2026-09-04 07:54–07:59 JST

## Host load at start
- 07:44 (pre-run script): load avg 26.73 / 25.53 / 23.59 → >20, not-run rule considered
- 07:54 (re-check): 16.78 / 19.37 / 21.46, trending down → proceeded

## JVM test suite (`clojure -M:test`) → evidence/test-0755.out
- Ran 357 tests containing 915 assertions. **0 failures, 0 errors.**
- Wall: 43.2s. All green — JVM==nbb count parity with 0712 baseline (357/915) unchanged.

## Bench (`clojure -M:bench`) → evidence/bench-0757.out / .err — **RED again**
- Same known failure: `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112)` —
  `Wrong number of args (2) passed to: torihiki.book/cancel!` (bench-tape-cancel-arity).
- Died during "warming up (JIT)" after tape generation (5,000,000-op tape) — no throughput numbers.
- **23rd consecutive red confirmation** (previous: 0656/0657/0712 = 22nd per maturity.md).
- Fix unchanged: bench.clj:112 needs owner-attached 3-arg `cancel!`; then n ≥ 1M low-load 3× stable required for 再現性 3.

## No code changes made.
