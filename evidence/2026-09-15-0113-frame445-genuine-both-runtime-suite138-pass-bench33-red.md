# 2026-09-15 01:1x frame 445 — genuine run (load gate passed): **両 runtime suite 357/915 PASS 同一カウント (suite 138) + bench 33 実行目赤再確認**

## Load gate

1-min load **15.77** (< 20) at 01:12 → gate 成立 (genuine run)。

## HEAD / src invariance

- HEAD **0a99c9c** (frame 442 — maturity citation refresh)。
- src 本体無変更。rename-copy は /tmp 実測専用 (`/tmp/torihiki-renamecopy-445`, src/test/bench の `.cljk`→`.cljc` 改名複写 + deps.edn 複写, 40 file)。

## Gates 実測 (本枠)

1. **JVM suite: PASS — Ran 357 tests containing 915 assertions. 0 failures, 0 errors** (exit 0, `/tmp/torihiki-445-jvm.out`, 17/17 ns)。rename-copy 経路 2 度目の転用成功。
2. **nbb suite: PASS — 同一カウント 357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB pass** (exit 0, 2 段 classpath 規約 `kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` → `$CP` + `tests-on-nbb.cljk`)。**suite 計上 137 → 138 前進**。
3. **bench: 赤 33 実行目**。rename-copy 側で bench/*.cljk を .cljc 改名して `-M:bench` を実行 (attempt 1/2 は bench dir 未複写/未改名で FileNotFoundException — 改名要確認)。attempt 3 は tape 生成 5,000,000 ops まで進み **ArityException at torihiki.bench/run-tape (bench.cljc:112), Wrong number of args (2) passed to: torihiki.book/cancel!** — 既知赤の再確認 (evidence/f445-bench4.txt 抜粋)。falsify-15 (owner wiring で cancelled=0 の新規欠陥) の前提のまま fix 未着手。
4. parity / fuzz: not-run (budget) — parity 40 度目 / fuzz 33 度目 引用維持。

## Scores / 判定

- 発覚 0 件 (新規 defect なし — bench 赤は既知欠陥の再現, rename-copy が bench も .cljk→.cljc 改名で JVM 可走である点の追加実測のみ)。
- スコア 7 軸変更なし **3/3/3/3/2/1/1**。
- 次枠番号 = **446** (falsify-14 複数アカウント deficit 合算が最優先残; bench 34 実行目 / parity 40 / fuzz 33)。
