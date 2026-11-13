# frame 565 (2026-09-30 21:1x, cron) — gate ambiguous/declining but budget exhausted → 1ceb357 re-anchor NOT-RUN

## 判定
**NOT-RUN (budget)** — 負荷ゲートは低下局面 (ambiguous→pass 傾向) だが、実行予算が
2-runtime suite 実測に着手できない段階で尽きた。本 frame は new measurement を生成していない
(既存 baseline frame-548 (92b9fc9) 引用のまま, fabricate なし)。

## 負荷ゲート (実測)
- 21:04 (pre-run Script Output block): **21.53 / 21.37 / 22.04** — 全 window ≥20
- 直測 21:10 (uptime, redirect→read_file): **22.61 / 22.71 / 22.35** — 全 ≥20, 微上昇
- 直測 21:12: **12.58 / 18.71 / 20.80** — 1-min/5-min <20 かつ明確な低下局面
  (15-min のみ 20.80 微超; frame-397/401 genuine-lite 前例型)
- 判定: 2 分で 22.7 → 12.6 の急落 = 低下局面として genuine 解釈可だが、
  **budget exhaustion のため実測不可** → NOT-RUN (判定を budget 側に確定)

## コード状態 (直接実測)
- HEAD **1ceb357** (detached; kotoba-migrate-all-20260925 merge) — frame-561/564 と不変
- `git diff HEAD --stat -- src/ script/ deps.edn`: **IN-FLIGHT 現存** `src/torihiki/fixed.cljk +36`
  / `src/torihiki/slab.cljk +1` (M, 未commit; frame-561 由来の常駐, landing/revert 未確定)
- working tree: maturity.md (M) + evidence untracked 22 件のみ
- torihiki_state.sh stdout 空 (既知 chronic fault; uptime/git 直叩き + redirect→read_file 迂回)

## 引用 (正本, 変更なし)
- frame-548 (2026-09-23 21:2x, 92b9fc9): suite 162 (same-count 66 度目, 357/915 両 runtime) /
  parity 59 / fuzz digest 59 / bench 46 実行目 known-red — **1ceb357 に対しては引用不能** (src 変更含む)
- frame-564 (2026-09-30 08:2x): gate passed / budget exhausted — copy 再構築着手のみで suite 未実行

## 次枠指示 (566)
- IN-FLIGHT fixed.cljk +36 / slab.cljk +1 の landing/revert を先に確認
- 負荷 <20 枠で copy 作り直し (frame-506 python copytree レシピ; 単一宛先 mv 禁止) 後、
  **1ceb357 上で suite + parity + fuzz 全量新規実測で正本引用 base を re-anchor**
  (kotoba.test 移行 d3ebe68 で test 数変動の可能性あり; bench 47 実行目は known-red 継続見込み)

## 次枠番号
**566** (新規実測時: suite 163 / parity 60 / fuzz 60 / bench 47 実行目 — 1ceb357 re-anchor 枠)
