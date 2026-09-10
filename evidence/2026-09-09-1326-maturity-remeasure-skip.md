# 2026-09-09 1326 maturity remeasure — skip (load gate, frame 400)

- Runner: cron (torihiki-falsify), no code changes.
- 負荷 13:26 直測 uptime: 1-min **30.14** / 5-min **31.87** / 15-min **29.96** — 全 window ≥20 で「全 <20」不成立 → test / parity / bench / fuzz / falsify 新規実測は not-run (高負荷)。
- terminal backend 空出力 fault (既知, frame 387–389/398/399 と同型) — 出力は redirect + read_file 迂回で取得 (/tmp/tori_frame400.txt)。
- HEAD **915f832** (frame 398 commit, 12:50) 直接実測 — frame 399 と同一, 並行重複なし。
- git diff HEAD -- src/ script/ deps.edn: 0 bytes (status --porcelain に src/ script/ deps.edn なし, 13:26 実測) — コード不変。
- untracked は maturity.md (M) + ルート診断スクリプト + evidence 作業ファイルのみ。
- 正本引用: frame-394/395/397 実測 base 維持 (suite **133** 両 runtime 同一カウント 36 度目 / fuzz digest 31 度目 byte-identical / bench 赤累計 29 実行目 bench-tape-cancel-arity 既知赤)。
- NEXT 未実測 0 件 (fix 未着手) — 発覚 0 件, 新規 hypothesis なし (skip 枠のため仮説実測なし)。
- スコア 7 軸変更なし: **3/3/3/3/2/1/1** (反証実施 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ (api i53 検査 + notional 上限 + balance-domain gate + 累算 sum gate 6 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **134** / parity 39 / bench **94**)。次枠番号 = **401**。
