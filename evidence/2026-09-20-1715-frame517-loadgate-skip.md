# frame 517 load gate skip (2026-09-20 17:15, no code changes, cron)

負荷 17:15 uptime/sysctl 直測 1 回 (1-min **29.90** / 5-min **29.24** / 15-min **40.47**) — 全 window ≥20 で「全 window <20」不成立のため test/parity/fuzz/bench/falsify 新規実測 not-run (skip 記録 本ファイル + evidence/skip-check-0920-1715.txt)。

terminal 空出力 fault (既知高負荷型) 継続 — python driver (/Users/junkawasaki/.hermes/profiles/torihiki-falsify/cache/scratch/_f55_state.py 流, subprocess+redirect→read_file 迂回) で全項目取得。

- **HEAD 6552005** 直接実測 (frame-512 ledger commit, 2026-09-20 06:31): `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** (直接実測), `git diff 6552005c --stat -- src/ script/ deps.edn` = 0 → コード不変。working tree = status/maturity.md (M) + untracked evidence backlog のみ (src/ script/ 変更なし)。
- **OPEN 赤 8 site 引用行を 6552005 で直接行読み再検収 — 全引用現存有効**:
  - api.cljk:68 `(not (and (integer? qty) (pos? qty))) :bad-quantity` (i53/notional cap 無し)
  - api.cljk:202 `(not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount` (i53/balance-domain gate 無し)
  - clearing.cljk:711 `:deficit` `(fnil + 0)` sum 無検査
  - clearing.cljk:726 `:collateral` `(fnil + 0)` sum 無検査
  - funding.cljk:138 `:funding-residue` `(fnil + 0)` sum 無検査
  - liquidation.cljk:195 `:insurance-fund` `(fnil + 0)` sum 無検査
  - commit.cljk:113 `(- (reduce + 0 (map #(:sum % 0) leaves)) (long attested))` merkle aggregate
  - bench/torihiki/bench.cljk:112 `(let [q (bk/cancel! b oid)]` — 2 引数のまま (3-part fix f15+f16+f17 未着地)
- 正本引用 **frame 513 (2026-09-20 09:0x) 実測 base** 維持: suite **155** 両 runtime 同一カウント (357/915 0F/0E, 59 度目) / parity **52** (FLAT b4322bed…/STATE d1ebb9d3…) / seeded fuzz digest **52** byte-identical (f513-fuzz-{jvm,nbb}) / bench known-red (bench.cljk:112 2 引数) — src 不変 (diff 実測) のため引用有効。測定 copy **/tmp/tori-f506 現存** (次枠再利用可)。
- NEXT 未実測変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし (skip 枠のため仮説実測なし)。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- 枠番号注: 本日 ledger 登録は frame 514 (11:12 genuine-gate-passed budget-exhausted) 止み、evidence の frame514-loadgate-skip (13:08) / frame515 (12:17) / frame516 (14:10) は未登録 — 実走行順序で本枠 = **517**。13:08 の file は 11:12 の genuine 枠と番号衝突 (frame 514 二重命名) — 次回 genuine 枠で retro 登録候補。

次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **156** / parity **53** / fuzz **53** / bench **40 実行目**)。次枠番号 = **518**。
