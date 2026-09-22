# frame 524 (2026-09-21 11:14, cron) — load gate skip (no code changes)

## 負荷ゲート (2026-09-21 11:14 直測, sysctl vm.loadavg + uptime)

- 1-min **33.17** / 5-min **32.97** / 15-min **31.74** — 全 window ≥20 で「全 window <20」不成立 → test/parity/fuzz/bench/falsify 新規実測 **not-run** (skip-check-0921-1114.txt)。
- pre-run (11:09, cron script 収集 block) 1-min 19.57 / 5-min 21.97 / 15-min 28.46 — 1-min は <20 だが 5/15-min ≥20。本枠 11:14 直測は 1-min が 19.57 → 33.17 に上昇しており、pre-run の 1-min <20 は一過性 (frame-397/401 前例の genuine-lite 解釈の適用条件である「明確な低下局面」を満たさない)。2 測定で 5/15-min は継続 ≥20。
- torihiki_state.sh は stdout 空の chronic fault (本枠 11:0x 実行 exit 0 stdout 0 bytes; frame 496/499/505/514/517/521/523 と同型) のため、本枠は python driver + redirect→read_file 迂回で直接実測。

## HEAD / working tree (11:14 直接実測)

- HEAD **e819d69** (frame 523 と同一 — 09:05 直測から 11:14 まで不変)。
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** (直接実測) → コード不変。
- `git status --porcelain` = 2 行: `M status/maturity.md` + `?? evidence/2026-09-21-0905-frame523-gate-passed-budget-exhausted.md` (frame-523 evidence 未 commit) のみ — src/ 変更なし。

## OPEN 赤引用行再検収 (e819d69 で直接行読み)

全 8 site + bench 引用現存**有効**:
- src/torihiki/api.cljk:68 = `(not (and (integer? qty) (pos? qty))) :bad-quantity` (i53/notional cap なし)
- src/torihiki/api.cljk:202 = `(not (and (integer? (:amount t)) (pos? (:amount t)))) :bad-amount` (i53/balance-domain gate なし)
- src/torihiki/clearing.cljk:711 = `(update-in [:accounts acct :deficit] (fnil + 0) (fx/check :deficit (- c))))` (sum 無検査)
- src/torihiki/clearing.cljk:726 = collateral `(fnil + 0)` 累算 (sum 無検査)
- src/torihiki/funding.cljk:138 = `(update :funding-residue (fnil + 0) p))))))` (sum 無検査)
- src/torihiki/liquidation.cljk:195 = `(update :insurance-fund (fnil + 0) fee))` (sum 無検査)
- src/torihiki/commit.cljk:113 = `(- (reduce + 0 (map #(:sum % 0) leaves)) (long attested))))` (internal-node 合算) / :115 = `(defn reserves` (merkle aggregate, falsify-14)
- bench/torihiki/bench.cljk:112 = `(let [q (bk/cancel! b oid)]` — **2 引数のまま** (3-part fix f15+f16+f17 未着地再確認, bench known-red)

## 正本引用 (不変)

frame-513 (2026-09-20 09:0x) 実測 base 維持 (frame 523 同一引用): suite **155** 両 runtime PASS (same-count 59 度目, 357/915 0F/0E) / parity **52** (FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3…, PROOF a 10 true) / fuzz digest **52** byte-identical / bench known-red (bench.cljk:112 2 引数, 3-part fix 未着地)。

## 結果

- 発覚 0 件, 新規 hypothesis 0 件, スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 18 件, f8–f18 OPEN)。
- NEXT 未実測不変: validate-i53-halt fix パッケージ (全てコード変更系, cron 禁止 — インタラクティブ枠で実施) → 3-part bench fix 着地 → HEAD 3× n=1M → 再現性 3 → fuzz 常設化。
- 次枠番号 = **525** (新規実測時 suite 156 / parity 53 / fuzz 53 / bench 40 実行目)。
