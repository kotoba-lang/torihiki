# maturity remeasure 2026-09-04 01:56 (16th)

- No code changes since last remeasure (HEAD dd55c85 unchanged; only untracked evidence/, status/).
- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures, 0 errors.
- nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`:
  Ran 357 tests containing 915 assertions. 0 failures, 0 errors.
  (evidence/test-nbb-0156.out) — 16th identical-count run. Note: bare `nbb script/tests-on-nbb.cljk`
  fails with "Could not find namespace: torihiki.address-test"; classpath via script/nbb-classpath.cljk is required, as documented.
- validate-i53-halt and bench-tape-cancel-arity: both remain OPEN (no code changes; last confirmed
  evidence/falsify6-{jvm,nbb}.out and evidence/bench-0128.err).
- Scores unchanged from maturity.md.
