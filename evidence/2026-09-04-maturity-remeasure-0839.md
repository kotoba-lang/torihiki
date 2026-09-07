# torihiki maturity remeasure — 2026-09-04 08:39 JST (load gate cleared)

- host load at start: **13.49 / 16.70 / 19.64** (1/5/15min) — all < 20, first battery-eligible window since 0757 (0808/0826/0721 iterations were not-run on the load gate)
- code: HEAD `dd55c85` unchanged; `git diff` empty; only `evidence/` + `status/` untracked — no code changes
- in-flight (not perturbed, background): falsify-11 JVM honest-walk PID 2897 (started 21:25 prev day, 673 CPU-min, k=500000 @ 08:38, coll3 pinned at 2^53−1) **and a second falsify-11 JVM walk PID 54913** (started 07:30 today, `evidence/falsify11-fees-accum.cljc` + `evidence/falsify11-jvm-driver.clj`, 39 CPU-min @ 08:38). Completed output belongs to a later iteration's review.

## Results (all green/red as expected, no score change)

| check | result | evidence |
|---|---|---|
| JVM test | 357 tests / 915 assertions, 0 failures (**31 度目** 実測) | test-0839.out |
| nbb test | 357 tests / 915 assertions, 0 failures, 17/17 namespaces — 同一カウント (**31 度目**) | test-nbb-0840.out |
| nbb 素呼び pitfall | 再確認: `nbb script/tests-on-nbb.cljs` 素呼びは `Could not find namespace: torihiki.address-test` で即落ちる (test-nbb-0840.err) — classpath は `nbb --classpath "$(nbb script/nbb-classpath.cljs)"` が必須 | test-nbb-0840.err |
| seeded fuzz JVM | 16 seeds 完走, digests | fuzz-jvm-0841.out |
| seeded fuzz nbb | 16 seeds 完走, **JVM と byte-identical** (`diff` 空, **28 度目** 実測) — seed 0 先頭 digest `ada0db86…` は 0657/0608/0712 baseline と一致 → 再現性維持 | fuzz-nbb-0842.out |
| bench | **24 実行目の連続赤**: bench-tape-cancel-arity (`ArityException … bench.clj:112, Wrong number of args (2) passed to: torihiki.book/cancel!`) — harness 未修正のため予測どおり | bench-0843.out / bench-0843.err |

## Scores

7 軸すべて現状維持: spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1。
OPEN 赤 6 件 (validate-i53-halt, cumulative-deposit-divergence, deficit-accum, funding-residue-accum, fees-collected-accum, bench-tape-cancel-arity) は fix 未着手のためすべて不変。

## NEXT (最高レバレッジ)

validate-i53-halt fix は scope 確定済み (falsify-6〜11 で全 site 実測): (a) api/validate :bad-amount i53 上限 (:deposit amount / :order qty) + (b) notional 上限 level×qty ≤ i53-max + (c) balance-domain gate `collateral + amount > i53-max` 拒否 + (d) 累算 sum gate (:deficit / :funding-residue / :fees-collected + 各 (fnil +/− 0)) — 実装は validate 層か no-op clamp, apply-block 内 throw 禁止 + (e) fx/mul-rate 事前積界限 + rate 上限。6 件の OPEN 赤のうち 5 件がこの一括 fix で消える。その次: bench-tape-cancel-arity fix (bench.clj:112 owner 付き 3 引数) → n ≥ 1M 低負荷 3 回安定で 再現性 3。falsify-11 JVM walk 2 本の完了出力は次回レビュー。
