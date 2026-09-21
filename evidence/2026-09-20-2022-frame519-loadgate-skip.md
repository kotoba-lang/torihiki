# frame 519 load gate skip (2026-09-20 20:2x, no code changes, cron)

負荷 20:09 pre-run (24.73/23.36/27.90) → 20:13 直測 1 回目 (1-min **17.30** / 5-min **20.79** / 15-min **25.65**) → 20:21 直測 2 回目 (1-min **30.60** / 5-min **26.06** / 15-min **25.57**, 再上昇) — 両測定とも全 window <20 不成立のため test/parity/fuzz/bench/falsify 新規実測 not-run (skip 記録 本ファイル + evidence/skip-check-0920-2022.txt)。

terminal 空出力 fault (既知高負荷型) 継続 — python driver (subprocess+redirect→read_file 迂回) で全項目取得。top proc: python 94.1% CPU + WindowServer 49% で host busy。

- **HEAD 6552005c7cc96b02b62c5613974b3ccf0de5e5d6** 直接実測 (frame-512 ledger commit): `git diff HEAD --stat -- src/ script/ deps.edn bench/` = **0 bytes** (直接実測) → コード不変。working tree = status/maturity.md (M) + untracked evidence backlog のみ。
- **OPEN 赤 9 site 引用行を 6552005 で直接行読み再検収 — 全引用現存有効**:
  - api.cljk:68 `(not (and (integer? qty) (pos? qty))) :bad-quantity` (i53/notional cap 無し)
  - api.cljk:202 `(not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount` (i53/balance-domain gate 無し)
  - clearing.cljk:711 `:deficit` `(fnil + 0)` sum 無検査 (fx/check :deficit 併存)
  - clearing.cljk:726 `:collateral` `(fnil + 0)` sum 無検査
  - funding.cljk:138 `:funding-residue` `(fnil + 0)` sum 無検査
  - liquidation.cljk:195 `:insurance-fund` `(fnil + 0)` sum 無検査
  - commit.cljk:113 `(- (reduce + 0 (map #(:sum % 0) leaves)) (long attested))` merkle aggregate
  - commit.cljk:115 `reserves` (同系 aggregate)
  - bench/torihiki/bench.cljk:112 `(let [q (bk/cancel! b oid)]` — 2 引数のまま (3-part fix f15+f16+f17 未着地)
- 正本引用 **frame 513 (2026-09-20 09:0x) 実測 base** 維持: suite **155** 両 runtime 同一カウント (357/915 0F/0E, 59 度目) / parity **52** (FLAT b4322bed…/STATE d1ebb9d3…) / seeded fuzz digest **52** byte-identical / bench known-red — src 不変 (diff 実測) のため引用有効。
- 測定 copy: **/tmp/tori-f495, /tmp/tori-f499 現存** (tori-f506 / tori-f513 は 20:13 時点で不在 — f506 依存の suite/parity/fuzz 枠は次 genuine 枠で copy 再構築要)。
- NEXT 未実測変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし (skip 枠のため仮説実測なし)。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 18 件, f8–f18 OPEN, frame-518 ledger 基準)。

次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **156** / parity **53** / fuzz **53** / bench **40 実行目**)。次枠番号 = **520**。
