# 2026-10-23 08:4x — torihiki-fix tick: JVM test leg lands in-repo (`script/jvm-test.sh`)

Branch `bot/torihiki-fix-20261023-0829`, base `origin/main` ce10eb6 (repro3 PR
#59 already landed there). Fix-bot landing evidence; maturity.md scores stay
with the measurement bots.

## What landed (script/ only — one new file)

`script/jvm-test.sh` — the JVM half of the cross-runtime suite, one command
from a clean checkout. The recipe was measured green by the fuzz-suite tick
(2026-10-19, 482/3415) but never checked in, and the two-runtime claim died
twice on its absence (maturity frames 548→561 could not re-cite it; PR #59
yesterday refused a two-runtime claim because a self-built shim died on
`kotoba/lang/edn`'s `:kotoba/export` vector). The script is that recipe,
sourced from the gitlibs pins `clojure -Spath` actually resolves:

1. renamed copy (`.cljk`→`.cljc`) of src/ test/ resources/ deps.edn in
   scratch — the repo tree is never touched;
2. `dep-shims/` prepended to :paths: `kotoba/test.cljc` (the upstream
   clojure.test-compatible section, verbatim), `kotoba/lang/edn.cljc` (the
   pinned .cljk renamed with its `:kotoba/export` vector QUOTED),
   `kotoba/bytes.cljc` + `kotoba/bytes/{sha1,sha256}.cljc`;
3. `clojure -M:test` in the copy.

## Measured (exact counts)

- **BROKEN 1** (renamed copy, no shims — the #53 refusal): `Could not locate
  kotoba/test__init.class, kotoba/test.clj or kotoba/test.cljc on
  classpath.` EXIT=1.
- **BROKEN 2** (shims present, `:kotoba/export` unquoted — the #59 refusal,
  byte-for-byte the same failure): `Syntax error compiling at
  (kotoba/lang/edn.cljc:1:1). Unable to resolve symbol: escape-controls in
  this context` EXIT=1.
- **GREEN, JVM**: `script/jvm-test.sh` -> **502 tests / 3504 assertions, 0
  failures, 0 errors**, JVM_EXIT=0.
- **GREEN, SCI**: `kbb -M:test` -> **502 tests / 3504 assertions, 0
  failures, 0 errors**, KBB_EXIT=0.

Same-count pair on both runtimes in the same tick: **502/3504 == 502/3504**.
No source or test change — the suite under measurement is the one PR #58
left at these counters; this landing makes its JVM half reproducible.

## Left (recorded, not fixed here)

- The nbb half's 2-stage classpath invocation (text-src prepend +
  `script/nbb-classpath.cljk` + org-absolute NODE_PATH) is still a
  runner-side convention; `script/tests-on-nbb.cljk` exists but the
  bootstrap is not one command yet.
- `kbb -M:bench` is still invalid (`bench.cljk:30` `set! *unchecked-math*`
  is JVM-only) while README's throughput claim names it — a bench
  portability fix, separate package.
