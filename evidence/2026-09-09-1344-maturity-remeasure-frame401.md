# torihiki maturity remeasure — frame 401 (2026-09-09 13:44 genuine run)

Load gate (cron): 13:44 uptime 1-min **9.30** (<20) / 5-min 32.26 / 15-min 37.38 — declining-load ambiguous
window, treated as genuine per frame-397 precedent. Terminal backend empty-output fault present (known);
all output obtained via redirect-to-file + read_file.

HEAD **915f832** (frame 398/399/400 と同一, 並行重複なし). `git diff HEAD -- src/ script/ deps.edn` = 0 bytes
→ no code changes this frame. Untracked: maturity.md + diagnostic scripts + evidence skip records only.

## Results (all gates newly measured)

- JVM `kbb -M:test` → **357 tests / 915 assertions, 0 failures, 0 errors**, JVM_EXIT=0
  (evidence/test-jvm-401.out)
- nbb (bootstrap fix `kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` 経由) → **357/915, 0F/0E,
  namespaces 17/17, TESTS-ON-NBB: pass**, NBB_EXIT=0 (evidence/test-nbb-401.out)
  → 両 runtime 同一カウント **37 度目**実測 (**suite 134**)
- Seeded fuzz digest: fuzz-jvm-401.out vs fuzz-nbb-401.out diff = **0 bytes** (echo 行除いた raw diff 空)
  → byte-identical **32 度目** (evidence/fuzzdiff-401.txt)
- bench `kbb -M:bench` → 既知赤 **bench-tape-cancel-arity 30 実行目**
  (`ArityException at bench.clj:112, Wrong number of args (2) passed to: torihiki.book/cancel!`,
  bench-401.err) — 再現性 2 のまま (bench **94** 相当)

## Scores

7 軸すべて変更なし: **3/3/3/3/2/1/1**. 発覚 0 件, 新規 hypothesis なし.

## NEXT (unchanged)

validate-i53-halt fix パッケージ (api i53 検査 + notional 上限 + balance-domain gate + 累算 sum gate 6 site
+ fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp) + bench 3 箇所
(bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix.
次枠番号 = **402**; 新規実測時 suite **135** / parity 39 / bench **95**.
