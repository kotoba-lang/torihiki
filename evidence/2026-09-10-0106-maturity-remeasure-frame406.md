# 2026-09-10 0106 maturity remeasure — frame 406 genuine run (no code changes, cron)

負荷 01:06 uptime 直測 1-min **9.73** / 5-min **9.25** / 15-min **12.58** — 全 window <20 で gate 成立 → 全 gate 新規実測。

- HEAD **d8fb6be** (frame 404/405 skip commit と同一, コード不変; git status は maturity.md 修正 + untracked 診断スクリプトのみ, src/ script/ deps.edn 変更なし)。
- JVM `clojure -M:test` → **357 tests / 915 assertions, 0 failures, 0 errors** (test-jvm-406.out, JVM_EXIT=0)。
- nbb → 最初の呼び出しは bootstrap 依存で `Could not find namespace: torihiki.address-test` で落ちた (test-nbb-406.err; `--classpath "../text/src"` 単独では不十分 — nbb-classpath.cljs 出力を外側 classpath に与える 2 段構成が必要):
  `nbb --classpath "$(nbb --classpath '../text/src' script/nbb-classpath.cljk)" script/tests-on-nbb.cljk`
  → **357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass** (test-nbb-406.out, NBB_EXIT=0) — 両 runtime 同一カウント **38 度目**実測 (**suite 135**)。
- bench `clojure -M:bench` → 既知赤 **bench-tape-cancel-arity 31 実行目** (bench-406.err: ArityException bench.clj:112, Wrong number of args (2) passed to torihiki.book/cancel!, n=5,000,000; BENCH_EXIT=1) — 再現性 2 のまま (bench **95** 相当)。
- falsify/parity 新規実測なし。発覚 0 件, 新規 hypothesis なし。
- スコア 7 軸変更なし (**spec 3 / 実装 3 / テスト 3 / 反証 3 / 再現性 2 / governor 1 / 運用 1**; 反証実施 13 件確定, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 6 site + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix。
- 次枠番号 = **407** (新規実測時 suite 136 / bench 96)。
