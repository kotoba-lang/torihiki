# 2026-09-19 14:09 frame 504 load gate skip (no code changes, cron)

- 負荷: pre-run (torihiki_state.sh block) 14:09 **16.61 / 16.86 / 15.77** 全 window <20 → gate 一時成立
  と見えたが、直測 14:13 **30.46 / 24.69 / 19.51** (1-min/5-min ≥20)、再直測 14:14 **26.68 / 25.34 / 20.36**
  (全 window ≥20) — 直測 2 回とも不成立かつ上昇局面のため「全 window <20」不成立 →
  test/parity/fuzz/bench/falsify 新規実測 **not-run** (保守側 skip)。
- terminal 空出力 fault (既知) のため python driver + redirect→read_file 迂回実測
  (evidence/_f504_probe.py → /tmp/tori_f504, evidence/_f504_probe2.py → /tmp/tori_f504b)。
- **HEAD 移行を直接実測: 694e2ac7** (frame 503 commit, 366321a から移行; git log 実測:
  694e2ac frame 503 genuine / dc55cf3 frame 502 skip / 366321a frame 471+472)。
  `git diff 366321a..HEAD --stat -- src/ script/ deps.edn` = **0 行** → コード不変。
- working tree untracked = evidence 記録 + 診断スクリプトのみ (src/ 変更なし)。
- **OPEN 赤 8 site 引用行を 694e2ac で直接行読み再検収 — 全引用現存有効**:
  api.cljk:202 :deposit integer?/pos? のみ / api.cljk:68 :order qty integer?/pos? のみ /
  clearing.cljk:711 :deficit (fnil + 0) / clearing.cljk:726 :collateral (fnil + 0) /
  funding.cljk:138 :funding-residue (fnil + 0) / bench.cljk:112 (bk/cancel! b oid) 2 引数 /
  commit.cljk:113 internal-node reduce + 無検査 / commit.cljk:115 reserves。
- 測定 copy /tmp/tori-f495 現存確認済 (frame-495 recipe 再構成品, kotoba/ 同梱)。
- 正本引用 **frame-503 (694e2ac) 実測 base** 維持: suite **149** (f503: 357/915 両 runtime,
  same-count 53 度目) / fuzz digest **45 度目** byte-identical (2715B diff 空) /
  parity 49 (frame-499) / bench f451 copy 3-run green cancelled=153,767 (frame-477)。
- **ledger gap 発見 (記録系, 1 件)**: status/maturity.md の REMEASURE LOG は frame-495 で終わり、
  frame 496–503 の枠記録が LOG に未登録 (score 行の引用は frame-503 実測に更新済み、
  evidence/2026-09-19-frame496..503 記録ファイルは実在)。次 genuine 枠で retro 登録を推奨。
- 発覚 1 件 (記録系), 新規 hypothesis 0 件, NEXT 未実測 0 件変化なし
  (3-part bench fix 着地はインタラクティブ枠, cron 禁止)。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- 残作業不変: ① validate-i53-halt fix パッケージ (累算 sum gate 7 site + fx/mul-rate 事前界限 +
  REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) ② 3-part bench fix 着地 →
  HEAD 3× n=1M → 再現性 3 ③ fuzz 常設化 ④ nbb bootstrap 2 段恒久化 ⑤ JVM .cljk runner
  ⑥ parity 呼び出し規約恒久化 ⑦ maturity.md REMEASURE LOG frame 496–503 retro 登録。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **150** /
  parity **50** / fuzz **46** / bench **38 実行目**)。次枠番号 = **505**。
