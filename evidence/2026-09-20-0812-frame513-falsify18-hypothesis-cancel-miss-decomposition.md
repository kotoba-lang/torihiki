# falsify-18 hypothesis: cancel-miss decomposition on 3-part-fixed bench tape (2026-09-20 08:09 JST, frame 513)

## Hypothesis
On the measurement copy with the 3-part bench fix applied (f15 owner wiring +
f16 arg-order + f17 ring-owner, per falsify-15/16/17), the cancel-miss residual
(cancelled 153,767 of ~430k cancel attempts; frame-449 "remaining candidates:
ring slot selection window + stale gen oids") decomposes into exactly three
classes measurable from the bench layer:

1. **empty-slot miss** — ring slot reads −1 (never filled yet, or cleared by an
   earlier successful cancel);
2. **stale-oid miss** — `pos?` oid but `cancel!` returns 0 (order consumed by an
   aggressive fill → gen mismatch; owner is stored-correct so mismatch ≈ 0);
3. **owner-mismatch** — `cancel!` returns 0 because the presented owner does not
   match the placer (predicted ≈ 0 with ring-owner wiring; ~1/1024 per the
   frame-449 owner-agreement model was for the f16-wrong-order arrangement).

Prediction: attempts = cancelled + miss-empty + miss-stale (exact sum),
owner-mismatch = 0, and the dominant miss class is the empty-slot window
(ring index −1 covers both never-written and cleared slots because `w` advances
only on successful placement while `i` advances every op).

## Method (no repo changes; measurement copy only)
- Base copy: `/tmp/tori-f506` (frame 507–512 measurement copy, HEAD 6552005c
  code-identical to 694e2ac7 — `git diff 694e2ac..6552005` = evidence/maturity
  only, 20 files, 0 in src/ script/ deps.edn; repo `bench/torihiki/bench.cljk:112`
  re-read at 6552005c: still 2-arg `(bk/cancel! b oid)`, 3-part fix unlanded).
- Instrumented copy: `/tmp/tori-f513` (copytree of f506). bench.cljc changes,
  measurement copy only (backup bench.cljc.f513orig):
  - `ring-owner` long-array storing `(bit-and i 1023)` of the placing op (f17);
  - cancel branch → `(bk/cancel! b oid (aget ring-owner slot))` (f15+f16);
  - reason counters `rc` [attempts, miss-empty, miss-stale, miss-owner];
  - printed as `cancel-attempts / miss-empty-slot / miss-stale-oid / owner-mismatch`.
- Engine code untouched (`src/` diff 0 bytes); book `cancel!` signature
  `[b oid owner]` re-read at src/torihiki/book.cljc:542.
- Run: `clojure -M:bench 1000000` on the copy, JVM, BENCH_EXIT=0
  (evidence/f513-bench.{out,err,exit}). Two build iterations (first run EXIT=1,
  EOF-while-reading at :159 — instrument paren imbalance, fixed +1 paren,
  balance re-verified 0).
