# frame 458 genuine run (2026-09-16 00:36–00:4x, cron)

## Load gate

00:37 pre-run direct: 1-min 19.31 / 5-min 19.18 / 15-min 19.29 — all <20, gate PASSED (first run-allowed frame since 09-15 02:2x). Post-run 00:42: 14.11/16.25/17.89.

## HEAD / src invariance

- HEAD **0a99c9c2a1275d32ba2e99247f43978e62bf1645** unchanged (frame 442–457 同一, direct-measured 00:36)。
- git diff HEAD -- src/ script/ deps.edn = **0 lines**。untracked = maturity.md (M) + evidence のみ。No code changes (cron)。

## Gates 実測 (本枠, すべて新規実測)

1. **JVM suite: PASS — suite 141**。rename-copy /tmp/tori-f451 `clojure -M:test` → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors. JVM_EXIT=0** (evidence/test-jvm-458.out + .exit)。
2. **nbb suite: PASS — 47 度目同 count 実測 (suite 141)**。2 段 classpath 規約 (第 1 段 `kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` EXIT=0 → 第 2 段 `--classpath "src:test:<第1段出力>"`) → **357/915, 0F/0E, namespaces 17/17, TESTS-ON-NBB pass, NBB_EXIT=0** (evidence/test-nbb-458.out + .exit)。
3. **parity: 43 度目実測成立 — 両 runtime root byte 一致**。JVM /tmp/tori-f451 `clojure -M:parity` (PJ_EXIT=0) vs nbb 3 要素 classpath (`"../text/src:src:<deps>"` — **bare `-e` 呼びは 2 段 classpath では不十分**: `kotoba.lang.text` → `kotoba.bytes.sha256` の 2 連 unresolved を実測, deps 要素 + ../text/src の両方が要る) → FLAT ROOT **b4322bed…43445a** / STATE ROOT **d1ebb9d3…7e3aed7f** 両側一致, PROOF a 10 true (evidence/parity-jvm-458.out / parity-nbb-458.out / parity-nbb-458.err)。frame-440/456 baseline と同一。
4. **fuzz digest: 36 度目実測成立 — byte-identical**。JVM `/tmp/tori-f451` `clojure -M -e "(load-file \"evidence-fuzz-seeded.cljc\") (fuzz-seeded/run)"` (FJ_EXIT=0; `-i` 併用は `-e` を file として扱い FileNotFoundException — 誤り, 素 `-e` が正) vs nbb driver 3 要素 classpath (FN_EXIT=0) → diff = **0 bytes** (echo 行除外後, evidence/fuzz-diff-458.txt)。seed 0/1 digest は 0841/1114/2030/440 baseline と一致。

## Scores / 判定

- 発覚 0 件, 新規 hypothesis なし。スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 16 件, f8–f17 OPEN)。
- bench は本枠 not-run (budget; 3× stable は frame-451/453/455 実測済みで前提維持)。再現性 3 昇格は repo copy への 3 部 bench fix 着地 (f15/f16/f17) が条件 — cron は code change 禁止のため不変。

## 残作業 (不変)

1. validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 7 site + 集計 site [falsify-14 commit.cljk:113/:115] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp)。
2. bench fix 3 部着地 → repo copy で n≥1M 3 回安定 → 再現性 3。
3. fuzz 常設化 + nbb bootstrap 2 段恒久化 + JVM .cljk runner。

次枠番号 = **459**。
