# bench iteration not-run evidence — 2026-09-03 22:17 JST

## 判定: NOT-RUN (host load > 20)

cron 設定ルール「Host load > 20 → not-run evidence only」に基づき、
`clojure -M:test` / `clojure -M:bench` は実行せず記録のみ。

## 実測 load averages

- 22:14 (pre-run script): 17.70 / 18.70 / **21.62** (1/5/15-min)
- 22:17 (再計測):        16.34 / 18.43 / **21.02** (1/5/15-min)

15-min average が 2 回の計測でいずれも 20 を超過 (21.62 → 21.02)。
持続的な高負荷と判断しテスト/bench を実行しない。

## 補足 (コード変更なし)

- 前回 (21:52–53) の成熟度再計測: JVM / nbb とも 357 tests / 915 assertions 全緑
  (evidence/2026-09-03-maturity-remeasure-2156.md)。
- OPEN 赤 **bench-tape-cancel-arity** は未修正のまま
  (`bench/torihiki/bench.clj:112` が 2 引数 `(bk/cancel! b oid)` を呼出)。
  本イテレーションでもコード変更は行っていない。

## NEXT

- load が下がった次回イテレーションで `clojure -M:test` + `clojure -M:bench` 実行。
- bench harness の cancel! owner 必須化への追従が 3 回安定実測 (再現性 3) の前提。
