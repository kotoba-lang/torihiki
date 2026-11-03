#!/bin/sh
# Runs kotoba/torihiki/venue_test.kotoba against kotoba/torihiki/venue.kotoba
# with the Amu native CLI (no JVM, no nbb). Joins the two files under one ns,
# runs the result, and requires the printed answer to be 0; then builds the
# CONTROL 1 variant and requires exactly 1 failure, so a harness that cannot
# fail cannot pass.
#
#   AMU_HOME=<kotoba-lang/amu checkout> script/kotoba-venue-test.sh
set -eu
here=$(cd "$(dirname "$0")/.." && pwd)
amu=${AMU_BIN:-${AMU_HOME:?set AMU_HOME or AMU_BIN}/bin/amu}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
build() {  # $1 = CONTROL value, $2 = output
  { echo '(ns torihiki.venue-unit (:export [main]))'
    tail -n +2 "$here/kotoba/torihiki/venue.kotoba"
    tail -n +2 "$here/kotoba/torihiki/venue_test.kotoba" | sed "s/^(def CONTROL 0)/(def CONTROL $1)/"
  } > "$2"
}
build 0 "$tmp/unit.kotoba"
build 1 "$tmp/control.kotoba"
"$amu" check "$tmp/unit.kotoba" >/dev/null || { echo "REFUSED: amu check failed" >&2; exit 1; }
out=$("$amu" run "$tmp/unit.kotoba") || { echo "REFUSED: the unit did not run to completion (exit $?)" >&2; printf '%s\n' "$out" >&2; exit 1; }
echo "$out"
last=$(printf '%s\n' "$out" | tail -n 1)
[ "$last" = "0" ] || { echo "REFUSED: $last case(s) failed" >&2; exit 1; }
cout=$("$amu" run "$tmp/control.kotoba") || { echo "REFUSED: the control variant did not run" >&2; exit 1; }
clast=$(printf '%s\n' "$cout" | tail -n 1)
[ "$clast" = "1" ] || { echo "REFUSED: control expected 1 failure, got $clast" >&2; printf '%s\n' "$cout" >&2; exit 1; }
echo "ok: all cases pass; the control variant fails exactly once"
