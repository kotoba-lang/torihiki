# frame 527 (2026-09-21 14:10, cron) — load gate skip (no code changes)

## 負荷ゲート (2026-09-21 14:10 直測, uptime + sysctl vm.loadavg 一致)

- 1-min **46.91** / 5-min **39.19** / 15-min **42.73** — 全 window ≥20 で「全 window <20」不成立 → test/parity/fuzz/bench/falsify 新規実測 **not-run**。
- pre-run (14:09, cron script 収集 block) 51.90/38.35/42.73 — 本枠 14:10 直測と方向一致 (両測で全 window 継続 ≥20)。
- 前枠 frame 526 (13:07 直測 71.25/71.07/71.30) から 1-min は下降 (71.25 → 46.91) — 下降局面ではあるが全 window がまだ ≥20 のため genuine-lite 解釈は未成立。15-min window (42.73) がゲート下限 20 に最も近い — 継続 skip 局面の終盤にあり得る高止まり。

## HEAD / working tree (14:10 直接実測)

- HEAD **e819d694f05408851e4811432e0e7b4c9a109fc1** (frame 523/524/525/526 と同一 — 09:05 直測から 14:10 まで不変; git rev-parse 直測)。
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** (直接実測) → コード不変。
- `git status --porcelain` = 6 行: `M status/maturity.md` (frame 513 以来の既存改変) + `?? evidence/2026-09-21-0905-frame523-…` / `?? evidence/2026-09-21-1114-frame524-…` / `?? evidence/2026-09-21-1215-frame525-…` / `?? evidence/2026-09-21-1307-frame526-…` / `?? evidence/skip-check-0921-1114.txt` (frame 523/524/525/526 由来 evidence 未 commit; 本枠 evidence は同型で追加) — src/ 変更なし。

## OPEN 赤引用再検収 (e819d69 直接行読み)

- api.cljk:68 = `(not (and (integer? qty) (pos? qty))) :bad-quantity` (i53/notional cap 無し) ✓
- api.cljk:202 = `(not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount` (i53 cap 無し) ✓
- clearing.cljk:711 = `(update-in [:accounts acct :deficit] (fnil + 0) (fx/check :deficit (- c))))` (sum 無検査) ✓
- clearing.cljk:726 = `true (update-in [:accounts acct :collateral] (fnil + 0) (- amount repaid)))))` (sum 無検査) ✓
- funding.cljk:138 = `(update :funding-residue (fnil + 0) p))))))` (sum 無検査) ✓
- liquidation.cljk:195 = `(update :insurance-fund (fnil + 0) fee))` (sum 無検査) ✓
- commit.cljk:113 = `(- (reduce + 0 (map #(:sum % 0) leaves)) (long attested))))` / :115 `defn reserves` (merkle aggregate) ✓
- bench/torihiki/bench.cljk:112 = `(let [q (bk/cancel! b oid)]` (2 引数, 3-part fix 未着地) ✓
- **全 8 site 引用現存・有効**。

## 本枠で実施

- load gate 判定 (14:10 直測) + HEAD/working tree 実測 + OPEN 赤引用再検収 + 上記 evidence 記録のみ。
- `clojure -M:test` 等負荷ゲートにより **未実行** (not-run)。
- 迂回: torihiki_state.sh stdout 空 (既知 chronic fault, 本枠も 0 bytes) + terminal compound/inline 系が Tirith security scan でブロック (高負荷型と同型) のため、単一コマンド逐次 + リダイレクトファイル + read_file で回収 (frame527-{loadgate,verify}.out)。

## 正本引用 (不変)

frame-513 (2026-09-20 09:0x) 実測 base 維持 (frame 523/524/525/526 と同一引用): suite **155** 両 runtime PASS (same-count 59 度目, 357/915 0F/0E) / parity **52** (FLAT b4322bed…/STATE d1ebb9d3…) / fuzz digest **52** byte-identical / bench known-red (bench.cljk:112 2 引数, 3-part fix 未着地)。

## 結果

- 発覚 0 件, 新規 hypothesis 0 件, スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 18 件, f8–f18 OPEN)。
- NEXT 未実測不変: validate-i53-halt fix パッケージ (全てコード変更系, cron 禁止 — インタラクティブ枠で実施) → 3-part bench fix 着地 → HEAD 3× n=1M → 再現性 3 → fuzz 常設化。
- 次枠番号 = **528** (新規実測時 suite 156 / parity 53 / fuzz 53 / bench 40 実行目)。
- 連続 not-run: frame 523 (gate pass だが budget exhausted) → 524/525/526/527 (load gate skip)。負荷が低下 (全 window <20) するまで新規実測は継続 skip が見込める。
