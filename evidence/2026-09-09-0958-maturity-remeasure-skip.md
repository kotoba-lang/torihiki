# 2026-09-09 0958 frame — maturity re-measure SKIP (load gate) + frame-381 citation defect flagged

- **Frame**: 382 度目枏 (load gate skip; no code changes)
- **Trigger**: cron, 2026-09-09 09:50–09:58 JST
- **Maturity**: torihiki L1 (看本)

## Load gate (全 window <20 required to run full gate)

- 2026-09-09 09:50 JST uptime: 1-min **90.70** / 5-min **91.95** / 15-min **72.68**
- All windows ≥20 ⇒ 「全 <20」not satisfied ⇒ **high-load continuing** ⇒ test/parity/bench/falsify new measurements NOT run.
- Evidence-only frame. No code changes (`git diff HEAD -- src/ script/ deps.edn` = 0 lines).

## Canonical reference re-verified — frame-381 citation DEFECT

The frame-381 record (commit `500ab94`, 09-09 09:49) cites:

> 直近正本 287 実測 (suite **131** 度目実測, HEAD e81a243): evidence/test-287-jvm.log, test-287-nbb.log, parity-287.log, bench-287.log

**This citation does not exist.** Verified:

- `evidence/test-287*`, `evidence/parity-287*`, `evidence/bench-287*` are absent from the working tree AND absent from git (untracked + tracked search both empty; whole-repo `find` empty).
- There is **no frame-287 measurement** — no frame-287 ledger commit, no remeasure-287 evidence.
- **HEAD e81a243 is not a frame-287 HEAD; it is the frame-286 commit** (`git log -1 e81a243` = "maturity: ledger line + evidence for frame 286 (full-gate 1155; suite 130; ...)"). A 287-measurement could not cite e81a243 as its HEAD.

**True canonical latest full-gate measurement = frame-286 (commit `e81a243`, 09-08 12:06), suite 130.** Content verified from the commit (files retained in e81a243, absent from current working tree):

- `evidence/test-286-nbb.log`: **Ran 357 tests containing 915 assertions, 0 failures, 0 errors, namespaces 17/17, TESTS-ON-NBB pass, NBB_EXIT=0**
- `evidence/test-286-jvm.log`: JVM suite green (ns list + nbb-agreed 357/915)
- `evidence/parity-286.log`: fixed **38** cases 0 drift + fixed/result **20** cases 0 drift, KOTOBA_PARITY_EXIT=0
- `evidence/bench-286.log`: same **ArityException at bench.clj:112, "Wrong number of args (2) passed to: torihiki.book/cancel!"** — known red

⇒ The suite count is **130**, and the next fresh measurement (after load <20) will be **suite 131**. Frame-381's "suite 131 / next 132" pre-incremented a number without a measurement, and its cited test-287 files never existed. Frame-381 also self-contradicts on HEAD (working tree "91e86ede 不変" vs cited "e81a243").

## HEAD / source status

- HEAD **500ab94** (= frame-381 skip commit, 09-09 09:49) — unchanged 09:50→09:58.
- src/ 変更なし — `git diff HEAD -- src/ script/ deps.edn` = 0 lines.
- bench.clj:112 still calls `(bk/cancel! b oid)` (2-arg) — the known-red cancel-arity defect is unrepaired in code (as expected, no code changes this frame).

## Parallel tangle

- Observed 09:50–09:58: HEAD stayed 500ab94, no `.git/*.lock`, no frame-382 evidence — no concurrent claim on the next frame.
- This frame 09:58 canonicalized as **382** (no duplicate; previous canonical = 381 at 09:49).

## NEXT / falsify status

- NEXT 未実測 0 件のまま (fix 未着手). 本枏の反証なし, 発覚 0 件, 新規 hypothesis なし.
- スコア 7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 36 件 照合, 累算 overflow クラス falsify-8〜12 は OPEN のまま).
- 残作業は不変: fix 実装 (validate-i53-halt パッケージ = api/validate i53/notional/balance-domain gates + 累算 sum gates :deficit/:funding-residue/:fees-collected/collateral + fx/mul-rate pre-check + REDUCING 積 pre-limit) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化.

## 次ランナー

負荷 <20 (全 window) 突入後最初の枏で全 gate 再実測 (新規実測時 suite **131** / parity **39** / bench **91**; 引用正本は frame-286 / suite-130 / e81a243 の test-286-*.log).

## Recorded

- This skip md: evidence/2026-09-09-0958-maturity-remeasure-skip.md
- Citation check: evidence/skip-check-0909-0958.txt
- Ledger line appended to status/maturity.md (frame 382)