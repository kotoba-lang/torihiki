# 2026-09-14 21:04 frame 447 genuine run (no code changes, cron)

## Load gate (21:05 uptime 直測, /tmp/tori_f447_load.txt)

1-min **12.83** / 5-min **12.81** / 15-min **12.19** — 全 window <20 → gate 成立 (genuine 枠)。

## HEAD / src invariance (直接実測)

- HEAD **0a99c9c2a1275d32ba2e99247f43978e62bf1645** (frame 442–446 と同一)。
- git diff HEAD -- src/ script/ deps.edn = **0 lines**。untracked = maturity.md (M) + evidence のみ。

## Gates 実測 (本枠)

1. **JVM suite: PASS — suite 138**。cutover 後の kbb -M:test は既知 bootstrap 落ち
   (`kbb: 6 dep(s) are not :local/root…` → `Could not find namespace: kotoba.bytes.sha256`,
   /tmp/tori_test447.err) のため、**rename-copy /tmp/tori-falsify15** (byte-identical
   .cljc copy, HEAD 8af801db 由来) で `clojure -M:test` → **Ran 357 tests containing
   915 assertions. 0 failures, 0 errors. EXIT=0** (evidence/test-jvm-447.{out,exit})。
2. **nbb suite: PASS — 41 度目** (2 段 classpath 規約: 第 1 段
   `kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` EXIT=0 →
   第 2 段 `kbb --backend sci --classpath "src:test:<第1段出力>" script/tests-on-nbb.cljk`)
   → **Ran 357 tests containing 915 assertions. 0 failures, 0 errors. namespaces 17/17.
   TESTS-ON-NBB: pass. EXIT=0** (evidence/test-nbb-447.{out,exit})。
   - 両 runtime 同一カウント **41 度目実測 (suite 138)**。
3. parity / fuzz / falsify-14 実測: **budget 枯渇で not-run** (引用正本 frame-441 base:
   parity 40 / fuzz digest 33 / bench 赤累計 33 実行目消化なし)。

## falsify-16 静的解析の前進 (実測は not-run)

bench.cljc tape 側 cancel 分岐 (`slot (bit-and (- w 1 (bit-and i 524287)) ring-mask)`) と
book.cljc cancel! の検査列 (slot 域 / o-gen / o-qty / o-owner 一致) を実読:
owner 値は tape 側 `(bit-and i 1023)` で place/cancel 両側同一構成のため
owner-mismatch 単独では cancelled=0 を説明できない (frame-446 の静的判定を再確認)。
oid-of = slot×gen-mod + gen / slot-of = quot oid gen-mod の構成は整合。
cancelled=0 の残り候補 (ring slot 計算の w 起点ずれ / gen-stale) は
次 genuine 枠の instrumented bench (cancel!=0 理由カウント) で判別する。

## Scores / 判定

- 発覚 0 件, 新規 standing hypothesis なし。
- スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 15 件, f8–f15 OPEN のまま)。

## 残作業 (不変)

1. validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate
   6 site + 集計 site [falsify-14] + fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 +
   settle-deficit delta clamp)。
2. bench-tape-cancel-arity fix 第 2 部 (tape 側 cancel 分岐到達性, f15/f16) →
   n ≥ 1M cancelled>0 3 回安定で 再現性 3。
3. fuzz 常設化 + nbb-classpath bootstrap (2 段構成) + JVM .cljk runner 恒久化。

次枠番号 = **448** (新規実測時 parity 41 / bench 34 実行目 / falsify-16 instrumented bench 優先)。
