# 2026-09-15 11:1x frame 454 — genuine run (load gate passed): **parity 41 度目実測成立 (両 runtime root byte 一致) + fuzz digest 34 度目実測成立 (diff 実質空)**

## Load gate (pre-run 直測 11:16)

1-min **10.17** / 5-min **11.34** / 15-min **13.29** — 全 window <20 → gate 成立 (genuine run)。

## HEAD / src invariance

- HEAD **0a99c9c2** (frame 442–453 と同一)。
- src/ script/ deps.edn diff = 0 bytes (git diff HEAD --stat 実測空)。実測 copy は /tmp のみ。

## Gates 実測 (本枠)

1. **parity: 41 度目実測成立**。
   - JVM: rename-copy /tmp/tori-f451 `clojure -M:parity` (EXIT=0, /tmp/tori_f454_parity_jvm.{out,err,exit})
   - nbb: 2 段 classpath 規約 `kbb --backend sci --classpath "$CP" -e "(require '[torihiki.parity :as p]) (p/report)"` (NBB_EXIT=0, /tmp/tori_f454_parity_nbb.out)
   - FLAT ROOT **b4322bedd406112e6c2c7339e93d646eb7852959def143901961b3be4f43445a** / STATE ROOT **d1ebb9d30cd51516c14bc9e3c0007d806b423e33cd3bd64200362c7e7e3aed7f** — frame-440 baseline と同一, 両 runtime 完全一致, PROOF a 10 verifies true。diff は nbb 側最終 digest echo 行 1 行のみ (report戻り値, 実データでない)。
2. **fuzz digest: 34 度目実測成立 — byte-identical (実質)**。
   - JVM: /tmp/tori-f451 に evidence/fuzz-seeded.cljk を byte 同一 copy (evidence-fuzz-seeded.cljc, ls で不在確認後に copy — f451 copy には未置だった) → `clojure -M -e "(load-file …) (fuzz-seeded/run)"` (JVM_EXIT=0)
   - nbb: 常設 driver evidence/fuzz-nbb-driver.cljk 2 段 classpath (NBB_EXIT=0)
   - diff = 1 行のみ (JVM 側 echo `#'fuzz-seeded/run`, frame-440 baseline と同一形状) → digest 実質 byte-identical (/tmp/tori_f454_fuzz_diff.txt)。seed 0 digest ada0db86…, seed 1 3a9c8e43… — baseline 一致。
3. bench / suite / falsify-14 集計 site fix: not-run (budget; suite 139 / bench 赤累計 34 引用維持 — frame-452/453)。falsify-14 は frame-452 で CONFIRMED 済み、fix 着地待ち。

## Scores / 判定

- 発覚 0 件, 新規 hypothesis なし。スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証実施 16, 発覚 8; f8–f17 OPEN)。
- 再現性 2 のまま: 3-part bench fix (f15 owner wiring + f16 arg-order + f17 ring-owner) の repo 着地 + n≥1M 3 回安定が条件 (cron は code change 禁止)。
- 残作業不変: validate-i53-halt fix パッケージ (累算 sum gate 7 site — falsify-14 の commit 集計 site 含む) + bench fix 着地 → 再現性 3 + fuzz 常設化 + nbb bootstrap 2 段恒久化 + JVM .cljk runner。
- 次枠番号 = **455** (新規実測時 suite 140 / parity 42 / bench 35 実行目)。
