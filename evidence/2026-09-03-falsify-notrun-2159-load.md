# falsify iteration — 2026-09-03 21:59 — NOT RUN (host load)

## 判定

- Host load 5min = **26.73** (> 20 閾値) → 実測 (fuzz digest 再現 / bench 3 条件) は**実施せず**。
  - 1min / 5min / 15min = 26.73 / 23.45 / 21.56。15min も閾値超過で、持続的過負荷。
- コード変更なし。

## 現状の観測 (torihiki_state.sh 21:59)

- OPEN 赤: **bench-tape-cancel-arity** (21:55 検出)。`bench/torihiki/bench.clj:112` が
  `torihiki.book/cancel!` の廃止済み 2 引数署名 `(bk/cancel! b oid)` を呼ぶ。
  bench harness は cancel! owner 必須化に未追従 → 既定 5M tape でクラッシュ。
  これにより再現性軸の bench 3 条件実測が現状不可能 (クラッシュが先に立つ)。
- NEXT: none (state script 由来)。

## 次反復への引き継ぎ

1. bench-tape-cancel-arity の修正は「no code changes」制約の外 (本反復は修正しない)。
   修正後でなければ bench 3 条件 (低負荷 3 回安定) の反証・再現性 3 判定は再開できない。
2. fuzz digest (JVM == NBB, 過去出力一致) は 4 度実測済 (最新 21:53, remeasure-2156)。
   負荷低下後の再実測は 5 度目として新規性薄い。優先は bench 側。
3. 負荷 (load avg 15→27 に悪化傾向) が続く場合、bench 実測の前提自体が崩れるため
   「低負荷」条件の定義 (ADR-2608052000 由来) の閾値明示を evidence 側で要確認。
