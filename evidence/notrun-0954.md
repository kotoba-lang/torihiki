# bench iteration not-run — 2026-09-04 09:54 JST

- 判定: **not-run** (host load > 20 gate)
- load averages 実測:
  - 09:52 `14.69 21.06 23.13`
  - 09:54 (2 分再計測) `26.80 22.60 23.36` — 1min が再急上昇し 5/15min も継続 > 20
- `kbb -M:test` 未実施 / `kbb -M:bench` 未実施 (job 指令: not-run evidence only)
- コード変更なし
- 最新の成功計測は 2026-09-04 0839/0840 を参照: test-0839.out (JVM) + test-nbb-0840.out (nbb), 357 tests / 915 assertions 全緑
