# 2026-09-14 06:3x frame 440 — genuine run (load gate passed): **parity 40 度目実測成立 (両 runtime root byte 一致) + fuzz digest 33 度目実測成立 (diff 空)**

## Load gate (pre-run 直測 06:25)

1-min **8.39** / 5-min **9.68** / 15-min **10.10** — 全 window <20 → gate 成立 (genuine run)。

## HEAD / src invariance

- HEAD **8af801db** (frame 428–439 と同一)。
- src/ script/ deps.edn 変更なし (rename copy は /tmp 実測専用)。

## Gates 実測 (本枠)

1. **parity: 40 度目の実測成立 — JVM `clojure -M:parity` (rename-copy /tmp/tori-jvm-suite-438 経路, exit 0) vs
   nbb `kbb --backend sci --classpath "$CP" -e "(require '[torihiki.parity :as p]) (p/report)"`
   (2 段 classpath 規約, exit 0)**。
   - FLAT ROOT **b4322bedd406112e6c2c7339e93d646eb7852959def143901961b3be4f43445a**
   - STATE ROOT **d1ebb9d30cd51516c14bc9e3c0007d806b423e33cd3bd64200362c7e7e3aed7f**
   - 両 runtime 完全一致, PROOF a 10 verifies true (evidence/parity-jvm-440.out / parity-nbb-440.out)。
   - 正本引用 frame-413 の parity 39 度目を退役し本枠実測に移行。
2. **fuzz digest: 33 度目の実測成立 — byte-identical**。JVM: rename-copy 側に
   `evidence/fuzz-seeded.cljk` を byte 同一 `.cljc` copy (`evidence-fuzz-seeded.cljc`) して
   `clojure -M -e "(load-file …) (fuzz-seeded/run)"` (exit 0, evidence/fuzz-jvm-440.out, stderr 空)。
   nbb: 既存常設 driver `evidence/fuzz-nbb-driver.cljk` を 2 段 classpath で実行
   (exit 0, evidence/fuzz-nbb-440.out, stderr 空)。
   `diff fuzz-jvm-440.out fuzz-nbb-440.out` = **1 行のみ** (JVM 側 echo `#'fuzz-seeded/run` — load-file の
   戻り値印字で実データではない) = digest 実質 byte-identical (evidence/fuzz-diff-440.txt)。
   0841/0842/1114/1118/2030 baseline と seed 0/1 の digest 一致を head で確認。
   試行錯誤 1 件: 素 `clojure -M` (repo cwd) は fuzz-seeded.cljc (存在しない) + classpath 無しで落ちる —
   正規 JVM 呼び出しは rename-copy 側 + load-file 構成で確定。
3. bench / suite: not-run (budget) — suite 137 / nbb 42 度目 / bench 赤累計 33 実行目 引用維持 (frame-438/439)。
   再現性 2 のまま (bench fix + n≥1M 低負荷 3 回安定が条件)。

## Scores / 判定

- 発覚 0 件, 新規 hypothesis なし。スコア 7 軸変更なし **3/3/3/3/2/1/1**。
- NEXT 残: JVM suite 呼び出し規約恒久化 (runner .cljk 対応か build 時 rename) → bench-tape-cancel-arity
  fix (bench.clj:112 owner 付き 3 引数) + n≥1M 低負荷 3 回安定実測で 再現性 3 → fuzz suite 常設化で
  テスト/反証 4 → falsify-14 (複数アカウント deficit 合算) 実測。
- 次枠番号 = **441**。
