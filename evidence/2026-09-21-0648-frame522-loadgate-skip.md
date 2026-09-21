# frame 522 load gate skip (2026-09-21 06:1x–06:4x, no code changes, cron)

負荷 06:14 pre-run (33.43/27.56/23.51) → 06:19 直測 1 回目 (1-min **15.09** / 5-min **24.10** / 15-min **23.18**) → 06:21 直測 2 回目 (1-min **10.96** / 5-min **19.74** / 15-min **21.58**, 1-min/5-min 下降継続だが 15-min は >=20) → 06:48 直測 3 回目 (1-min **22.84** / 5-min **29.25** / 15-min **29.09**, 再上昇) — 06:21 時点で 15-min のみ >=20、06:48 時点では全 window 再逆 >=20 で復帰したため test/parity/fuzz/bench/falsify 新規実測 not-run (skip 記録 本ファイル + evidence/skip-check-0921-0616.txt)。frame 521 (00:2x) に続き 2 フレーム連続の load gate skip。

terminal 空出力 fault (既知高負荷型) 継続 — redirect→read_file 迂回で全項目取得 (/tmp/f492_{git,status,head,diff,tmp,time*,load*}.txt)。

- **HEAD 6552005c7cc96b02b62c5613974b3ccf0de5e5d6** (frame-512 ledger commit) 直接実測: `git diff HEAD --stat -- src/ script/ deps.edn bench/` = **0 bytes** (直接実測, 空ファイル) → コード不変。working tree = status/maturity.md (M) + untracked evidence backlog のみ (git status --porcelain 直接実測, frame 521 と同一)。
- 正本引用 **frame 513 (2026-09-20 09:0x) 実測 base** 維持: suite **155** 両 runtime 同一カウント (357/915 0F/0E, 59 度目) / parity **52** (FLAT b4322bed…/STATE d1ebb9d3…) / seeded fuzz digest **52** byte-identical / bench known-red — src 不変 (diff 実測 0 bytes) のため引用有効。
- 測定 copy: **/tmp/tori-f495, /tmp/tori-f499, /tmp/tori-f506, /tmp/tori-f513 現存** (ls -d 直接実測, 2026-09-21 06:1x 時点) — f513 依存の suite/parity/fuzz 枠は次 genuine 枠で再利用可能。
- NEXT 未実測変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし (skip 枠のため仮説実測なし)。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 18 件, f8–f18 OPEN, frame-518 ledger 基準)。

次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **156** / parity **53** / fuzz **53** / bench **40 実行目**)。次枠番号 = **523**。
