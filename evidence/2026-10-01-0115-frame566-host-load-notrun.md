# frame 566 (2026-10-01 01:1x, cron) — loadgate NOT-RUN

## 判定
**NOT-RUN (loadgate)** — 直測の 3 window すべて負荷ゲート (>20) を大幅に超え、
本 frame は suite/parity/fuzz/bench すべて実行していない (fabricate なし)。

## 負荷ゲート (実測)
- 01:01 pre-run (Script Output block): **31.63 / 34.13 / 39.51** — 全 window ≥20
- 01:01 直測 (uptime, redirect→read_file): **48.28 / 51.53 / 45.55** — 全 window ≥20
- 01:15 直測 (uptime, redirect→read_file): **45.41 / 47.64 / 47.53** — 全 window ≥20, 微低下
  (15-min 45.55 → 47.53 / 5-min 51.53 → 47.64 低下傾向; 1-min 48.28 → 45.41 低下)
- 判定: 3 window すべて >20 が 2 回連続で確認 → gate 未達成 → **suite NOT RUN**。
  低下局面ではあるが (5/15-min が 50 → 47.6 / 45.5 へ下落) 再測は次枠へ。
- skip 継続: 549–565 (23:13 以来 15h+)

## コード状態 (直接実測)
- HEAD **1ceb357** (1ceb357233c528c0d40409cb6d4a6f69ed1864ea, detached) — frame-564/565 と不変
- `git status --short -- src/torihiki/fixed.cljk src/torihiki/slab.cljk`:
  `M src/torihiki/fixed.cljk` + `M src/torihiki/slab.cljk` — IN-FLIGHT 現存 (untracked 2 件,
  landing/revert 未確定; frame-561 由来の常駐)
- torihiki_state.sh stdout 空 (既知 chronic fault; uptime/git 直叩き + redirect→read_file 迂回)

## 引用 (正本, 変更なし)
- frame-548 (2026-09-23 21:2x, 92b9fc9): suite 162 (same-count 66 度目, 357/915 両 runtime) /
  parity 59 / fuzz digest 59 / bench 46 実行目 known-red — **1ceb357 に対しては引用不能** (src 変更を含む)
- frame-565 (2026-09-30 21:1x): gate ambiguous/declining but budget exhausted — 1ceb357 re-anchor NOT-RUN

## 次枠指示 (567)
- IN-FLIGHT fixed.cljk +36 / slab.cljk +1 の landing/revert を先に確認
- 負荷 <20 枠で copy 作り直し (frame-506 python copytree レシピ; 単一宛先 mv 禁止) 後、
  **1ceb357 上で suite + parity + fuzz 全量新規実測で正本引用 base を re-anchor**
  (kotoba.test 移行 d3ebe68 で test 数変動の可能性あり; bench 47 実行目は known-red 継続見込み)

## 次枠番号
**567** (新規実測時: suite 163 / parity 60 / fuzz 60 / bench 47 実行目 — 1ceb357 re-anchor 枠)
