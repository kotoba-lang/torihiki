#!/usr/bin/env bash
# The JVM bench legs, runnable from a clean checkout — the runner behind the
# README throughput claim.
#
#   script/jvm-bench.sh [bench args...]          # :bench   (torihiki.bench)
#   script/jvm-bench.sh --hl [args...]           # :bench-hl (torihiki.hyperliquid)
#
# WHY THIS EXISTS. deps.edn says the :bench alias "must stay runnable or the
# claim rots", but nothing in the repo ran it: the benchmark sources are
# `.cljk`, so `clojure -M:bench` over the repo tree dies at namespace
# resolution ("Could not locate torihiki/bench__init.class...", measured
# 2026-10-24), and the README-named `kbb -M:bench` is invalid on its face —
# kbb runs aliases on its SCI host and the harness opens with
# `(set! *unchecked-math* :warn-on-boxed)`, a JVM-only special form
# ("Unable to resolve symbol: *unchecked-math*", measured 2026-10-24,
# kbb EXIT=1). Both benchmarks need the JVM; the renamed-copy recipe that
# made them run lived in nobody's tree until now (the same rot that took the
# two-runtime suite claim down twice before script/jvm-test.sh landed).
#
# The recipe itself is script/jvm-leg.sh (renamed .cljk->.cljc copy +
# pinned dep shims; KEEP=1 keeps the scratch copy, JVM_WORK pins it). This
# wrapper only selects the alias. `:bench-hl` takes
# [n-blocks block-size n-accounts] (default `40 2000 64`).
#
# The tape is deterministic (fixed LCG, never `rand`): the same n prints the
# same placed/cancelled/resting counts on every run and both runtimes — the
# throughput line varies with host load, the counts never do.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
if [ "${1:-}" = "--hl" ]; then
  shift
  exec "$DIR/jvm-leg.sh" bench-hl "$@"
fi
exec "$DIR/jvm-leg.sh" bench "$@"
