#!/usr/bin/env bash
# The JVM test leg, runnable from a clean checkout.
#
#   script/jvm-test.sh [cognitect.test-runner args...]
#
# The renamed-copy recipe (why it exists, what the shims are, KEEP=1 /
# JVM_WORK) lives in script/jvm-leg.sh — this wrapper only selects the
# `test` alias. The counters it prints must match the SCI leg
# (`kbb -M:test`) exactly; that equality is the cross-runtime claim.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/jvm-leg.sh" test "$@"
