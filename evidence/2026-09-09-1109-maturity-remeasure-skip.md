# 2026-09-09 1109 枠 — frame 390 load gate skip (no code changes, cron)

## 負荷判定 (実測)
- pre-run script 直測 (11:09 uptime): 1-min **36.55** / 5-min **49.70** / 15-min **65.96**
- 全 window ≥20 で「全 <20」不成立 → 高負荷続行 → test / parity / bench / falsify 新規実測は **not-run**。

## 既知 fault
- terminal backend 空出力 (uptime / git 直接実行 3 回とも exit 0 で stdout 空) — frame 387/388/389 と同型の高負荷時 fault。torihiki_state.sh も stdout 空。
- このため HEAD / src diff の直接検収は not-run。コード不変は前枠 frame-389 (11:00 skip) の記録契約 (HEAD 33602ff, src/script/deps.edn diff 0 bytes, IN-FLIGHT は maturity.md + untracked 診断スクリプトのみ) を引用して踏襲。pre-run IN-FLIGHT 一覧も src/ script/ への変更を含まないことを確認 (_diag*.py / _ins*.py / _mat_279.sh のみ)。

## 正本引用 (変更なし)
- 引用正本: **frame-286 (commit e81a243, 09-08 12:06) suite 130** — test-286-{jvm,nbb}.log (357/915 全緑), parity-286.log (0 drift), bench-286.log (bench.clj:112 cancel! 2-arg 既知赤)。
- OPEN 赤 6 site 引用行 (api.cljc:68 / api.cljc:202 / clearing.cljc:711 / clearing.cljc:726 / funding.cljc:138 / bench/torihiki/bench.clj:112) は frame 384/386 実読検収のまま有効。

## 本枠の反証
- NEXT 未実測リスト 0 件 (fix 未着手) のため hypothesis なし, 発覚 0 件, 新規実測なし。反証実施 36 件 / 発覚 30 件のまま。

## スコア
- 7 軸変更なし: **3/3/3/3/2/1/1** (spec/実装/テスト/反証/再現性/governor/運用)。

## 残作業 (不変)
- validate-i53-halt fix パッケージ (i53/notional/balance-domain gates + 累算 sum gates :deficit/:funding-residue/:fees-collected/collateral + fx/mul-rate pre-check + REDUCING 積 pre-limit) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化。

## 次ランナー
- 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **131** / parity **39** / bench **91**; 引用正本 frame-286 / suite-130 / e81a243)。次枠番号 = **391**。
