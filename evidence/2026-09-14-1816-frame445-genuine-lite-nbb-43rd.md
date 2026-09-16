# 2026-09-14 18:1x frame 445 — load gate skip→genuine-lite (nbb suite 43rd pass; JVM suite not-run, budget)

## Load gate (18:16 uptime 直測, /tmp/tori_frame445.txt)

1-min **11.97** / 5-min **12.59** / 15-min **14.64** — 全 window <20 → gate 成立 (genuine 枠)。

## HEAD / src invariance (直接実測)

- HEAD **0a99c9c2a1275d32ba2e99247f43978e62bf1645** (frame 442 と同一; 18:16 実測)。
- src/ script/ deps.edn 変更なし (git status: maturity.md M + evidence 作業ファイルのみ)。

## Gates 実測 (本枠)

1. **nbb suite: PASS — 43 度目**。2 段 classpath 規約どおり
   (`kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` → exit 0,
   第 2 段で tests-on-nbb) → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.
   namespaces 17/17, TESTS-ON-NBB: pass** (exit 0, evidence/test-nbb-445.{out,exit}, .err 空)。
   - 呼び出しメモ: 第 1 段出力を $() に渡す際に EOF 行を混ぜない (本枠初回は NBB_EXIT=1
     kotoba.bytes.sha256 不解決 — 混入行が classpath を壊した人為ミス, 規約自体は問題なし)。
2. **JVM suite / parity / fuzz / bench: not-run** (runtime budget 枯渇 — 正本引用維持:
   suite **137** / parity 40 / fuzz 33 / bench 赤累計 33 実行目消化なし, 引用正本 frame-441 base)。
   - rename-copy /tmp/tori-falsify15 は現存を確認 (test 17 ns, .cljk 0 件,
     bench.cljc:112 owner wiring `(bk/cancel! b (bit-and i 1023) oid)` 現存) —
     次 genuine 枠で JVM suite 42?→ suite 138 再実測に即使用可。

## Scores / 判定

- 発覚 0 件 (classpath 混入行は計測者ミスで defect なし), 新規 hypothesis なし。
- スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 15 件, f8–f15 OPEN のまま)。

## 残作業 (不変)

1. validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate
   6 site + 集計 site [falsify-14] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 +
   settle-deficit delta clamp)。
2. bench-tape-cancel-arity fix 第 2 部 (tape 側 cancel 分岐到達性 — f15 で cancelled=0) →
   n ≥ 1M cancelled>0 3 回安定で 再現性 3。
3. fuzz 常設化 + nbb-classpath bootstrap (2 段構成) + JVM .cljk runner 恒久化。

次枠番号 = **446** (新規実測時 suite 138 / parity 41 / bench 34 実行目)。
