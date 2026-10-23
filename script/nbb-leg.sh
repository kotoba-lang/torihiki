#!/usr/bin/env bash
# The nbb leg shared by script/nbb-test.sh: the pins-based classpath and the
# one JS dependency, one command per script.
#
#   script/nbb-leg.sh <script-name.cljk> [args...]
#
# <script-name.cljk> is looked up under script/ — `tests-on-nbb.cljk` (the
# suite), `kotoba-parity.cljk` (the two digests), `loads-on-nbb.cljk`
# (require-only) all work.
#
# WHY THIS EXISTS. The ClojureScript invocation lived in README prose and
# maturity-frame notes as a two-stage classpath dance, and it failed in a
# different place depending on which convention the caller remembered:
#
#   - a clean checkout carries package.json but no node_modules, so the
#     suite dies at require time with "Cannot find module
#     '@noble/hashes/sha2.js'" (measured 2026-10-24 on the README command
#     as written, exit 1);
#   - the header of script/tests-on-nbb.cljk named a runner
#     (script/run-nbb-tests.cljs) that has never existed — flagged in
#     evidence/2026-09-03-maturity-remeasure-2100.md and still true the day
#     this leg landed;
#   - the measurement frames needed NODE_PATH pointed at a sibling
#     checkout's node_modules and the classpath generator bootstrapped with
#     a text/src prepend, neither of which any file in this repo recorded.
#
# This script is that recipe, in-tree — the counterpart of script/jvm-leg.sh
# for the runtime that SHIPS (torihiki-node compiles this engine to a Worker
# bundle; the JVM suite is the one nobody deploys).
#
# WHAT IT DOES.
#   1. Installs the JS dependency (@noble/hashes, from package.json /
#      package-lock.json) into <repo>/node_modules when it is missing.
#      Node resolves requires upward from the requiring file, so an in-repo
#      node_modules needs no NODE_PATH. Offline fallback: set NODE_PATH to a
#      tree that already carries @noble/hashes (the maturity frames used
#      <checkouts>/chain/node_modules) and the install is skipped.
#   2. Builds the classpath with script/nbb-classpath.cljk — from the
#      deps.edn PINS, never from wherever the sibling checkouts happen to
#      be. That script exits 2 naming the dependency it cannot resolve
#      rather than printing a short classpath; a short one would run a
#      subset and print a pass.
#   3. Runs `kbb --backend sci --classpath "$CP" script/<name>` from the
#      repo root (the classpath's "src"/"test" entries are CWD-relative).
#
# KOTOBA_CHECKOUTS=<dir-of-sibling-checkouts> selects where the pinned
# commits are extracted from (they are used as an object store — see
# script/nbb-classpath.cljk). It defaults to the sibling-checkout layout of
# this workspace (<repo>/../<name>); a worktree or a plain clone elsewhere
# must set it.
set -euo pipefail

SCRIPT="${1:?usage: script/nbb-leg.sh <script-name.cljk> [args...] (under script/: tests-on-nbb.cljk kotoba-parity.cljk loads-on-nbb.cljk)}"
shift

REPO="$(cd "$(dirname "$0")/.." && pwd)"

# 1 — the JS dependency
if [ ! -d "$REPO/node_modules/@noble/hashes" ]; then
  have_node_path=0
  if [ -n "${NODE_PATH:-}" ]; then
    IFS=':' read -r -a _np <<< "$NODE_PATH"
    for _d in "${_np[@]}"; do
      if [ -d "$_d/@noble/hashes" ]; then have_node_path=1; break; fi
    done
  fi
  if [ "$have_node_path" = 0 ]; then
    if ! (cd "$REPO" && npm ci --no-audit --no-fund); then
      echo "nbb-leg: node_modules/@noble/hashes is missing and npm ci failed." >&2
      echo "nbb-leg: offline — set NODE_PATH to a node_modules that carries @noble/hashes (e.g. <checkouts>/chain/node_modules)." >&2
      exit 3
    fi
  fi
fi

# 2 — the pins classpath (exits 2 naming the unresolvable pin rather than
# printing a short one)
CP="$(cd "$REPO" && kbb --backend sci script/nbb-classpath.cljk)"

# 3 — the leg
cd "$REPO" && exec kbb --backend sci --classpath "$CP" "script/$SCRIPT" "$@"
