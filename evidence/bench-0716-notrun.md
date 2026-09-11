# bench iteration — 2026-09-04 07:16 (NOT RUN: host load)

## Decision
Host load 1-min average **34.48** (5-min 31.44, 15-min 26.40) at 2026-09-04 07:16:54 JST — above the >20 threshold. `kbb -M:test` / `kbb -M:bench` not executed; only this not-run evidence recorded. No code changes.

## Status carried forward
- Latest green suite: test-0657.out (JVM) + test-nbb-0657.out (nbb), 357 tests / 915 assertions, both runtimes matched — 29th confirmed count.
- Remaining OPEN red (from maturity.md):
  1. validate-i53-halt fix (incl. falsify-7 notional cap + falsify-8 balance-domain gate + falsify-9/10 accum sum gate + fx/mul-rate pre-check + rate cap)
  2. bench-tape-cancel-arity fix (bench.clj:112, 3-arg cancel!) + n ≥ 1M low-load 3-run stable → reproducibility 3
  3. fuzz suite 常設化 → tests/falsification 4

## Next iteration
Re-check load at start; run `kbb -M:test` when 1-min load < 20. bench stays red until cancel-arity fix (21 consecutive red runs at bench-0657).
