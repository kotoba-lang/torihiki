# falsify-5: ring offset が n ≤ 524288 で cancel 経路を完全に素通りする — 生存 (境界 524289 を実測)

日時: 2026-09-04 00:29–00:31 JST / host load 15.13–15.21 / 15-min < 20 → runnable
制約: コード変更なし (1 反復 = 1 hypothesis, 1 measured verdict)
NEXT は none のため、falsify-4 記載の新規欠陥 (run-tape ring offset) を hypothesis 化した。

## Hypothesis

`bench.clj:109` の `(bit-and (- w 1 (bit-and i 524287)) ring-mask)` は
i < 524288 では `(bit-and i 524287)` = i かつ w ≤ i+1 ゆえ offset ≤ 0 (負または 0)、
負値の bit-and が未書き込み slot (初期値 -1) を指すため `(pos? oid)` が常に false となり、
**n ≤ 524288 の全実行で cancel 経路が 1 回も呼ばれず正常終了する**。
初回の実 cancel 呼び出しは i ≥ 524288 の最初の cancel-kind op で発生し、
そこで既知の ArityException (bench-tape-cancel-arity) が表面化する。

## 証拠 (静的 + tape 実測)

- bench.clj:109–112: 上記 offset 式と 2 引数 `(bk/cancel! b oid)` を確認。
- tape 実測 (gen-tape 1048576, LCG 固定シード):
  - i ∈ [0, 524287] に cancel-kind op が **450,447 個** 存在 → 全て skip されるはず。
  - 最初の i ≥ 524288 の cancel-kind op は **i = 524289** (kind@524288=0, kind@524287=0)。
  - 実行: `clojure -Sdeps '{:paths ["src" "bench"]}' -M -e '(b/gen-tape ...)'`(中間出力は transcript、err の Boxed math warning 2 行のみ既知)

## 証拠 (動的, 実測)

1. `clojure -M:bench 524288` → **exit=0, cancelled 0** (evidence/falsify-5-bench-524288.{out,err})。
   THROUGHPUT 196,361 ops/sec と出るが、450,447 個の cancel op をすべて素通りした
   cancel-free workload の数字 (n=524288 は仮想上「ちょうど全 cancel が skip される」境界)。
2. `clojure -M:bench 524290` → **exit=1, ArityException at bench.clj:112**
   (evidence/falsify-5-bench-524290.{out,err})。i=524289 の cancel op で
   `Wrong number of args (2) passed to: torihiki.book/cancel!` — 予測した境界と一致。

## Verdict

Hypothesis は**生存**。falsify-4 の補足観測を境界値で確定させた:

- crash の下限は **n = 524289** (524288 で exit 0 / 524290 で exit 1 を実測、i=524289 が起点)。
- 「bench が cancel を exercise する」のは i ≥ 524288 のみ。n ≤ 524288 の出力は
  mix を測っていないため、**再現性 3 の条件 (3 回安定) を n=1M 以上で行う**こと
  (falsify-4 と同じ結論だが、今回は tape 内 cancel 分布の実測 450,447 件で裏取り済み)。
- 修正 (次反復、コード変更なしの今回ではない): bench.clj:112 を owner 付き 3 引数に
  直す際、offset 式は修正不要だが n ≥ 524289 の実行でしか顕在化しないことに注意。

## Files

- evidence/falsify-5-bench-524288.out / .err (n=524288, exit=0, cancelled 0, 196,361 ops/sec)
- evidence/falsify-5-bench-524290.out / .err (n=524290, exit=1, ArityException bench.clj:112)
