# torihiki maturity remeasure — 2026-09-04 09:55 JST (battery NOT RUN — load gate; falsify-11 honest-walk completed & reviewed)

- host load across the iteration window: 09:24 **23.19/25.96/23.29** → 09:29 15.43/20.75/21.85 → 09:32 17.54/19.66/21.26 → 09:37 **35.76/27.81/24.26** → 09:42 18.50/23.27/23.41 → 09:48 **25.09/27.45/25.41** → 09:55 **31.21/24.17/23.90** — 1-min dipped below 20 twice but 5/15-min never did; `kbb -M:test` / nbb test / fuzz / bench **skipped** per iteration rule (all three < 20 required). Code HEAD `dd55c85` unchanged, `git diff` empty, no code changes
- the falsify-11 JVM honest-walk (PID 54913, started 07:30, 2h07m elapsed, 71:23 CPU) **completed exit=0 at 09:42** — the only state change this window; host load spikes are external (PID 2897 is an unrelated long-running `cloud.itonami.app.server`, up 12h, 724 CPU-min)

## NEW evidence: falsify-11 JVM honest-walk (completed output, first review)

`evidence/falsify11-jvm.out`:
- **honest-walk: 981,177 crosses of real trading activity drove `:fees-collected` from 0 to 9,007,204,512,755,301 — i.e. past 2^53−1 (9,007,199,254,740,991) with no throw and no gate.** Crossing at cross 981177 (fees 9007204512755301 > 2^53−1); prior cross 981176 fees 9007195332755288 still in-domain. Collaterals stayed in-domain (coll1 pinned at i53-max by design, coll2 4.41e15) — the accumulator alone escaped the domain
- seeded-crossing re-confirms cross-runtime root divergence at cross1: JVM fees **…993 (odd)** root `d13ec02e…` vs nbb (falsify11-nbb.out) fees **…992 (even)** root `2c059f52…` — 2^53+1 is double-non-invertible on JS → rounding, identical signature tx stream, throw 皆無. Matches the 0745 verdict baseline exactly
- implication: the :fees-collected sum gate in NEXT is not only adversarial-hardening — **a legit long-running validator reaches 2^53 fees in ~981k honest crosses**, so this is a liveness/consensus bound on production operation, strengthening the case that the accum gate is the single highest-leverage fix

## Scores

7 軸すべて現状維持 (battery 未実行のため新規カウント実測なし): spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。最新実測根拠は 0839 battery のまま (test-0839/test-nbb-0840: 357 tests / 915 assertions 31 度目, fuzz byte-identical 28 度目, bench 24 実行目連続赤 bench-0843.err)。OPEN 赤 6 件すべて不変 (fix 未着手)。falsify-11 honest-walk 完了は反証 3 の根拠を補強するが、harness 常設化条件 (4) は未達のままスコア不変

## NEXT (最高レバレッジ — 不変)

validate-i53-halt fix, scope 確定済み (falsify-6〜11): (a) api/validate :bad-amount i53 上限 (:deposit amount / :order qty) + (b) notional 上限 level×qty ≤ i53-max + (c) balance-domain gate `collateral + amount > i53-max` 拒否 + (d) 累算 sum gate (:deficit / :funding-residue / :fees-collected + 各 (fnil +/− 0)) — validate 層か no-op clamp, apply-block 内 throw 禁止 + (e) fx/mul-rate 事前積界限 + rate 上限。honest-walk 実測 (981k crosses で 2^53 crossing) により (d) の :fees-collected は敵対的シナリオでなく運用必達ゲート。fix 後: bench-tape-cancel-arity fix (bench.clj:112 owner 付き 3 引数) → n ≥ 1M 低負荷 3 回安定で 再現性 3 → fuzz suite 常設化で テスト/反証 4
