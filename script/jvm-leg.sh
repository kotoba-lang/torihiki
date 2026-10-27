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
#        - the WHOLE external closure: every resolved gitlibs src root,
#          renamed .cljk -> .cljc with its :kotoba/export vector quoted
#          (the unquoted symbol vector is evaluated by the JVM ns macro's
#          attr-map and dies with "Unable to resolve symbol:
#          escape-controls", measured 2026-10-23). Since the kotoba
#          monorepo move the deps are all .cljk under lib/<id>/src —
#          chain.core, ipld.core, multiformats.core, merkle-sum.core and
#          the rest — so renaming one or two files no longer reaches the
#          load path.
#        - kotoba/test.cljc       : kotoba-lang/test's clojure.test-
#                                   compatible section, verbatim (its ns
#                                   docstring says the section forwards to
#                                   the host runner, clojure.test on the
#                                   JVM — this file is that section).
#                                   Overwrites the blanket copy.
#        - kotoba/io.cljc — the FORWARDING shim (see below). Overwrites
#          the blanket copy.
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

# The :kotoba/export vector is metadata for the kbb engine but the JVM ns
# macro evaluates an attr-map — quote the vector on the way in. No-op on
# files that carry no export vector.
quote_export() { sed "s/{:kotoba\\/export \\[/{:kotoba\\/export '[/"; }

# Every dependency source on this classpath is `.cljk` — the kotoba
# extension, invisible to the JVM (measured 2026-10-28 after the kotoba
# monorepo move: chain.core, ipld.core, multiformats.core, merkle-sum.core
# are all .cljk under lib/<id>/src at the pinned shas). The dep-shims tree
# is the WHOLE external closure — every resolved gitlibs src root renamed
# to `.cljc`, export vectors quoted. The first classpath root wins a
# duplicate name, the same rule the JVM loader uses. The copy's own
# src/test/bench stay out of this (no /io.github.kotoba-lang/ in their
# paths). Two files are deliberately NOT taken from upstream: kotoba/test
# and kotoba/io — curated shims below overwrite whatever the blanket
# copied for them.
SHIM="$WORK/dep-shims"
mkdir -p "$SHIM"
printf '%s' "$CP" | tr ':' '\n' | grep '/io\.github\.kotoba-lang/' | while read -r root; do
  [ -d "$root" ] || continue
  find "$root" -type f | while read -r f; do
    rel="${f#"$root"/}"
    tgt="$SHIM/$(printf '%s' "$rel" | sed 's/\.cljk$/.cljc/')"
    [ -e "$tgt" ] && continue
    mkdir -p "$(dirname "$tgt")"
    quote_export <"$f" >"$tgt"
  done
done

# kotoba/test and kotoba/io are NO LONGER shimmed: at the monorepo pins
# both hubs are self-contained (kotoba.test's registry family and
# kotoba.io's definition family live in the same lib/<id>/src), and the
# blanket rename above brings the whole family across. The old curated
# forwarding shim for kotoba.io is measured DEAD at these pins — it
# shadows the real hub and fails with "No such var: kio/read-all-bytes"
# (measured 2026-10-28), because multiformats.core reads through the hub.

# 3 — the JVM leg
sed 's/:paths \["src" "resources"\]/:paths ["dep-shims" "src" "resources"]/' \
  "$REPO/deps.edn" >"$WORK/deps.edn"

cd "$WORK" && exec clojure -M:"$ALIAS" "$@"
