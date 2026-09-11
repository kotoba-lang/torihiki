# Maturity remeasure — frame 392 (2026-09-09 11:44–11:56 JST)

Head at start/end: 323248b (frame 391). 1m load at start 9.98 (<20 gate) → genuine run.
**src DIFFERS from the canonical full-gate base e81a243 (frame 286)**: kotoba.lang.text
merge (9b0b760/0862e71) changed 9 src/script files. Per standing rule this frame is a
genuine measurement; the canonical citation base moves to frame 392 (not frame 286).

## Results

1. **JVM tests** `kbb -M:test` → evidence/test-jvm-392.out:
   **357 tests / 915 assertions, 0 failures, 0 errors.** exit=0.
2. **nbb tests** → evidence/test-nbb-392.out:
   **357 tests / 915 assertions, 0 failures, 0 errors, namespaces 17/17. TESTS-ON-NBB: pass.** exit=0.
   Same count both runtimes — 34th identical-count measurement.
3. **bench** `kbb -M:bench` → evidence/bench-392.{out,err}:
   **known red bench-tape-cancel-arity at 27th run**: `ArityException at bench.clj:112,
   Wrong number of args (2) passed to: torihiki.book/cancel!` (n=5,000,000 tape). exit=1.
   Unchanged from frames 286–391; 再現性 score stays 2.

## NEW pitfall (nbb classpath bootstrap broken by the merge)

Commit 0862e71 migrated `script/nbb-classpath.cljk` itself from `clojure.string` to
`kotoba.lang.text` — but this script is the tool that *produces* the classpath
containing kotoba/text. Chicken-and-egg: bare `kbb --backend sci script/nbb-classpath.cljk` now fails
with `Could not find namespace: kotoba.lang.text` (evidence/_392cp2.txt). The merge
commit explicitly says ".cljs is deliberately NOT touched", yet this .cljs was rewritten.
Working invocation (verified this frame):
`kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` → prints full pinned classpath
(evidence/_392cp4.txt), then `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` → pass.
This must be fixed in the nbb-classpath/bridge fix workstream; the doc-comment invocation
in nbb-classpath.cljs:6 and tests-on-nbb.cljs is now wrong.

## Scores

Unchanged: 3/3/3/3/2/1/1 (spec/impl/test/反証/再現性/governor/運用).
NEXT unchanged (validate-i53-halt fix package, bench cancel! arity fix), plus:
nbb-classpath bootstrap self-dependency fix.
