# frame 444 (2026-09-14 17:12–17:2x, cron, genuine gate): falsify-16 hypothesis registered, measurement deferred to next genuine frame

- load gate PASSED: 17:12 uptime direct 1-min 11.62 / 5-min 13.23 / 15-min 16.91, all <20 — genuine frame.
- HEAD 0a99c9c2 (frame 442 citation-refresh commit; frames 443/444 same), src/ script/ deps.edn unchanged.
- falsify-14 (multi-account aggregate, frame 437) is CONFIRMED and verdict'd — maturity.md line 12 "未実測 (NEXT 残)" citation is STALE vs evidence/2026-09-14-0222-falsify-14-multi-account-aggregate-overflow.md (measured via assoc-in seeding). NEXT unmeasured follow-through re-registered as **falsify-16**: production-path reachability — real `:deposit` txs via `st/apply-block` walk 2/3 accounts to individually-reversible 2^53+2, aggregate 3×(2^53+2)=27021597764222982 (≡2 mod 4, irreversible) diverges roots with zero throws; 2×(2^53+2)=18014398509481988 (≡0 mod 4) is the reversible control. hypothesis: evidence/2026-09-14-1712-falsify16-hypothesis.md. Run out of run-time budget before harness/driver measurement — defer to next genuine frame (falsify-16 first).
- Scores unchanged 3/3/3/3/2/1/1; falsifications 15, discoveries 9; f8–f15 OPEN.
- Next frame = 445 (suite 137 / parity 40 / bench 96 on next full re-measure; falsify-16 priority).
