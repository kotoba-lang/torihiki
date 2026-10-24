#!/usr/bin/env bash
# The JVM half of the cross-runtime state-root parity check, runnable from a
# clean checkout.
#
#   script/jvm-parity.sh
#
# The renamed-copy recipe (why it exists, what the shims are, KEEP=1 /
# JVM_WORK) lives in script/jvm-leg.sh — this wrapper only selects the
# `parity` alias. The digests it prints must match the ClojureScript half
# (script/nbb-parity.sh) byte for byte; that equality is the cross-runtime
# claim. What the digests ARE is pinned in torihiki.commit-test.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/jvm-leg.sh" parity "$@"
