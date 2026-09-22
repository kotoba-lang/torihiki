# frame 536 (2026-09-22 17:0x JST, cron) — gate passed at frame open, budget exhausted + load rebound → 新規実測 not-run (no code changes)

- torihiki_state.sh 正常動作を再確認 (stdout 完全 — 記憶にある「cron 環境が異ホスト」という注記は本枠で False と判明: HOME=/Users/junkawasaki, repo + script 存在)。
- 負荷ゲート: 17:04–17:06 直測 2 回 **13.59–16.67 / 15.73–16.48 / 15.71–15.92** — 全 window <20 (frame 530 以来 6 枠ぶりの通過判定) → suite/parity/fuzz/bench 新規実測に着手したが **実行時間予算切れ** (nbb 2-stage classpath + JVM suite は完了前)。
- 17:15 直測で負荷が **27.46 / 28.67 / 24.37 に反発** (全 window ≥20) — 測定着手の機会は消滅。budget + 負荷反発の複合で not-run。発見は測定なしでは成立しないため skip 判定は正しい。
- HEAD **e819d694f05408851e4811432e0e7b4c9a109fc1** 直接実測 (git rev-parse; frame 523 以来 14 枠連続不変)。
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** → コード不変, frame-513/535 canonical 引用有効。
- frame 535 実測物を spot 再検収 (直接読み): f535-test-jvm.out tail = **357 tests / 915 assertions, 0 failures, 0 errors**; f535-test-nbb.out tail = 同一 + namespaces 17/17 + TESTS-ON-NBB: pass; f535-parity-jvm.out tail = FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true (baseline 同一); f535-parity-nbb.out = fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass; fuzz sizes JVM 2733 / nbb 2715 bytes (frame-513 core 2715 baseline と同一形状)。→ suite 156 / parity 53 / fuzz 53 の frame-535 実測は本枠時点でも正本引用として有効。
- OPEN 赤 8 site を e819d69 直接行読みで再検収 — **全現存**: api.cljk:68 `:bad-quantity` (i53/notional cap 無し), api.cljk:202 `:bad-amount` (i53 cap 無し), clearing.cljk:711 `:deficit (fnil + 0)` + per-delta fx/check, clearing.cljk:726 `:collateral (fnil + 0)`, funding.cljk:138 `:funding-residue (fnil + 0)`, liquidation.cljk:195 `:insurance-fund (fnil + 0)`, bench/torihiki/bench.cljk:112 `(bk/cancel! b oid)` 2 引数 (3-part fix f15+f16+f17 未着地を再確認)。
- 測定コピー /tmp/tori-f506 現存確認 (script 5 ファイル, kotoba/ intact, deps.edn git-pin; text/src + chain/node_modules + amu も現存 → 次 genuine 枠の suite/parity/fuzz スロット全て即実行可能)。
- 発覚 0 件, 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 19 件, f8–f18 + f14b OPEN)。
- NEXT 未変: validate-i53-halt fix パッケージ (falsify-14b 測定済のため累算 sum gate 裏付けは complete — 残 work は全部コード変更系, cron 禁止・インタラクティブ枠) → 3-part bench fix 着地 → HEAD 3× n=1M cancelled>0 → 再現性 3 → fuzz suite 常設化。
- 連続 not-run: frame 523 (gate pass, budget 切れ) → 524–534 (load gate skip) → 535 genuine → 本枠 536 (gate pass → budget 切れ + 負荷反発)。次枠 = **537** (新規実測時 suite 157 / parity 54 / fuzz 54 / bench 40 実行目)。frame 535 の 17:1x 時点では 5-min/15-min が 20 超で着手しつつ全 run EXIT=0 完走の前例あり — 次枠は「全 window <20 + 予算残存」の両立を開始直前に確認すること。
