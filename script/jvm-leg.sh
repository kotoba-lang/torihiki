#!/usr/bin/env bash
# The JVM leg shared by script/jvm-test.sh and script/jvm-bench.sh: the
# renamed-copy recipe, one command per alias.
#
#   script/jvm-leg.sh <alias> [alias args...]
#
# <alias> is a deps.edn alias run with `clojure -M:<alias>` — `test`,
# `parity`, `bench`, `bench-hl` all work.
#
# WHY THIS EXISTS. The sources in this repo are `.cljk` — the kotoba
# extension for portable Clojure/ClojureScript. The SCI-based runners (kbb,
# nbb) load `.cljk` by name; the JVM loads `.clj`/`.cljc` only, so a plain
# `clojure -M:<alias>` over the repo tree fails at namespace resolution
# ("Could not locate torihiki/bench__init.class...", measured 2026-10-24 —
# the same .cljk invisibility that made plain `clojure -M:test` discover 0
# tests). The measurement frames since the kotoba-migrate ran the JVM legs
# on an ad-hoc /tmp rename-copy recipe that lived in nobody's tree, and the
# two-runtime same-count claim became unreproducible twice (maturity frames
# 548->561, and again 2026-10-23 when the self-built shims died on
# kotoba/lang/edn's `:kotoba/export` vector). This script is that recipe,
# pinned to the classpath `clojure` actually resolves.
#
# THE RECIPE (measured green 2026-10-19 at 482/3415 by the fuzz-suite tick;
# re-measured at every landing since):
#
#   1. Copy src/ test/ bench/ resources/ deps.edn to scratch and rename
#      every .cljk to .cljc in the copy. The repo tree is never touched.
#   2. Generate a dep-shims/ directory from the gitlibs sources at the
#      SHAS `clojure -Spath` actually resolves, prepended to :paths:
#        - kotoba/test.cljc       : kotoba-lang/test's clojure.test-
#                                   compatible section, verbatim (its ns
#                                   docstring says the section forwards to
#                                   the host runner, clojure.test on the
#                                   JVM — this file is that section).
#        - kotoba/lang/edn.cljc   : kotoba-lang/edn's kotoba/lang/edn.cljk,
#                                   renamed, with its :kotoba/export vector
#                                   QUOTED. The unquoted symbol vector is
#                                   evaluated by the JVM ns macro's
#                                   attr-map and dies with "Unable to
#                                   resolve symbol: escape-controls"
#                                   (measured 2026-10-23).
#        - kotoba/bytes.cljc and kotoba/bytes/{sha1,256}.cljc :
#                                   kotoba-lang/bytes .cljk files renamed.
#      Unshimmed, the copy fails with "Could not locate
#      kotoba/test__init.class" (measured 2026-10-19): the deps are on the
#      classpath, but their `.cljk` sources are invisible to the JVM.
#   3. Run `clojure -M:<alias>` in the copy. For `test`, the counters must
#      match the SCI leg (`kbb -M:test`) exactly — that equality is the
#      cross-runtime claim. For `bench`, the counters must match the
#      deterministic tape (same n, same numbers, every run).
#
# KEEP=1 keeps the scratch copy and prints its path (on failure it is kept
# unconditionally). JVM_WORK=/path pins the scratch location.
set -euo pipefail

ALIAS="${1:?usage: script/jvm-leg.sh <alias> [args...] (aliases: test parity bench bench-hl)}"
shift

REPO="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${JVM_WORK:-$(mktemp -d "${TMPDIR:-/tmp}/torihiki-jvm.XXXXXX")}"
mkdir -p "$WORK"
cleanup() {
  if [ "${KEEP:-}" = "1" ] || [ "${1:-0}" != "0" ]; then
    echo "jvm-leg: scratch copy kept at $WORK" >&2
  else
    rm -rf "$WORK"
  fi
}
trap 'cleanup $?' EXIT

# 1 — the renamed copy
cp -R "$REPO/src" "$REPO/test" "$REPO/bench" "$REPO/resources" "$REPO/deps.edn" "$WORK/"
find "$WORK/src" "$WORK/test" "$WORK/bench" -name '*.cljk' | while read -r f; do
  mv "$f" "${f%.cljk}.cljc"
done

# 2 — dep shims, from the pins `clojure` resolves (never from a hand-picked
# sha: tools.deps takes the NEWEST sha it is shown, so the resolved pin is
# the only one that matters)
CP="$(cd "$WORK" && clojure -Spath)"
src_root() {
  printf '%s' "$CP" | tr ':' '\n' | grep -m1 "/io.github.kotoba-lang/$1/[0-9a-f]*/src$" || {
    echo "jvm-leg: dep $1 is not on the resolved classpath" >&2
    exit 1
  }
}
EDN_SRC="$(src_root edn)"
BYTES_SRC="$(src_root bytes)"
TEST_SRC="$(src_root test)"

SHIM="$WORK/dep-shims"
mkdir -p "$SHIM/kotoba/lang" "$SHIM/kotoba/bytes"

# The :kotoba/export vector is metadata for the kbb engine but the JVM ns
# macro evaluates an attr-map — quote the vector on the way in. No-op on
# files that carry no export vector.
quote_export() { sed "s/{:kotoba\\/export \\[/{:kotoba\\/export '[/"; }

quote_export <"$EDN_SRC/kotoba/lang/edn.cljk"  >"$SHIM/kotoba/lang/edn.cljc"
quote_export <"$BYTES_SRC/kotoba/bytes.cljk"        >"$SHIM/kotoba/bytes.cljc"
quote_export <"$BYTES_SRC/kotoba/bytes/sha1.cljk"   >"$SHIM/kotoba/bytes/sha1.cljc"
quote_export <"$BYTES_SRC/kotoba/bytes/sha256.cljk" >"$SHIM/kotoba/bytes/sha256.cljc"

# kotoba/io.cljc — the FORWARDING shim (unlike the renamed ones above).
# Upstream kotoba.io is a re-export hub that requires one repo per
# definition (kotoba.io.reader, kotoba.io.writer, ...), every one of them
# `.cljk` and invisible to the JVM; renaming the hub would rename the tip of
# an iceberg. deps.edn says what kotoba.io actually is here — "kotoba.io
# replaced clojure.java.io" — so the JVM shim forwards the documented
# surface to clojure.java.io and nothing more. torihiki.hyperliquid calls
# exactly one name (io/resource); anything beyond the six below fails at
# require time rather than resolving to something subtly different.
cat >"$SHIM/kotoba/io.cljc" <<'EOF'
(ns kotoba.io
  "JVM shim for kotoba-lang/io. That repo re-exports one-repo-per-definition
  namespaces, all `.cljk` and invisible to the JVM; deps.edn documents the
  contract as 'kotoba.io replaced clojure.java.io', so this shim forwards
  the documented surface to clojure.java.io. See script/jvm-leg.sh for why."
  (:refer-clojure :exclude [file-seq])
  (:require [clojure.java.io :as host]))

(def resource host/resource)
(def file host/file)
(def as-file host/as-file)
(def as-relative-path host/as-relative-path)
(def make-parents host/make-parents)
(def delete-file host/delete-file)
EOF

# kotoba/test.cljc — the clojure.test-compatible section of
# $TEST_SRC/kotoba/test.cljk, verbatim from its "clojure.test-compatible
# surface" marker to end of file. The upstream file's re-exports (the
# kotoba.test.* registry namespace family) are deliberately not shimmed:
# this suite calls only deftest/is/testing, and a copied re-export would be
# a second definition of an upstream name.
cat >"$SHIM/kotoba/test.cljc" <<'EOF'
(ns kotoba.test
  "JVM shim for kotoba-lang/test's clojure.test-compatible surface. The
  section below is verbatim from the pinned upstream kotoba/test.cljk
  (resolved via the pins in deps.edn); it forwards to the HOST runner, and
  on the JVM the host is clojure.test. See script/jvm-leg.sh for why."
  (:require #?(:clj [clojure.test :as host] :cljs [cljs.test :as host])))

;; ---------------------------------------------------------------------------
;; clojure.test-compatible surface -- forwards to the host runner.
;; Each macro expands to the host macro of the same name with the same
;; arguments, so the expansion a runner sees is the one it saw before the
;; call site was rewritten.
;; ---------------------------------------------------------------------------

(defmacro deftest
  "clojure.test/deftest (host). Defines a var with :test metadata, so the
  repo's runner (kbb -M:test, cognitect.test-runner, cljs.test/run-tests)
  discovers it."
  [name & body]
  `(host/deftest ~name ~@body))

(defmacro is
  "clojure.test/is (host). `(is (thrown? C body))` and
  `(is (thrown-with-msg? C re body))` are the host's own special forms and
  report exactly as before."
  ([form] `(host/is ~form))
  ([form msg] `(host/is ~form ~msg)))

(defmacro are
  "clojure.test/are (host): one `is` per row."
  [argv expr & args]
  `(host/are ~argv ~expr ~@args))

(defmacro testing
  "clojure.test/testing (host)."
  [string & body]
  `(host/testing ~string ~@body))

(defmacro use-fixtures
  "clojure.test/use-fixtures (host), :once or :each, for the namespace that
  expands it."
  [type & fns]
  `(host/use-fixtures ~type ~@fns))

#?(:cljs
   (defmacro async
     "cljs.test/async (host). ClojureScript only, as in the host."
     [done & body]
     `(host/async ~done ~@body)))

(def run-tests
  "clojure.test/run-tests (host): namespaces in, the host's summary out; the
  current namespace when called with none."
  host/run-tests)

(def successful?
  "clojure.test/successful? (host)."
  host/successful?)

(def report
  "clojure.test/report (host) -- the multimethod OBJECT, not a copy, so a
  `defmethod` through this name installs a method the host runner calls."
  host/report)
EOF

# 3 — the JVM leg
sed 's/:paths \["src" "resources"\]/:paths ["dep-shims" "src" "resources"]/' \
  "$REPO/deps.edn" >"$WORK/deps.edn"

cd "$WORK" && exec clojure -M:"$ALIAS" "$@"
