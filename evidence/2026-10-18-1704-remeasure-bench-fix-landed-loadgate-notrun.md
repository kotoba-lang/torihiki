# torihiki maturity rank iteration 2026-10-18 17:04 (no code changes, cron)

## Load gate
17:04 直測 1-min 61.84 / 5-min 56.82 / 15-min 43.60 — 全 window ≥20 → suite/parity/fuzz/bench 新規実測 not-run (host-load-notrun 規約)。

## State
- HEAD `1ceb357` (frame-548 base 92b9fc9 から kotoba-migrate-all コードモッド経由で移動)。`git diff HEAD --stat -- src/ script/ bench/ deps.edn` = 0 bytes。
- 正本引用 frame-548 (2026-09-23 21:2x) base 維持: suite 162 両 runtime 357/915 (same-count 66) / parity 59 / fuzz 59 byte-identical / bench 46th known-red。

## Re-measurement findings (2 点, maturity.md に反映)
1. **bench 3-part fix (f15+f16+f17) は HEAD 着地済み** — commit 66c206f「bench: measure against Hyperliquid on its own axis; the book bench runs again」。bench/torihiki/bench.cljk:112 直接行読み: `(let [owner (bit-and i 1023) oid (bk/place! b … 0 owner)]` + ring-owner ring + `(bk/cancel! b oid (aget ring-owner slot))` — owner 接続 (f15) + 引数順 (f16) + ring-owner (f17) 全て揃う。book.cljk:542 `cancel!` は owner 付き署名。**2026-10-13-1110-bench-line112-check.md の「fix 未着地」結論は誤判定** (let binding 数を呼出 arity と誤読)。→ 再現性 3 の残条件は「HEAD で n≥1M 低負荷 3 回安定 (cancelled>0)」の 1 つのみ。
2. **反証件数表記を 15→19 件に更新** (frame-523 citation-hygiene note 準拠; falsify-14 merkle aggregate frame 452 / f16 frame 449 / f17 frame 451 / f18 cancel-miss closure frame 513; ledger 19 件, f8–f18 OPEN, f14b CLOSED)。テスト行に f1014 (2026-10-12) JVM HEAD 1ceb357 単独緑 357/915 を追記 (nbb HEAD 未実測)。

## Scores
7 軸 **3/3/3/3/2/1/1 変更なし** (spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1)。再現性は bench fix 着地により 3 への条件が 1 測定に縮小。

## NEXT (highest leverage)
1. 低負荷窓 (全 window <20) 突入後最初の枠で **HEAD 1ceb357 bench n=1M 3 回安定実測 (cancelled>0)** → 再現性 2→3 (コード変更不要、1 測定で軸が動く — 本枠の最高 leverage)。
2. 次点: validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 6 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) — 全てコード変更系, インタラクティブ枠 (cron 禁止)。
3. fuzz suite 常設化 → テスト/反証 4。

発覚 0 件, 新規 hypothesis 0 件。次枠番号: 直近 ledger 枠は frame 566 系 (2026-10-13 0121) — 新規実測時 suite 163 / parity 60 / fuzz 60 / bench 47 実行目。