# frame 496 load gate skip (2026-09-19 ~01:04–01:08 JST, cron, no code changes, no measurement)

- Load gate FAILED: torihiki_state.sh pre-run 22.04/21.46/21.98 (1-min ≥20); re-check at ~01:08 after 90s wait **27.77/23.86/22.83** — all windows ≥20 and rising. No 低下局面 within budget (cf. frame-495 23:12 6.45/11.92/12.94 で gate 成立).
- HEAD **366321a** (git rev-parse direct, same as frames 478–495). Working tree = status/maturity.md (M, frames 476/477 反映) + evidence のみ。repo bench/torihiki/bench.cljk:112 3-part fix 未着地のまま (frame-495 23:12 再検収済み、本枠は未再読 — load skip)。
- terminal stdout capture fault persists (all commands return empty output, exit 0) → all reads via redirect + read_file (frames 489/495 同一 fault)。
- Measurement: not-run (gate skip, established convention 1-min <20 all windows 必須)。

## スコア

7 軸変更なし: **3/3/3/3/2/1/1**。正本引用は最新 genuine 実測で維持:
- suite 148 両 runtime PASS / fuzz digest 43 度目 byte-identical: frame 489 (evidence/2026-09-18-0915-frame489-genuine-suite148-fuzz43.md, f489-* files)
- parity 48 度目両 runtime PASS (PJ/PN_EXIT=0, baseline root 一致): frame 495 (evidence/2026-09-18-2312-frame495-genuine-parity48-both-runtimes.md)
- bench 3-run green series (copy /tmp/tori-f451, cancelled=153,767 × 3): frame 477 (evidence/2026-09-17-0510-frame477-bench37-first-3run-green-series.md) — repo HEAD 未実測のまま再現性 2 維持
- 新規実測 0 件, 新規 hypothesis 0 件, 発見 0 件。

## NEXT (変化なし, 最高レバレッジ順)

1. **validate-i53-halt fix パッケージ** (interactive 枠, cron 禁止): api/validate :bad-amount i53 検査 + notional 上限 (level×qty ≤ i53-max, f7) + balance-domain gate (collateral+amount > i53-max 拒否, f8) + 累算 sum gate 7 site (:deficit/:funding-residue/:fees-collected/:insurance-fund, f9–f13) + settle-deficit delta clamp (f12 副次) + fx/mul-rate / REDUCING 積 事前界限 (f9/f11 副次) + rate 上限。OPEN 赤 2 件 (halt 型 + 分岐型) を同時に閉じる。
2. **3-part bench fix 着地** (bench.cljk:112 owner 付き 3 引数, f15+f16+f17) → HEAD 3× n=1M 低負荷 → 再現性 3。f451 copy は揮発済みのため frame-495 再構築手順 (copytree + .cljk→.cljc + kotoba symlinks=True) で copy を作り直すこと。
3. その後 fuzz suite 常設化 → テスト/反証 4。
