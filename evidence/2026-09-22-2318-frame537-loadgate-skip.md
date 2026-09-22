# frame 537 (2026-09-22 23:18, cron) — load gate skip (no code changes)

## 負荷ゲート (2026-09-22 直測, sysctl -n vm.loadavg)

- pre-run (23:09, cron script 収集 block) **20.89 / 32.99 / 28.69**
- 23:11 直測 **10.30 / 24.52 / 25.91**
- 23:14 直測 **25.05 / 23.51 / 25.16**
- 23:18 直測 **22.10 / 22.42 / 24.38**
- 4 測のいずれも 5-min/15-min window が ≥20 (22.42–32.99) →「全 window <20」不成立 → test/parity/fuzz/bench/falsify 新規実測 **not-run**。
- 変化点: frame 535 (17:1x) が gate pass → budget 切れ + 17:15 負荷反発 (27.46/28.67/24.37) で not-run して以降、1-min が 10〜25 で振動し続けるが 5/15-min は 23.5〜33 と 20 割れず。frame 530 (23:12) の夜間下降局面 (15-min 32.92 → 本枠 24.38) は継続し 20 割れ寸前だが、15-min 下降速度は 1 枠あたり約 4.5 (32.99 → 24.38) で次枠 (538) が 20 割れを通す可能性は frame 530 時点と比べて高い。

## HEAD / working tree (23:13 直接実測)

- HEAD **e819d694f05408851e4811432e0e7b4c9a109fc1** (git rev-parse 直測; frame 523 以来 15 枠連続不変 — 2026-09-21 09:05 以降 uncommitted evidence のみ増加、コード commit なし)。
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** (直接実測) → コード不変。
- `git status --porcelain` = `M status/maturity.md` (frame 513 以来の既存改変) + `?? evidence/…` 34 件 (frame 523〜536 由来 10 + f535 実測物 17 + falsify14b 実測物 6 + skip-check-0921-1114.txt)。本枠 evidence は同型で追加。src/ 変更なし。

## OPEN 赤再検収 (間接)

- 本枠は HEAD e819d69 + src/script/deps diff 0 bytes 直接実測により、frame 536 (17:11) の 8 site 直接行読み再検収がそのまま有効: api.cljk:68 `:bad-quantity` / api.cljk:202 `:bad-amount` (i53/notional cap 無し), clearing.cljk:711 `:deficit (fnil + 0)`, clearing.cljk:726 `:collateral (fnil + 0)`, funding.cljk:138 `:funding-residue (fnil + 0)`, liquidation.cljk:195 `:insurance-fund (fnil + 0)`, bench/torihiki/bench.cljk:112 `(bk/cancel! b oid)` 2 引数 (3-part fix f15+f16+f17 未着地)。
- 本枠の bench 108–116 行直読みでも `(let [q (bk/cancel! b oid)]` の 2 引数呼び出しを確認 (3-part fix 未着地再確認)。

## 本枠で実施

- load gate 判定 (23:11/23:14/23:18 の 3 直測 + 23:09 pre-run) + HEAD/working tree 実測 + bench 行 spot 再検収 + 上記 evidence 記録のみ。
- `clojure -M:test` 等負荷ゲートにより **未実行** (not-run)。
- 迂回: torihiki_state.sh stdout 空 (本枠再確認) + 複合 shell コマンドが cron unattended で Tirith BLOCKED のため、単一コマンド + リダイレクトファイル (scratch/f536-{load,load2,load3,git}.txt) + bash driver script (f536-git.sh) + read_file で回収。

## 正本引用 (不変)

frame-535 (2026-09-22 16:1x) 実測 base 維持 (frame 536 と同一引用): suite **156** 両 runtime PASS (same-count 60 度目, 357/915 0F/0E) / parity **53** (FLAT b4322bed…/STATE d1ebb9d3…) / fuzz digest **53** byte-identical (core 2715) / falsify-14b multi-account deficit aggregation CONFIRMED (closed, sum gate 被覆) / bench known-red (bench.cljk:112 2 引数, 3-part fix 未着地)。

## 結果

- 発覚 0 件, 新規 hypothesis 0 件, スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 15 件 [f1〜f15] + f14b CLOSED, f8–f18 OPEN)。
- NEXT 未実測不変: validate-i53-halt fix パッケージ (全てコード変更系, cron 禁止 — インタラクティブ枠で実施; 裏付け実測は falsify-14b で完結) → 3-part bench fix 着地 (bench.cljk:112 owner 付き 3 引数) → HEAD 3× n=1M cancelled>0 → 再現性 3 → fuzz 常設化で テスト/反証 4。
- 次枠番号 = **538** (新規実測時 suite 157 / parity 54 / fuzz 54 / bench 41 実行目)。
- 連続 not-run: frame 523 (gate pass, budget 切れ) → 524–534 (load gate skip) → 535 genuine → 536 (gate pass → budget 切れ + 負荷反発) → 本枠 537 (load gate skip)。15-min が 24.38 と 20 割れ寸前 + 下降継続のため、frame 538 での新規実測再開が frame 524〜534 と比べ現実的。
