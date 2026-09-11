# torihiki maturity re-measure — 2026-09-03 19:45 JST

Cron maturity-rank iteration. HEAD dd55c85, uncommitted: evidence/, status/ (only).
Host load 12.9-25.4 (1min avg 25 briefly exceeded the 20 gate during load spike;
test run started at ~19:41 when load was 16.9 → ran, see below).

## `kbb -M:test` (re-verified this run)

Ran 357 tests containing 915 assertions.
0 failures, 0 errors.

## falsify-2 第1段 (test 存在確認) — resolved this run

status/maturity.md の falsify 候補-2「test/snapshot_test.cljc に検証が存在するか
未確認」を確認した。正しいパスは `test/torihiki/snapshot_test.cljk`
(候補-2 の記述は test/ 直下と書いていたため ls で最初見つからなかった — test/torihiki/ 配下)。

存在し、しかも当該主張を直接カバーする test が並ぶ:

- `the-next-slot-is-the-same-slot` — 復元後の free-slot 選択が同一
  (「structural comparison では見えない部分」の明示)
- `a-cancel-issued-before-the-snapshot-still-works-after` — 復元前の oid で cancel が両側同結果
- `generations-of-freed-slots-are-carried` — 再利用 slot の generation 引継ぎ
- `the-canonical-bytes-survive-a-round-trip` / `an-edn-round-trip-survives-too` — byte-identical
- `applying-the-same-block-to-both-gives-the-same-root` — 復元側と生側の root 一致
  (unknown oid cancel も両側同一拒否)

すべて本日 19:41 の 357/915 全緑に含まれる。src 側は
`src/torihiki/book.cljk:242` `lowest-free` (ladder, lowest-set-bit)。
**「復元後に oid が分岐する」経路は test が既に塞いでいる** —
falsify-2 の harness 実測は test と同形になるため marginal gain が小さい。

## Scores

| 軸 | score | 変化 | 根拠 |
|---|---|---|---|
| spec/契約 | 3 | = | 変化なし。consensus 接続仕様は未 |
| 実装 | 3 | = | 変化なし |
| テスト | 3 | = | 357/915 全緑 (19:41 再実測)。snapshot 側の oid 一貫性 coverage も確認済 (今回) |
| 反証 | 2 | = | falsify-1 survived 済 (evidence/2026-09-03-falsify-1-binding-race.md)。falsify-2 は test 存在確認で半解決 (harness 未実施のため 3 にしない) |
| 再現性 | 2 | = | bench/test 再現可。seeded fuzz 未整備 — ここが次の最大ギャップ |
| governor 統合 | 1 | = | 変化なし |
| 運用 | 1 | = | 変化なし |

## NEXT

falsify-2 は第1段で解決 (coverage 実在)。第2段 harness は test と同形で
leverage が低いので NEXT を差し替え:

**NEXT: seeded fuzz harness — adversarial block 列を seed 固定で n 回 folding し、
(1) JVM/nbb の state root parity、(2) snapshot→restore 中断後も同一 oid/root を
維持することを randomized に実証する。** 1 本で 反証+1 (randomized 反証) と
再現性+1 (seeded, 再実行可能な反証ジョブ) を同時に狙える。README の不変条件
(i53 整数, floor division, ソート済み fold, 論理時刻) を fuzz が破らなければ
主張群 survived として evidence 化する。
