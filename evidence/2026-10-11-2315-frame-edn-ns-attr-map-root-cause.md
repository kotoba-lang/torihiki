# 2026-10-11 23:1x frame — kotoba.lang.edn JVM compile failure ROOT CAUSE CONFIRMED (upstream ns attr-map bug)

Load gate: 23:10 direct measure 14.38/14.53/15.21 (all <20) → PASSED. No suite/parity/fuzz run —
this frame is a measured root-cause verdict upstream of the frame-18:1x blocker (budget spent on
cause isolation, which is the re-anchor prerequisite).

## Hypothesis → verdict
H: `kotoba/lang/edn.cljc` (pinned 79dba32) "escape-controls unresolved" is a plain JVM compile
failure whose cause is determinable in-frame.
**V: CONFIRMED — stock JVM Clojure evaluates `(ns … attr-map)` metadata symbols. edn.cljc:7 is
`{:kotoba/export [escape-controls …]}` with unquoted symbols; the compiler compiles the attr-map
as def metadata and resolves `escape-controls` before line 537 defines it →
"Unable to resolve symbol: escape-controls in this context" at (edn.cljc:1:1).**

## Measured evidence (all /tmp/tori-c11, JVM clojure 1.12.0, HEAD 1ceb357)
1. Repro on target: `clojure -M -e "(require 'kotoba.lang.edn)"` → same error (also via
   `(require …)` standalone; resource URL confirmed = gitlibs edn/79dba32c8892058842c57783f06a2dfe4601aeb0).
2. Stack trace (full report): FnExpr/BodyExpr → **HostExpr$Parser → MapExpr → VectorExpr →
   analyzeSymbol `escape-controls`** — i.e. the vector inside the attr-map is ANALYZED AS CODE.
3. Minimal repro (3 independent runs):
   - `(eval '(ns probe.f "d" {:kotoba/export [no-such-sym-xyz]}))` → FAIL, cause chain =
     [CompilerException, RuntimeException "Unable to resolve symbol: no-such-sym-xyz"]
   - `load-file` of a file with same ns shape → identical failure at (file:1:1)
   - quoted attr-map variant and keyword-only attr-map not yet compiled in-run (program aborted
     early); not needed for the verdict.
4. Contrast (why only edn breaks): classpath-resolved `kotoba/lang/text.cljc` is sha
   **73bdb13a** and has NO `:kotoba/export` attr-map (ns form = docstring + (:refer-clojure …)
   only) → `(require 'kotoba.lang.text)` succeeds, **44 publics** measured. The edn pin 79dba32
   carries the attr-map → JVM-only break. SCI/nbb treat ns metadata as data, so nbb is unaffected
   (consistent with nbb suite being reachable in prior frames).

## Classification
**Upstream kotoba-lang/edn defect at pin 79dba32**: JVM-incompatible ns attr-map (Clojure
evaluates def metadata; text lib at the actually-resolved sha doesn't have this pattern). Not a
torihiki regression; introduced by the 2026-09-23 deps.edn switch to kotoba.lang.edn.

## Fix paths for NEXT (choose one, then re-anchor on 1ceb357)
- (a) advance edn pin to an upstream sha whose ns form drops/quotes the attr-map, or
- (b) pin an edn sha where exports use quoted vectors / strings, or
- (c) local deps.edn `:exclusions`+local override is NOT available for gitlibs — practical local
  stopgap: `:local/root` a patched edn checkout in a MEASUREMENT COPY only (never repo HEAD).
Then rerun: /tmp/tori-c11 `clojure -M:test` → nbb suite → parity/fuzz (frame 565 numbering:
suite 163 / parity 60 / fuzz 60 target counts per frame-564 plan).

## Quote for maturity.md (反証 axis)
falsify-16 (upstream): kotoba.lang.edn pin 79dba32 ns `:kotoba/export` attr-map breaks JVM compile
(root cause confirmed 3-way repro + text-lib contrast; evidence 2026-10-11-2315-frame-edn-ns-attr-map-root-cause.md).

## Housekeeping note (upstream report)
`torihiki_state.sh` line-3 `cd` target
`/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/torihiki` does not exist (repo is at
`/Users/junkawasaki/github/kotoba-lang/torihiki`); pre-run script aborted (exit 126) again this
frame. Needs the path fixed by the script owner.
