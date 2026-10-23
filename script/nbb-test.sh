#!/usr/bin/env bash
# The ClojureScript test leg, runnable from a clean checkout.
#
#   KOTOBA_CHECKOUTS=<dir-of-sibling-checkouts> script/nbb-test.sh
#
# The recipe (why it exists, what it installs, KOTOBA_CHECKOUTS) lives in
# script/nbb-leg.sh — this wrapper only selects the suite. The counters it
# prints must match the other legs (`kbb -M:test`, script/jvm-test.sh)
# exactly; that equality is the cross-runtime claim. It refuses to report a
# pass when fewer than the full set of test namespaces ran (see the floor in
# script/tests-on-nbb.cljk).
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/nbb-leg.sh" tests-on-nbb.cljk "$@"
