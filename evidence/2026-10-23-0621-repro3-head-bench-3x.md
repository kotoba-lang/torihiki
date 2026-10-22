# 2026-10-23 06:21 (torihiki-fix tick) — reproducibility item: HEAD bench n=1M 3x stable, cancelled>0

HEAD (origin/main) **0a7a5fbd02577d35f1cf49973f07b657f62ebcaa** (PR #58 merge), worktree
bot/torihiki-fix-20261023-0621. Item = maturity.md NEXT reproducibility condition
("HEAD に対する n≥1M 低負荷 3 回安定実測 (cancelled>0)") — every code fix package in
NEXT is already landed at HEAD (verified: validate i53/notional caps api.cljk:124/:131,
balance-domain gate clearing.cljk:759, accumulator folds via fx/acc, fx/mul-rate pre-check
fixed.cljk:186, fx/pmul REDUCING bound clearing.cljk:166, settle-deficit delta clamp
clearing.cljk:731, rate caps, merkle aggregate saturation, bench.cljk:112 3-arg
`cancel!` with ring-owner, probe.cljk:31 / curve.cljk:21 owner-consistent, fuzz suite
in-suite). No source change in this PR — evidence append only.

## Load gate / low-load condition
- gate 06:18 pre-run **10.31/10.08/10.60** all <20 (tick start), torque from a concurrent
  foreign nbb run subsided; bench windows (evidence file) **14.65/16.12/14.57 →
  12.32/15.20/14.38** all <20 for all 3 runs.

## Bench (the measurement) — JVM `clojure -M:bench 1000000` x3 on renamed .cljk→.cljc copy
- RUN1 EXIT=0 / RUN2 EXIT=0 / RUN3 EXIT=0
- **identical counts in all 3 runs: placed 450,909 / cancelled 153,767 ( > 0 ) /
  resting at end 223,196** (deterministic tape; throughput 153,445 / 162,916 / 152,859
  ops/s varies with host load only)
- carriers: `2026-10-23-0621-repro3-bench-jvm-run{1,2,3}.{out,err}`
- note: `kbb -M:bench` is NOT a valid invocation — kbb is the nbb/Sci host and
  bench.cljk:30 `(set! *unchecked-math* …)` is JVM-only (measured refusal,
  `2026-10-23-0621-repro3-bench-run{1,2,3}.err`). The bench leg is JVM `clojure -M:bench`
  on a renamed copy, per convention.

## Suite — one runtime only (not both, deliberately)
- `kbb -M:test` on the worktree: **502 tests / 3504 assertions / 0 failures / 0 errors**,
  exit 0 (`2026-10-23-0621-repro3-test-nbb.{out,err}`).
- **JVM leg NOT REPRODUCED this tick** — renamed-copy `clojure -M:test` fails at
  `kotoba.test` resolution (the kotoba-lang dep libs ship .cljk only; the historical
  "dep shims" recipe lives in volatile /tmp copies, not in-repo). Self-built rename
  shims advance to `kotoba/lang/edn.cljc:1 Unable to resolve symbol: escape-controls`
  (kotoba-dialect layer) — carriers `2026-10-23-0621-repro3-test-jvm.{out,err}` record
  the refusal. No two-runtime count is claimed from this tick.

Scores are the measurement bots' call; nothing in status/maturity.md is touched here.
