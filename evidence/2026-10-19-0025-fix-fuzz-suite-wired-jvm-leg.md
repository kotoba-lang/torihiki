# 2026-10-19 00:2x — torihiki-fix tick: fuzz suite 常設化 + JVM leg unblocked (landing evidence)

Branch `bot/torihiki-fix-20261019-0020`, base `origin/main` 74f38b0 (i53 fix
package PR #52 already landed there). This file is the fix-bot's landing
evidence; scores stay with the measurement bots.

## What landed (test/ only)

1. `test/torihiki/fuzz_seeded_test.cljk` — the seeded fuzz harness (falsify-3
   generator, frozen copy of `evidence/fuzz-seeded.cljk`) permanently wired
   into the suite, asserting INVARIANTS (domain, closed reason taxonomy,
   snapshot parity, fold determinism) rather than digests, plus apply-block
   end-to-end pins for the falsify-7/8 gates (REASON + VALUE). One change
   vs the draft: `gen-tx-heavy`'s deposit draw now composes two rng draws
   into [1, 2^53 - 2^27] (`rng-draw` alone caps at 2^31, so the old draw
   never reached the ceiling half of the domain).
2. `test/torihiki/snapshot_test.cljk:183` — bare `js/Error` ->
   `#?(:clj Exception :cljs js/Error)` (the pattern every other test file
   already uses). This was the ONLY JVM-loadability blocker in the suite.

## Measured (exact counts)

- Red (pre-fix engine: `git checkout 2a79ec5 -- src/` in the worktree,
  `kbb -M:test -n torihiki.fuzz-seeded-test`): 4 tests / 107 assertions,
  **23 failures**, 0 errors, reproduced on 2 runs. Failure classes exactly
  the fix package's scope: 16 heavy-mode folds left the i53 domain (1/seed),
  the crossing deposit landed (0 rejections), the over-cap order rested
  (resting-count 1).
- Green at 74f38b0 (`git checkout HEAD -- src/`): same command, 4/107, 0F 0E.
- Full suite, JS/sci: `kbb -M:test` -> **482 tests / 3415 assertions, 0
  failures, 0 errors** (baseline before this landing: 478/3308 0F 0E;
  +4 tests / +107 assertions).
- Full suite, JVM: `clojure -M:test` on a renamed copy -> **482 / 3415, 0
  failures, 0 errors** — same counters as sci. Recipe (measurement copy,
  repo untouched): copy src/ test/ deps.edn to scratch, rename every .cljk
  to .cljc, prepend a `dep-shims/` dir to :paths containing
  `kotoba/test.cljc` (the upstream file's clojure.test-compatible section,
  verbatim — upstream itself expands to `host/deftest` = clojure.test on the
  JVM), `kotoba/lang/edn.cljc` (pinned edn dep's .cljk renamed, with its
  `:kotoba/export` vector quoted — an unquoted symbol vector fails the JVM
  `ns` macro's attr-map evaluation), `kotoba/bytes.cljc` +
  `kotoba/bytes/{sha256,sha1}.cljc` (pinned bytes dep's .cljk renamed;
  sha256 is byte-identical to the .cljc in sibling shas). kotoba.lang.text,
  chain.core, merkle-sum.core already ship .cljc. Unshimmed rename-copy
  fails with `Could not locate kotoba/test__init.class...` (measured).

## Left

- maturity.md NEXT's remaining items are measurement-side (bench n>=1M
  3-run stability for 再現性 3) and the fuzz harness is now suite-connected
  (テスト/反証 4's software side; the bots re-measure).
- The JVM rename-copy recipe above is not automated in-repo; if the
  measurement bots want it permanent, it is a runner-side change.
