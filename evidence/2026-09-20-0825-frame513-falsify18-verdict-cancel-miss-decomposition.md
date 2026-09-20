# falsify-18 verdict: cancel-miss decomposition — CONFIRMED, residual fully explained (2026-09-20 08:2x, frame 513)

## Result (JVM, n=1,000,000, BENCH_EXIT=0, evidence/f513-bench.out)

| counter | value | share of attempts |
|---|---|---|
| cancel-attempts | 429,561 | 100.0% (43.0% of n — matches gen-tape 43% cancel mix) |
| cancelled | 153,767 | 35.8% |
| miss-empty-slot | 244,946 | 57.0% |
| miss-stale-oid | 30,848 | 7.2% |
| owner-mismatch | 0 | 0.0% |

**Exact closure: 153,767 + 244,946 + 30,848 = 429,561 = cancel-attempts.**
placed 450,908 / resting 223,196 — identical to the deterministic tape of
frames 451/453/455/456/464/477 (cancelled=153,767), so instrumentation did not
perturb the measurement.

## Verdict: CONFIRMED
1. **Decomposition closes exactly** — the three classes are exhaustive at bench
   layer; no unexplained residual remains between cancelled=153,767 and 429,561
   attempts.
2. **Owner-mismatch = 0 exactly** — the frame-449 owner-agreement model
   (~1/1024 → predicted ~2,200) is fully refuted as the residual's cause; with
   ring-owner wiring the engine auth check never rejects a tape cancel. The
   `cancel!` owner gate itself is sound (0 false rejections over 429,561
   attempts with correct owners).
3. **Dominant miss class is the empty-slot window (244,946, 57.0%)** — the ring
   slot expression `(bit-and (- w 1 (i mod 524287)) ring-mask)` frequently
   lands on −1: `w` advances only on successful placement while `i` advances
   every op, and successful cancels clear their slot to −1. This is harness
   geometry, not an engine defect.
4. **Stale-oid (30,848, 7.2%)** = oids consumed by aggressive fills, gen
   mismatch → `cancel!` returns 0 — the generation counter working as designed
   (this is the class frame-449 leaned toward; it is real but 8× smaller than
   the empty-slot class).

## Implication for reproducibility-3
The engine cancel path is sound on the measured tape; the remaining gap to
"cancelled > 0 at n ≥ 1M, 3× stable" is purely the 3-part fix landing in the
repo copy (repo bench.cljk:112 still 2-arg at 6552005c — re-read this frame).
No further harness defect is required to explain cancelled values; the
f451-copy 3-run green series (cancelled=153,767 identical 3/3) plus this frame's
decomposition complete the falsify-16/17/18 chain. Score stays 2 (landing is an
interactive-frame action, cron code-change prohibited).

## Evidence files
- evidence/f513-bench.out / f513-bench.err / f513-bench.exit (BENCH_EXIT=0)
- instrumented copy /tmp/tori-f513 (backup bench.cljc.f513orig inside; repo untouched)
