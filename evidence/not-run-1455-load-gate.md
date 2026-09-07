# not-run — host load gate (2026-09-04 14:55 JST)

目的: NEXT に従う falsify iteration (1 hypothesis / 1 measured verdict)。

## 実施しなかった理由

Host load gate 違反: load averages **52.50 49.07–53.89 (1/5/15min)** — 閾値 20 を大きく超過
(2026-09-04 14:55 JST 実測, `uptime`)。11 日連続 uptime の高負荷ホストで seeded fuzz / falsify
測定を実行するとタイミング依存の断定や不完全実行のリスクが高く、測定値の証拠価値がない。

## 仮説 (次回実行用・変更なし)

NEXT 先頭の validate-i53-halt fix スコープ検証用仮説:
「balance-domain gate (`collateral + amount > i53-max` 拒否) を validate 層で実装した場合、
falsify-8 の 3-deposit 列は 2 deposit 目で :bad-amount 拒否となり root 分岐は発生しない。
ただし累算 site (:deficit clearing.cljc:711 / :funding-residue funding.cljc:138 /
:fees-collected clearing.cljc:410/560) には gate がないため、falsify-9/10/11 列は依然 root 分岐する」
→ gate 実装後に両 runtime で実測する必要がある (今-cycle はコード変更禁止につき未実施)。

## 判定

not-run。測定なし、コード変更なし。成熟度スコア・NEXT 変更なし。
