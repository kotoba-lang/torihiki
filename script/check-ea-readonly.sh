#!/bin/sh
# Fails if the MT5 bridge EA contains a trading call, a trade header, or a
# socket read. A text check, not a proof: it is the tripwire that makes adding
# one a deliberate act. Run from the repository root.
set -eu
f=${1:-mt5/TorihikiReadOnlyBridge.mq5}
# Comments are removed first so the header's own wording cannot trip it.
body=$(sed -e 's://.*$::' "$f")
bad=$(printf '%s\n' "$body" | grep -n -E 'OrderSend|OrderSendAsync|OrderCheck|CTrade|Trade\.mqh|PositionOpen|PositionClose|PositionModify|OrderOpen|OrderDelete|OrderModify|SocketRead|SocketTlsRead|WebRequest|FileOpen|ShellExecute|#import' || true)
if [ -n "$bad" ]; then
  echo "REFUSED: forbidden token(s) in $f:" >&2
  echo "$bad" >&2
  exit 1
fi
echo "ok: $f has no trading, read, file or import calls"
