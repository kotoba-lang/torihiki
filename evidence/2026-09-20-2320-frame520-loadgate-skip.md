# frame 520 load gate skip (2026-09-20 23:2x, no code changes, cron)

負荷 23:13 pre-run (113.35/99.15/67.28) → 23:16 直測 1 回目 (1-min **124.13** / 5-min **107.34** / 15-min **76.36**) → 23:20 直測 2 回目 (1-min **155.33** / 5-min **137.69** / 15-min **97.91**, 再上昇) — 両測定とも全 window <20 不成立 (15-min まで >=20) ため test/parity/fuzz/bench/falsify 新規実測 not-run (skip 記録 本ファイル + evidence/skip-check-0920-2320.txt)。

terminal 空出力 fault (既知高負荷型) 継続 — python driver (subprocess+redirect→read_file 迂回) で全項目取得。top proc: top 74.3% + fileproviderd 61.9% + git 33.6% + tailscaled 33.2% + WindowServer 30.4% で host busy。

- **HEAD 6552005c7cc96b02b62c5613974b3ccf0de5e5d6** (frame-512 ledger commit) 直接実測: `git diff HEAD --stat -- src/ script/ deps.edn bench/` = **0 bytes** (直接実測) → コード不変。working tree = status/maturity.md (M) + untracked evidence backlog のみ。
- 正本引用 **frame 513 (2026-09-20 09:0x) 実測 base** 維持: suite **155** 両 runtime 同一カウント (357/915 0F/0E, 59 度目) / parity **52** (FLAT b4322bed…/STATE d1ebb9d3…) / seeded fuzz digest **52** byte-identical / bench known-red — src 不変 (diff 実測 0 bytes) のため引用有効。
- 測定 copy: **/tmp/tori-f495, /tmp/tori-f499, /tmp/tori-f506, /tmp/tori-f513 現存** (f513 依存の suite/parity/fuzz 枠は次 genuine 枠で再利用可能 — 2026-09-20 23:16 時点)。
- NEXT 未実測変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし (skip 枠のため仮説実測なし)。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 18 件, f8–f18 OPEN, frame-518 ledger 基準)。

次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **156** / parity **53** / fuzz **53** / bench **40 実行目**)。次枠番号 = **521**。
