# frame 492 remeasure (2026-09-18 13:0x JST, cron, no code changes)

- Load gate: 13:04–13:06 direct 10.07/10.56/11.17 — all windows <20, gate passed.
- HEAD **366321a** direct (same as frames 478–491). `git diff HEAD --stat -- src/ script/ bench/ deps.edn test/` = **0 行** → コード不変。working tree = maturity.md (M) + evidence のみ。
- repo bench/torihiki/bench.cljk:112 再読: still 2-arg `(bk/cancel! b oid)` — 3-part bench fix (f15+f16+f17) **unlanded**。
- /tmp/tori-f451 measurement copy intact (bench/torihiki/bench.cljc owner-wired + .f15orig/.f16orig, evidence/ layout correct); /tmp/f491_runner.sh staged and recipe self-contained.
- **New measurement legs not-run: run-time budget exhausted this frame** (same as frames 490/491). No JVM_EXIT/NBB_EXIT/fuzz/bench numbers claimed.
- 正本引用 (latest genuine): **frame 489 (2026-09-18 0915)** — suite **148** same-count 51st (357/915, 0F/0E, JVM+NBB both PASS, JVM_EXIT=0 / NBB_EXIT=0) / fuzz digest **43rd** byte-identical (diff 空) / parity 47 (frame 484)。fuzz re-occurrence streak 継続中。
- スコア 7 軸変更なし: **3/3/3/3/2/1/1** (spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1)。反証実施 **16 件**, f8–f17 OPEN。
- NEXT 未実測変化 0 (fix 未着手 — cron code-change 禁止)。

Next runner (frame 493): execute /tmp/f491_runner.sh as-is under load gate, then standing NEXT: interactive slot lands 3-part bench fix at HEAD → 3× n=1M → 再現性 3; validate-i53-halt fix package remains highest leverage.
