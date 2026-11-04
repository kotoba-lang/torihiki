#!/bin/sh
# Runs the Kotoba test programs (venue_test, venue_mt_test) against their modules
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
# build NAME CONTROL OUT MODULE... : one ns, the harness, the modules, then the test
build() {
  name=$1; control=$2; out=$3; shift 3
  { echo "(ns torihiki.$name-unit (:export [main]))"
    for f in "$@"; do tail -n +2 "$here/kotoba/torihiki/$f.kotoba"; done
    tail -n +2 "$here/kotoba/torihiki/test_harness.kotoba" | sed "s/^(def CONTROL 0)/(def CONTROL $control)/"
    tail -n +2 "$here/kotoba/torihiki/${name}_test.kotoba"
  } > "$out"
}
run_unit() {  # NAME MODULE...
  name=$1; shift
  build "$name" 0 "$tmp/$name.kotoba" "$@"
  build "$name" 1 "$tmp/$name-control.kotoba" "$@"
  "$amu" check "$tmp/$name.kotoba" >/dev/null || { echo "REFUSED: amu check failed for $name" >&2; exit 1; }
  out=$("$amu" run "$tmp/$name.kotoba") || { echo "REFUSED: $name did not run to completion (exit $?)" >&2; printf '%s\n' "$out" >&2; exit 1; }
  printf '%s\n' "$out"
  last=$(printf '%s\n' "$out" | tail -n 1)
  [ "$last" = "0" ] || { echo "REFUSED: $name: $last case(s) failed" >&2; exit 1; }
  cout=$("$amu" run "$tmp/$name-control.kotoba") || { echo "REFUSED: the $name control variant did not run" >&2; exit 1; }
  clast=$(printf '%s\n' "$cout" | tail -n 1)
  [ "$clast" = "1" ] || { echo "REFUSED: $name control expected 1 failure, got $clast" >&2; printf '%s\n' "$cout" >&2; exit 1; }
  echo "ok: $name passes; its control variant fails exactly once"
}
run_unit venue venue
run_unit venue_mt venue text_state venue_mt
run_unit venue_router venue text_state venue_fake venue_router
