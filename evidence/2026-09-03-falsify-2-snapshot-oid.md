# falsify-2 — snapshot/restore round-trip の free-slot 選択 (oid 生成の replica 一貫性)

- 日時: 2026-09-03 19:41 JST
- 主張 (status/maturity.md OPEN 赤 #2): snapshot/restore round-trip が
  「byte-identical canonical-bytes」でも、復元後の free-slot 選択が同一でないと
  order id 生成が replica 間で分岐する。この検証が test/snapshot_test.cljc に
  存在するか未確認だった。

## 実在確認 (test/torihiki/snapshot_test.cljk)

存在する。該当テスト 4 件:

- `the-next-slot-is-the-same-slot` — churned book (slot 再利用 + generation
  移動済み) を snapshot/restore し、両者に同条件で `place!` して **同一 oid が
  払い出されること** を直接検証 ("a restored book handed out a different order
  id from the one it restored")。旧 free LIST 実装で実際に分岐した箇所を突く
  テストとコメントに明記。
- `generations-of-freed-slots-are-carried` — 復元時に generation が reset されず
  stale cancel が継承者の注文を打たないこと。
- `the-high-water-mark-tracks-peak-resting-not-total-placed` — lowest-free
  選択のため 500 回 place/cancel しても hwm が 1 のまま (snapshot が小さい
  性質)。
- `applying-the-same-block-to-both-gives-the-same-root` — 復元後も同一ブロック
  適用で state root と `:rejected` が一致 (未知 oid cancel の拒否が replica 間で
  同一)。

実装側の根拠 (src/torihiki/book.cljk): free set は bit ladder で
「どの slot が占有されているか」の関数になっている → snapshot が resting
orders を運べば free set は暗黙に復元される (L133-137 コメント)。

## harness 実測

- `kbb -M:test --namespace torihiki.snapshot-test` (JVM):
  **Ran 10 tests containing 21 assertions. 0 failures, 0 errors.**
- 続けて全体 `kbb -M:test` (JVM):
  **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.**
- host load: 1min 14.59 / 5min 16.95 / 15min 16.66 → 実行可 (閾値 20 未満)。

## 判定

**survived (主張は反証されず)**。free-slot 選択 (oid 生成の replica 一貫性) の
検証は test/snapshot_test.cljc に既に存在し、churned fixture + 世代移動の上で
実測グリーン。byte-identical でも oid が分岐するケースは現行 bit-ladder 実装
では再現しなかった。OPEN 赤 #2 は解消 (反証不成立)。

## NEXT (提案)

falsify-3 — 上記は JVM 単一ランタイムの実測。nbb クロスランタイムで
snapshot round-trip の oid 一貫性も parity (script/tests-on-nbb.cljk) を
通すことで固定できる。
