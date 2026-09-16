# falsify-15 verdict (2026-09-14 08:26-08:3x, cron frame 441): bench rename-copy owner-wired run — PARTIAL SURVIVE (harness passes, cancel count = 0 が新規発見)

## 判定
- **CONFIRMED (前半)**: bench-tape-cancel-arity (bench.cljk:112 2-arg cancel!) は bench 側の
  取り残しであり、owner 付き 3 引数に接続した rename-copy 経路の `clojure -M:bench`
  (n=5,000,000) は **ArityException に到達せず BENCH_EXIT=0 で完走** (初めての完走)。
- **新規発見 (後半)**: しかし実測値は **cancelled = 0, placed 2,251,559 / 5,000,000 ops**。
  2-arg 時代と同じ workload 形状 (cancel-free) のままで、修正済み (owner 接続) を入れても
  tape が cancel を実行していない。つまり bench.clj の cancel 選択条件 (slot/ring) は
  owner 接続後も cancel に落ちていない。再現性 3 の条件「n ≥ 1M で 3 回安定」の
  **前提 (cancel が実際に走る)** が未成立のまま。

## 実測
- 装置: rename-copy /tmp/tori-falsify15 (byte 同一 .cljk->.cljc 改名, HEAD 8af801db,
  src+test+bench 43 file + deps.edn 複写, 改名後 .cljk 0 file)。
- 対照 (引用): frame-439 bench-439.err = ArityException at bench.cljc:112 cancel! 2-arg
  (BENCH_EXIT=1, 既知赤 33 実行目)。
- 処置: bench.cljc:112 の `(bk/cancel! b oid)` を `(bk/cancel! b (bit-and i 1023) oid)` に
  1 行接続 (owner 値は place 側と同一)。diff は evidence/falsify15-bench-owner-diff.txt。
- 結果: BENCH_EXIT=0, operations 5,000,000 / placed 2,251,559 / **cancelled 0** /
  resting at end 1,879,250 / elapsed 30.063 s / THROUGHPUT 166,317 ops/sec /
  latency 6013 ns/op (evidence/falsify15-bench.out, stderr は boxed math warning のみ
  evidence/falsify15-bench.err)。

## 解釈
- ArityException 赤は owner 接続で消える (33 連続赤は harness 連番の上限)。これは
  falsify-5 (cancel 経路は n ≥ 524289 で crash しない) と整合。
- だが cancelled=0 は bench harness の tape 生成/選択側の問題で、cancel 経路の
  実測が取れていない。このままでは「3 回安定」を取っても cancel 経路を 1 度も通らない。
- NEXT への含意: bench-tape-cancel-arity の fix は **(1) bench.clj:112 owner 接続**
  (実測済: 今回の 1 行) **+ (2) tape 側 cancel が実行される条件の確認/修正**
  (ring slot が cancel 分岐に落ちるための w/ slot 計算の検収) の 2 点が必要。

## 影響
- src 本体無変更 (git diff HEAD -- src/ script/ deps.edn = 0, HEAD 8af801db 不変)。
- 測定用 copy への 1 行変更は evidence に diff で固定済み。
- スコア変動なし (再現性 2 は cancel 3 回安定不達のため維持)。
- bench 赤累計 33 実行目で消化完了 (ArityException は消えたが cancel=0 が新たな欠陥)。
