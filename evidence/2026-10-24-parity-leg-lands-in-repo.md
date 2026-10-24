# 2026-10-24 — parity leg lands in-repo (torihiki-fix tick)

Fix package: maturity.md NEXT residue 「parity 呼び出し規約恒久化」 — the last
unlanded reproducibility item after #60 (jvm-test.sh) / #61 (jvm-bench.sh) /
#62 (nbb-leg.sh + nbb-test.sh) brought the test and bench legs in-tree.

## What was broken (measured, not assumed)

The cross-runtime state-root parity check (`torihiki.parity`) was pinned in
`torihiki.commit-test` but unrunnable as documented — all three documented
invocations fail as written (2026-10-24, worktree at origin/main 7f32214):

| invocation (as documented) | exit | error |
|---|---|---|
| `kbb -M:parity` | 1 | `Unable to resolve symbol: torihiki.parity/-main` (the `:parity` alias's `:main-opts` name `-main`, which is `#?(:clj ...)` only) |
| `clojure -M:parity` | 1 | `Could not locate torihiki/parity__init.class, torihiki/parity.clj or torihiki/parity.cljc on classpath` (`.cljk` invisible to the JVM) |
| `kbb --backend sci --classpath "src" -e "(require '[torihiki.parity :as p]) (p/report)"` (docstring's hand-rolled classpath, naive fill) | 1 | `Could not find namespace: merkle-sum.core` (the deps.edn pins ARE the classpath; `<path-to>/…` prose placeholders are not) |

Raw outputs: `/tmp/par-broken{1,2,3}.{out,err}` during the run (scratch;
transcribed here in the table).

## What landed

- `script/torihiki-parity-on-nbb.cljk` (new) — the nbb half: requires
  `torihiki.parity`, calls `p/report`. Header records the three measured
  failures above.
- `script/jvm-parity.sh` (new) — thin wrapper: `jvm-leg.sh parity`.
- `script/nbb-parity.sh` (new) — thin wrapper: `nbb-leg.sh torihiki-parity-on-nbb.cljk`.
- Citations retired: `src/torihiki/parity.cljk` docstring, README Platform
  section, deps.edn `:parity` comment now name the in-tree commands;
  `script/nbb-leg.sh` usage lists the new driver.

## Measured after (same worktree)

- `./script/jvm-parity.sh` — exit 0, prints
  `FLAT ROOT b4322bedd406112e6c2c7339e93d646eb7852959def143901961b3be4f43445a`,
  `STATE ROOT d1ebb9d30cd51516c14bc9e3c0007d806b423e33cd3bd64200362c7e7e3aed7f`,
  `PROOF a 10 verifies  true`.
- `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang ./script/nbb-parity.sh`
  — exit 0, identical output.
- `diff` of the two stdout captures: **empty** (byte-identical).
- Suite unchanged and green on all three legs, 502 tests / 3504 assertions /
  0 failures / 0 errors each: `kbb -M:test`, `script/jvm-test.sh`,
  `script/nbb-test.sh` (KOTOBA_CHECKOUTS as above).

Note: the digests themselves are pinned in `torihiki.commit-test` (single
source — a deliberate encoding change moves them there, with a comment);
this package adds no second pin. What it adds is commands that run.
