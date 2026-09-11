# falsify-10 — funding 累算 `:funding-residue` (funding.cljc:138) は sum 無検査で 2^53 を越え、無音 cross-runtime root 分岐を起こす

- date: 2026-09-04 (JST, host load ~14–18 — under gate, measured)
- hypothesis: evidence/2026-09-04-falsify-10-funding-residue-accum-overflow-notrun.md (実行前登録)。仮説の R0 計算に減算ミスがあったため初回 JVM 実測 (evidence/falsify10-jvm.out 第 1 版) で crossing が起きず、R0 を訂正 (9007181254740991) して再実測 — 修正経緯は本欄に記録。
- runtimes: JVM `kbb -M -e '(load-file "evidence/falsify10-funding-accum.cljk") (load-file "evidence/falsify10-jvm-driver.cljk")'` → falsify10-jvm.{out,err} (exit=0); nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/falsify10-driver.cljk` → falsify10-nbb.{out,err} (exit=0)
- method: `fnd/apply-funding` (funding.cljc:130–140) は支払い `p` を `fx/mul-rate` で fx/check するが、`(update :funding-residue (fnil + 0) p)` と collateral 引き落とし `(fnil - 0) p` は**累算 sum 無検査** — falsify-8 (collateral) / falsify-9 (:deficit) と同一クラス (delta 検査済み / sum 無検査)。probe は実物の `st/apply-tx {:tx :funding-settle}` を **residue を直下に seed した** state に適用 (synthetic-seeded: ~979k 時間の honest 累算が残す形の時間圧縮のみ、accumulator と適用経路は無修正の production code)。falsify-9 と同じ方式。

## スケール突破 (NEXT の「~653k cross は非現実的」を回避した点)

mul-rate の積上限 (falsify-9: `(* amount rate) < 2^63` で JVM long overflow 回避) により 1 settle の p は ~9.2e9 が上限。積が 2^53〜2^63 の間でも **floor 商 (`/1e9`) の境界から積が十分離れていれば** (誤差 ≤1024 « 5e8) p は両 runtime で bit-identical になる:
- 実運用レート (empty accumulator): interest 1bp/8h → hourly `fdiv(1e5, 8)` = **12500** (cap 4e7 未達の本物の値)
- notional = 80000×p + 40000 (p = 9000000001, odd) → 積 = 1e9×p + 5e8 (境界距離 5e8)
- 実測 (payment-identity probe): JVM product 9000000001500000000 / boundary-dist 500000000 / p 9000000001; nbb 同 product / boundary-dist **500000256** (丸め +256) / p **9000000001** — p 完全一致 ✓

## Measured — probe A: residue-seeded-crossing (両 runtime, throw 皆無)

seed: `:funding-residue = R0 = 9007181254740991` (= 2^53+1−2p, 奇数 < 2^53 = double 可逆), account 7 long size 720000000120000, oracle 1, collateral i53-max。`p = 9000000001 (odd) / settle`。

| step | JVM residue / root | nbb residue / root | 一致? |
|---|---|---|---|
| settle 1 | 9007190254740992 (even, <2^53) / 0fdc4adb… | 同 / 0fdc4adb… | ✅ |
| settle 2 | **9007199254740993 = 2^53+1 (奇数, 非可逆)** / root **adb99f90…** | **9007199254740992** (丸め) / root **549280fb…** | ❌ **発散** |
| settle 3 | 9007208254740994 / 8d5527ad… | 9007208254740992 / 20e2f97a… | ❌ 分岐持続 (even 可逆値に着地したが両 runtime の値が ±2 離れたまま — falsify-8/9 型の「再収束」は今回は起きない: 以後の p が odd で parity が保存されるため) |
| settle 4 | 9007217254740995 / 0d02fd6a… | 9007217254740992 / 040450bd… | ❌ 分岐持続 |

collateral は全 step で両 runtime 完全一致 (9007190254740990 → 9007163254740987, 全程 < 2^53) — **分岐は :funding-residue のみが運び**、それは state root に入る (state.cljc:1603 `encode-clearing-totals`)。rate は 12500 で両 runtime 一致 (clamp 域外に出ず、下流の支払い金額も一致 — つまり検知手段は root 以外にない)。

## Measured — probe B: honest-walk (JVM のみ, seed なし)

residue 0 から実レートで **1,000,801 回の実物 funding-settle、throw 皆無** (JVM のみ。1M apply-block ループは falsify-9 が nbb で非現実的と判定したスケールにつき skip — 発散 verdict は probe A が担い、本 probe は seed なしでも到達することの実証):

- residue(1000799) = 9007191001000799 (奇数, < 2^53, 可逆)
- residue(1000800) = **9007200001000800** (even, [2^53,2^54) は even grid で可逆 — 予測どおり)
- residue(1000801) = **9007209001000801 (奇数 → nbb なら非可逆, ここで root 分岐するはず)**
- max |collateral| = 9007190254740990 (全程 i53 域内)

## Verdict

**Confirmed.** `:funding-residue` 累算 (funding.cljc:138) は falsify-8/9 と同一クラスの第 3 例: 各支払い p は `fx/mul-rate` の fx/check を通る (in-domain) が、sum は無検査。in-domain な `:funding-settle` のみで residue は 2^53 を越えて double 非可逆になり、**throw 皆無のまま同一演算列で JVM/nbb の state-root が不一致** — 署名済み tx 列で validator 間合意不能 (無音 consensus break)。今回の実測の追加知見:

1. **mul-rate 積上限は 2^53 に留まる必要はない**: 積が 2^53〜2^63 でも floor 商の境界距離 (ここでは 5e8) が double 誤差 (≤1024) を圧倒すれば p は両 runtime bit-identical。falsify-9 で「~653k cross は nbb 非現実的」とされた fees-collected も、この境界距離 trick + falsify-9 同様の seeded crossing probe なら実測可能 (falsify-11 候補)。
2. **分岐の永続性**: falsify-8/9 では後続の even 可逆値への着地で偶然再収束したが、p が odd の場合 nbb の丸め残差 (±2) が以後の累算に保存され、probe A の範囲では再収束しなかった (settle 3–4 分岐持続)。発散則 (ii)「可逆値着地で再収束」は p の parity に依存する — odd p では丸め残差が消えない。
3. rate は clamp (4e7 cap) に達しない限り下流で同一のため、分岐の唯一の観測点は state root — 検知は root 比較のみ。

## Fix への含意 (NEXT への反映)

- falsify-8/9/10 で共通化された fix パッケージ: **累算 site の sum gate** — `:funding-residue` (funding.cljc:138), `:deficit` (clearing.cljc:711), collateral 引き (`fnil - 0` / `fnil + 0` 各所), `:fees-collected` (clearing.cljc:410/560) は、累算後の値が i53 domain を出る seek を validate 層か no-op clamp で拒否すること。fx/check throw による gate は falsify-6/7/9 型の apply-block 内 halt を作るので禁止。
- validate-i53-halt fix (balance-domain gate) と同一の設計律: gate は validate 層 (:bad-amount 系) か no-op で実装、apply-block 内 throw 禁止。
- funding 固有: `apply-funding` は転送 (longs pay / shorts receive) で sum-to-zero だが residue は丸め残差の蓄積所なので、cap を rate 側 (既存 4e7/hour) に掛けても residue の累算上限は時間とともに非有界 — 累算 gate が必要。
