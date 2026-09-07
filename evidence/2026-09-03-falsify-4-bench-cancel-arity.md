# falsify-4: bench-tape-cancel-arity — 生存 (crash 再現 + 部分実行時の黙示スキューを新規観測)

日時: 2026-09-03 23:33–23:37 JST / host load ~12–17 (runnable 枠内)
制約: コード変更なし (1 反復 = 1 hypothesis, 1 measured verdict)

## Hypothesis

`bench/torihiki/bench.clj:112` の `(bk/cancel! b oid)` (2 引数) は
`src/torihiki/book.cljc:566` の現行 `cancel! [b oid owner]` (owner 必須, 2-arity 削除済み)
と署名不整合であり、既定 5M tape では ArityException でクラッシュする — status/maturity.md NEXT の記述は正しい。

## 証拠 (静的)

- bench.clj:112 `(let [q (bk/cancel! b oid)]` — 2 引数呼び出し。
- book.cljc:566 `[^Book b oid owner]`、docstring が明示: "It is an argument rather than an optional one, and there is no two-argument arity left behind."

## 証拠 (動的, 実測)

1. `clojure -M:bench 200000` → **exit=0, クラッシュせず** (evidence/falsify-4-bench-2333.{out,err})。
   ただし `cancelled 0` — cancel 経路が一度も `cancel!` に到達していない。
   理由: run-tape:109 の ring offset `(bit-and (- w 1 (bit-and i 524287)) ring-mask)` は
   `w` が 524288 を超えるまで負で、負→bit-and で未書き込み slot (初期値 -1) を指すため
   `(pos? oid)` が常に false。**n < 524288 では bench は cancel を含まない workload を黙って測る**
   (192,855 ops/sec は cancel-free 記録であり、mix を測った数字ではない)。
2. `clojure -M:bench 1000000` → **exit=1, ArityException 再現** (evidence/falsify-4-bench-1m-2336.err):
   `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112).`
   `Wrong number of args (2) passed to: torihiki.book/cancel!`
   23:15 の bench-2315.err (既定 5M) と同一例外・同一行。

## Verdict

Hypothesis は**生存**。NEXT の診断は正確で、修正行は bench.clj:112 で間違いない。
ただし NEXT の「1 行修正で OPEN 赤が消える」には補正が要る:

- 修正は `(bk/cancel! b oid)` → `(bk/cancel! b oid (bit-and i 1023))` が最小
  (place! が同じ owner id を使っているため、ring の oid は全て同一 owner → cancel が必ず成功し、
  owner 検査は実質 exercise されない。mix の意図を保つには owner を place と cancel で分岐させる
  2 行規模になるが、それは次反復で判断)。
- **新規欠陥 (今回は直さない): run-tape:109 の ring offset 設計上、n ≤ 524287 では cancel が
  0 回のまま正常終了する。** 小さい n での bench 出力は "cancelled 0" と表示されるので
  気づけるが、3 回安定実測を n=1M 以上で行うこと (524288 以下だと再現性 3 の条件を
  形式的に満たしても cancel 経路を一度も踏んでいない測定になる)。

## Files

- evidence/falsify-4-bench-2333.out / .err (200k, exit=0, cancelled 0)
- evidence/falsify-4-bench-1m-2336.out / .err (1M, exit=1, ArityException bench.clj:112)
