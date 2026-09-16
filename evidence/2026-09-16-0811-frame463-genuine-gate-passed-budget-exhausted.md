# frame 463 genuine-gate-passed budget-exhausted frame (2026-09-16 08:11 JST, cron, no code changes)

- 負荷 08:11 uptime 直測 1-min **15.35** / 5-min **12.11** / 15-min **13.37** 全 window <20 → gate 成立。
- HEAD **0a99c9c2a1275d32ba2e99247f43978e62bf1645** (git rev-parse 直接実測, frame-442〜461 と同一), git diff HEAD -- src/ script/ deps.edn = **0 bytes**, untracked は maturity.md (M) + evidence のみで src/ 変更なし。
- /tmp/tori-f451 測定 copy 現存確認済 (deps.edn / bench / src / test / evidence-fuzz-seeded.cljc)。
- 実測 budget 枯渇 (runtime budget exhausted mid-frame) のため test/parity/fuzz/bench/falsify 新規実測は **not-run** — 06:15 skip 枠 (frame-462) に続き 2 枠連続 not-run。
- 正本引用 **frame-461 (2026-09-16 05:1x) 実測 base** 維持: suite **143** (JVM+nbb 357/915 0F/0E same-count 49 度目) / parity **44** / fuzz digest **37** / bench f451 copy cancelled=153,767 3x stable。
- NEXT 未実測 0 件変化なし (fix 未着手 — cron code-change 禁止), 発覚 0 件, 新規 hypothesis なし。
- スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- 残作業不変: ① validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 7 site [commit.cljk:115/:113 含む] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) ② 3-part bench fix 着地 (f15 owner + f16 arg-order + f17 ring-owner) → repo copy n≥1M 3x 安定 → 再現性 3 ③ fuzz 常設化 ④ nbb bootstrap 2 段恒久化 ⑤ JVM .cljk runner ⑥ parity 呼び出し規約恒久化 (AMU_HOME + script/kotoba-parity.cljk)。
- 次枠番号 = **464** (新規実測時 suite **144** / parity **45** / fuzz **38** / bench **37 実行目**)。
