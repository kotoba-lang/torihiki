# 2026-11-03 14:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# environment reconstruction + baseline + design-correction probe). No src
# change, no PR. "not run" != "passed": the only green claimed below is the
# baseline suite, run and counted this tick.

Load gate: 14:18 uptime direct — 1.23 / 1.22 / 1.26, all windows < 20, gate
passed. NOTE: this host (jacob.local) had no ~/.hermes/scripts/torihiki_state.sh
(the profile scripts dir is empty); direct uptime per the chronic-fault
precedent in the maturity frames. Host was also missing the whole working
set: the torihiki checkout, the monorepo sibling, the kbb engine, the clojure
CLI and node_modules were ALL absent and were rebuilt this tick (see below).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261103-0222, branch
bot/torihiki-fix-20261103-0222 at f504e36 (the 02:2x handoff, wire-number-canon
scoped) — CONTINUED, kept for the next tick. maturity.md untouched.

## Measured this tick (exact counts)

- Baseline GREEN kbb/SCI (engine + suite unmodified): **600 tests / 5216
  assertions / 0 failures / 0 errors**, exit 0 (/tmp/kbb-baseline.out — tail:
  "Ran 600 tests containing 5216 assertions. 0 failures, 0 errors"). Matches
  PR #88's green exactly. Benign runner notes on stderr: stdlib sibling
  `error` not checked out at <kotoba-lang>/error; tools.namespace/tools.cli
  not on the classpath (test runner substitution, 36 namespaces).
- BROKEN run: NOT run. GREEN after fix: NOT run. JVM leg (script/jvm-test.sh):
  NOT run. nbb leg (script/nbb-test.sh): NOT run.

## Design correction (measured): the scoped decode is NOT `=`-equal on the JVM

Direct probe on clojure 1.12.0 (openjdk 27), this tick:

  (= 1e17 (bigint "100000000000000000"))  -> false
  (= 1 1.0)                                -> false
  (= -0.0 0)                               -> false
  (str 1e17)                               -> "1.0E17"
  (str (.toBigIntegerExact (BigDecimal. (double 1e17)))) -> "100000000000000000"
  (.toPlainString (BigDecimal. (double 5.5)))  -> "5.5"
  (.toPlainString (BigDecimal. (double 0.1)))  -> 0.1000000000000000055511151231257827021181583404541015625

Clojure `=` is TYPE-sensitive across numeric categories — the 02:2x scoped
design's decode claim ("Decode: JVM (bigint s) … both =-equal to the submitted
value") is FALSE as written for a submitted DOUBLE, and the same applies to
the bare-0 decode of -0.0 (its `=` pin would fail on the JVM leg only). The
digit strings themselves are correct and byte-identical across runtimes
(BigDecimal(double) constructor is exact and matches the JS BigInt binary
decomposition; 5.5 -> "5.5", 0.1 -> the full exact expansion above).

Next-tick fix for the LAW (do not re-litigate the digits — they are right):

- Pin the round trip at the WIRE level, which is runtime-independent and is
  the right law for a wire codec: `(= (tx->wire tx) (tx->wire (wire->tx
  (tx->wire tx))))` for the whole number space (1e17, -2.5e18, 5.5, 0.1,
  -0.0, 1e-300, 1.5e300), plus VALUE `=` pins only where the submitted type
  is recoverable (same-type round trips: 5.5, 0.1, 1e-300, 1.5e300 decode to
  doubles and `=` holds on both runtimes; -0.0 -> bare 0 pins through
  canon-tx where (long -0.0) = 0 and `=` holds). For "u" values pin the
  DECODED value with `==`/category-aware comparisons or as (bigint digits) on
  the JVM and js/Number on the JS legs — never a type-sensitive `=` across a
  submitted double and a decoded BigInt.
- Everything else in the 02:2x scoped design stands as scoped: the "u"/"d"
  wire literals (["u" "100000000000000000"] for {:claim 1e17}, ["d" "5.5"]
  for 5.5), in-domain 5.0 stays bare 5, NaN stays a loud symmetric refusal,
  +/-Inf -> ["d" "Infinity"]/["d" "-Infinity"], signing-payload renders kind
  through a total helper and the slots through the same number canon (JVM
  str of 1e17 is "1.0E17" — the measured Gap 3 divergence stands), and the
  wire-byte pins for {:claim 1e17} / 5.5 ride BROKEN-first on all three legs.

## Environment rebuilt on jacob.local (for the next tick: this is the recipe)

- repo: fresh clone https://github.com/kotoba-lang/torihiki (was absent),
  handoff branch worktree created from origin/bot/torihiki-fix-20261103-0222.
- sibling object store + engine: clones of
  https://github.com/kotoba-lang/kotoba.git and
  https://github.com/kotoba-lang/org-babashka-nbb.git at
  /Users/junkawasaki/github/kotoba-lang/ (kbb resolves the engine at
  KBB_HOME/../org-babashka-nbb/cli.js — both siblings required).
- kbb: /Users/junkawasaki/github/kotoba-lang/kotoba/bin/kbb on PATH.
- JVM: brew install clojure (openjdk 27 at /opt/homebrew/opt/openjdk — java
  is keg-only: prepend /opt/homebrew/opt/openjdk/bin to PATH).
- node_modules: `npm install` in the worktree (done; @noble/hashes present).
- git push works only with the macOS keychain helper
  (`git -c credential.helper=osxkeychain push …`; the plain store helper and
  gh's own token are both dead — `gh api user` -> "Requires authentication",
  `gh auth status` -> "The token in default is invalid"). PR create/merge via
  gh is BLOCKED on this host until a human runs `gh auth login`. The branch
  push above is the handoff channel.

## Not run / not claimed

BROKEN runs, all three GREEN legs, the JVM-nbb same-count claim: NOT RUN this
tick. No PR. Branch kept for handoff; the next tick starts from the
design-correction section above.
