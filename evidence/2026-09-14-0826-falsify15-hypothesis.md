# falsify-15 hypothesis (2026-09-14 08:26, cron frame 441)

## 仮説
bench-tape-cancel-arity (bench.cljk:112 2-arg cancel!) は bench 側の取り残しであり、
owner 付き 3 引数 `(bk/cancel! b owner oid)` に接続すれば rename-copy 経路の
`clojure -M:bench` (n=5,000,000) は既知赤 (ArityException) に到達せず完走し、
place と cancel の双方が実カウントされる。

## 予測
- 対照 (2-arg のまま): bench.cljc:112 で ArityException。
- 処置 (owner 接続, 接続値は place 側と同一の `(bit-and i 1023)`):
  BENCH_EXIT=0, ops=5,000,000, placed+cancelled > 0。

## 測定
- 装置: rename-copy /tmp/tori-falsify15 (byte 同一 .cljk->.cljc 改名, HEAD 8af801db,
  src/test/bench 43 file + deps.edn 複写, 改名後 .cljk 0)。
- 処置のみ 1 本 (対照は frame-439 bench-439.err が ArityException を実測済で引用)。
- bench.cljc への 1 行変更は測定用 copy のみ (src 本体は無変更, diff は
  evidence/falsify15-bench-owner-diff.txt に保存)。
