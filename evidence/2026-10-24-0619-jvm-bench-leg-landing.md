# 2026-10-24 06:19 torihiki-fix tick — bench leg lands in-repo (script/jvm-bench.sh + script/jvm-leg.sh)

Load gate: 12.28 / 11.58 / 11.93 (all windows < 20, `torihiki_state.sh` pre-run) — run.

Pick: maturity.md NEXT residue, highest-leverage UNLANDED item = the bench
runner package (PR #60 "Not claimed": `kbb -M:bench` invalid while README
names it; deps.edn `:bench` comment: "it must stay runnable or the claim
rots"; the JVM bench recipe lived in nobody's tree). The validate/accumulator/
mul-rate/pmul/rate-cap/merkle fix packages are all landed (PRs #52–#58) and
repro3 landed (#59) — verified component by component before choosing.

## Measured BROKEN (before)

- `kbb -M:bench 1000` → EXIT=1, `Error: Unable to resolve symbol:
  *unchecked-math*` (nbb_main.js — SCI cannot run bench.cljk's JVM-only
  `set!`, /tmp/fix0619-kbbbench.{out,err})
- `clojure -M:bench 1000` over the repo tree → EXIT=1,
  `Could not locate torihiki/bench__init.class, torihiki/bench.clj or
  torihiki/bench.cljc on classpath.` (the .cljk invisibility,
  /tmp/fix0619-jvmbench-tree.{out,err})
- `script/jvm-bench.sh` did not exist; the README transcript named the
  invalid `kbb -M:bench 10000000`, and "Running either benchmark" cited a
  failure mode ("Could not find namespace: kotoba.lang.text") that no longer
  reproduces — kbb resolves the git deps now and dies at the `set!` instead.

## What landed (branch bot/torihiki-fix-20261024-0619)

- `script/jvm-leg.sh` — the renamed-copy recipe extracted from
  script/jvm-test.sh (copy src/ test/ bench/ resources/ + deps.edn,
  .cljk→.cljc rename, dep-shims from the pins `clojure -Spath` resolves,
  :paths patch), parameterized by alias: `script/jvm-leg.sh <alias> [args…]`.
- `script/jvm-test.sh` — now a thin wrapper (`jvm-leg.sh test`); KEEP=1 /
  JVM_WORK semantics preserved.
- `script/jvm-bench.sh` — new: `[n]` → `:bench`, `--hl [n-blocks block-size
  n-accounts]` → `:bench-hl`.
- `script/jvm-leg.sh` shim set grew one entry: `kotoba/io.cljc`, a
  FORWARDING shim to clojure.java.io (upstream kotoba.io is a
  one-repo-per-definition re-export hub, all .cljk; torihiki.hyperliquid
  calls exactly `io/resource`; deps.edn documents "kotoba.io replaced
  clojure.java.io"). Discovered by measurement: `--hl` failed
  `Could not locate kotoba/io__init.class` (/tmp/fix0619-benchhl.err) before
  the shim existed.
- README: throughput transcript command → `script/jvm-bench.sh 10000000`
  (measured numbers untouched); "Running either benchmark" now names
  script/jvm-bench.sh and cites the 2026-10-24 measured failure modes.

## Measured GREEN (after)

- `script/jvm-bench.sh 1000000` ×2 → EXIT=0 both, deterministic counts
  IDENTICAL run to run and identical to PR #59's repro3 counters:
  placed 450,909 / cancelled 153,767 (>0) / resting 223,196
  (/tmp/fix0619-bench{1,2}.out).
- `script/jvm-bench.sh --hl 2 100 4` → EXIT=0, report printed
  (/tmp/fix0619-benchhl2.out).
- `script/jvm-test.sh` → 502 tests / 3504 assertions / 0F / 0E, EXIT=0 —
  measured BEFORE the wrapper refactor (baseline) and AFTER; identical
  counters, refactor behavior-identical.
- `kbb -M:test` → 502 / 3504 / 0F / 0E, EXIT=0 — same-count pair with the
  JVM leg in this tick.

## Not claimed

- The nbb leg (2-stage classpath convention) was NOT run this tick; no
  two-runtime (JVM/SCI/nbb) parity claim is made here beyond the
  kbb -M:test / script/jvm-test.sh same-count pair measured above.
- `kbb -M:test`'s runtime label ("# the JVM" in the README Test block) was
  not adjudicated and the Test block was left untouched — kbb's -M: backend
  identity is a citation question for the measurement bots.
- Scores untouched — measurement bots' call.
