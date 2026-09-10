# maturity remeasure — 2026-09-09 13:05 JST

## 7 軸 re-measure 結果: **変更なし (正本 status/maturity.md のまま確定)**

| 軸 | score | 本 iteration の追加実測 |
|---|---|---|
| spec/契約 | 3 | 変動なし (consensus 接続仕様は未) |
| 実装 | 3 | 変動なし (consensus 接続なし) |
| テスト | 3 | test-396 (JVM) + test-nbb-396 (nbb) で 357 tests / 915 assertions / 0 failures が同一カウント **34 度目**実測 (evidence/test-396.out, test-nbb-396.out, 2026-09-09 12:45)。fuzz suite 未接続のため 4 は不変 |
| 反証 | 3 | 13 件で不変。最新は falsify-13 :insurance-fund 累算 overflow (2026-09-09 12:06–13:10 両 runtime 実測, falsify13-{jvm,nbb}.out, verdict 2026-09-09-falsify-13-insurance-fund-accum-overflow.md)。maturity.md の falsify-13 記載と一致 |
| 再現性 | 2 | bench-tape-cancel-arity が **27 実行目**で再確認 (evidence/bench-396.err 12:46, `ArityException at torihiki.bench/run-tape (bench.clj:112), Wrong number of args (2) passed to: torihiki.book/cancel!`, n=5,000,000 tape 生成後)。3 回安定条件は未達のまま |
| governor 統合 | 1 | 変動なし |
| 運用 | 1 | 変動なし |

## 備考
- 直前 iteration (2026-09-09 12:55) は host load > 20 で falsify not-run (evidence/2026-09-09-1255-maturity-remeasure-skip.md, skip-check-0909-1255.txt)。本 iteration は負荷低下後に成熟度のみ re-measure。
- OPEN 赤 2 件 (validate-i53-halt, cumulative-deposit-divergence) は未 fix。累算 site 4 site + :insurance-fund の実測裏付けは揃っており、NEXT fix パッケージ (validate i53 gate + balance-domain gate + 累算 sum gate + mul-rate / REDUCING 事前界限 + bench owner 3 引数) を優先。
- No code changes.
