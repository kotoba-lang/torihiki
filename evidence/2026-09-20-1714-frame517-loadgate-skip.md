# frame 517 (2026-09-20 17:14 JST, cron) — load gate skip (no code changes)

## Load gate (FAIL — all windows >=20)
- pre-run torihiki_state.sh block (17:04): 1-min 19.84 / 5-min 40.49 / 15-min 57.74
- direct 17:10: 1-min **28.95** / 5-min **26.30** / 15-min **43.84**
- Both measurements: all windows >=20 → "all windows <20" not satisfied →
  test/parity/fuzz/bench/falsify new measurements **not-run**.
- terminal backend empty-output fault (known high-load type); outputs via redirect + read_file.
- skip-check: evidence/skip-check-0920-1714.txt

## HEAD / code invariance
- HEAD **6552005c7cc96b02b62c5613974b3ccf0de5e5d6** direct (frame-512 ledger commit;
  same as frame 513 canonical base).
- `git diff 694e2ac7..HEAD -- src/ script/ deps.edn` = 0 bytes → landing ledger/evidence only,
  code unchanged → frame-513 (6552005c) measured citations remain valid.
- working tree: 256 porcelain rows, all untracked evidence only (frames 467-516 records
  uncommitted); status/maturity.md (M) — no src/ script/ changes.
- repo bench/torihiki/bench.cljk:112 re-read at 6552005c: still 2-arg
  `(bk/cancel! b oid)` (lines 112: `(let [q (bk/cancel! b oid)]`) — 3-part fix (f15+f16+f17)
  unlanded. Reproducibility stays 2.

## Canonical ref maintained (frame 513, 6552005c)
- suite **155** both runtimes PASS (same-count 59th, 357/915 0F/0E, evidence/f513-test-*)
- parity **52** both runtimes PASS (FLAT ROOT b4322bed… / STATE ROOT d1ebb9d3…, PROOF a 10 true,
  evidence/f513-parity-*)
- fuzz digest **52** byte-identical (core 2715, raw diff = JVM echo + FJ_EXIT vs FN_EXIT port
  lines only, evidence/f513-fuzz-* + f513-fuzzdiff.txt)
- bench known-red (copy bench.cljc:112 2-arg ArityException, BENCH_EXIT=1, evidence/f513-bench-*)

## falsify-18 closure note (measured at frame 513, carried as canonical)
- **falsify-18 cancel-miss decomposition CONFIRMED** (2026-09-20 08:2x, frame 513):
  on 3-part-fixed instrumented copy /tmp/tori-f513 (BENCH_EXIT=0, evidence/f513-bench.out),
  cancel-attempts 429,561 = cancelled 153,767 + miss-empty-slot 244,946 (57.0%) +
  miss-stale-oid 30,848 (7.2%), **owner-mismatch = 0 exactly** (frame-449 ~1/1024 owner-agreement
  model fully refuted as the residual cause; ring-owner wiring → engine auth never rejects a
  tape cancel). placed 450,908 / resting 223,196 identical to the deterministic tape of
  frames 451/453/455/456/464/477 (cancelled=153,767) → instrumentation did not perturb the
  measurement. **The falsify-16/17/18 chain is complete: engine cancel path sound on the
  measured tape; the sole remaining gap to reproducibility-3 is the 3-part fix landing in the
  repo copy.** No further harness defect is required to explain cancelled values.
- Verdict: evidence/2026-09-20-0825-frame513-falsify18-verdict-cancel-miss-decomposition.md;
  hypothesis: evidence/2026-09-20-0812-frame513-falsify18-hypothesis-cancel-miss-decomposition.md.

## Result
- Discoveries 0 (falsify-18 was frame 513), new hypotheses 0.
- Scores 7 axes unchanged (**3/3/3/3/2/1/1**; falsifications 18, f8-f18 OPEN; falsify-14
  multi-account deficit aggregation unmeasured carry).
- NEXT unmeasured 0 (fix unlanded, cron code-change prohibited): validate-i53-halt fix package
  (api i53/notional/balance-domain gates + accum sum gate 7 site + fx/mul-rate pre-limit +
  REDUCING product pre-limit + rate cap + settle-deficit delta clamp + commit aggregate site)
  → 3-part bench fix landing (f15+f16+f17, engine sound per falsify-18) → HEAD 3× n=1M
  cancelled>0 → reproducibility 3 → fuzz suite permanent → test/falsify 4. All code-change
  class — interactive frame.
- Next frame = **518** (new-measurement-time suite 156 / parity 53 / fuzz 53 / bench 40 実行目).
