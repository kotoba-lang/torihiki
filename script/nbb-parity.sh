#!/usr/bin/env bash
# The ClojureScript half of the cross-runtime state-root parity check,
# runnable from a clean checkout.
#
#   KOTOBA_CHECKOUTS=<dir-of-sibling-checkouts> script/nbb-parity.sh
#
# The recipe (why it exists, what it installs, KOTOBA_CHECKOUTS) lives in
# script/nbb-leg.sh — this wrapper only selects the driver
# (script/torihiki-parity-on-nbb.cljk). The digests it prints must match the
# JVM half (script/jvm-parity.sh) byte for byte; that equality is the
# cross-runtime claim. What the digests ARE is pinned in
# torihiki.commit-test.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/nbb-leg.sh" torihiki-parity-on-nbb.cljk "$@"
