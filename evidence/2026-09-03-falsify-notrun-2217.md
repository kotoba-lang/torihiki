# falsify iteration — 2026-09-03 22:17 JST (not-run)

## 判定
- **status: NOT-RUN** (host load gate)

## 根拠
- torihiki_state.sh 22:17 実測: load averages **17.75 / 18.77 / 21.16**。
  1分値 17.75 は gate (<20) を下回るが 15分値 21.16 が閾値超過。重い JVM bench
  (既定 5M tape) を起動すれば負荷はさらに上昇し、計測結果の信頼性が失われる。
- status/maturity.md の NEXT セクション: `none` — 前回イテレーションが
  次仮説を登録していない。実行すべき仮説が未定義。

## OPEN 赤 (変化なし確認のみ・実行はしていない)
- bench-tape-cancel-arity (2026-09-03 21:55 検出):
  bench/torihiki/bench.cljk:112 が廃止済み 2 引数署名 `(bk/cancel! b oid)` を呼ぶ。
  本 iteration はコード変更禁止のため修正対象外。次回の仮説候補:
  「bench.clj を owner 必須化 3 引数署名に追従すれば 5M tape bench が
  低負荷時に 3 回連続安定する」→ 修正 + 低負荷時間帯 (load <10) で実測。

## 次回への申し送り
1. maturity.md に上記 bench harness 追従の仮説を NEXT として登録すること
   (コード変更はこの falsify ループ外で実施)。
2. host load 15分平均 <20 を確認してから bench 系 falsify を実行する。
