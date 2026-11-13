# frame 549 (2026-09-25 08:58, cron) — load gate skip (no code changes)

負荷 直測: 1-min **362.32** / 5-min **439.97** / 15-min **438.56** — 全 window ≥20 で「全 window <20」不成立のため test/parity/bench/falsify 新規実測 not-run。

torihiki_state.sh stdout 空 (既知慢性 fault) のため uptime 直叩きで迂回実測。

HEAD **92b9fc9** (frame 541 以来不変), `git diff HEAD --stat -- src/ script/ bench/ deps.edn` = **0 bytes** → コード不変。

正本引用 **frame-548 (2026-09-23 21:2x) 実測 base** 維持:
- suite **162** 両 runtime PASS (same-count 66 度目, 357/915 0F/0E)
- parity **59** 度目 (JVM FLAT b4322bed… / STATE d1ebb9d3… / PROOF a 10 true; nbb fixed 38 / fixed-result 20 cases 0 drift)
- fuzz digest **59** 度目 byte-identical (JVM 2733 / nbb 2715, raw diff = JVM echo 行のみ)
- bench **46** 実行目 known-red (bench.cljc:112 ArityException 2 引数, 3-part fix f15+f16+f17 未着地)

NEXT 未実測不変 (全てコード変更系, cron 禁止 — インタラクティブ枠):
1. validate-i53-halt fix パッケージ (api/validate :bad-amount i53 検査 + notional 上限 level×qty ≤ i53-max [falsify-7 必須] + balance-domain gate `collateral + amount > i53-max` 拒否 [falsify-8 実測で確定] + 累算 sum gate 6 site [:deficit/:funding-residue/:fees-collected/:insurance-fund + collateral 累算各 (fnil +/− 0)] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp)
2. 3-part bench fix 着地 (bench.clj:112 owner 付き 3 引数 + probe.clj:31 + curve.clj:21)
3. HEAD 3× n=1M 低負荷 3 回安定実測 (cancelled>0) で 再現性 3
4. fuzz suite 常設化で テスト/反証 4
5. nbb-classpath bootstrap fix (2 段構成恒久化)

発覚 0 件, 新規 hypothesis 0 件。
スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 19 件, f14b CLOSED, f8–f18 OPEN)。

次枠番号 = **550** (新規実測時 suite 163 / parity 60 / fuzz 60 / bench 47 実行目)。
