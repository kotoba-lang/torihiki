# falsify-11 — `:fees-collected` 累算 (clearing.cljc:410/560) は sum 無検査で 2^53 を越え、無音 cross-runtime root 分岐を起こす

- date: 2026-09-04 (JST, host load 14–35 変動 — 実測は低負荷帯で実施)
- hypothesis: evidence/2026-09-04-falsify-11-fees-collected-accum-overflow-notrun.md (実行前登録)。level/qty の因数分解ミスと book ladder 制約 (n-levels 1024 tick-indexed) で 2 回の harness 修正を経て実測 (経緯は本欄末尾に記録)。
- runtimes: JVM `kbb -M -e '(load-file "evidence/falsify11-fees-accum.cljk") (load-file "evidence/falsify11-jvm-driver.cljk")'` → falsify11-jvm.{out,err}; nbb `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)" evidence/falsify11-driver.cljk` → falsify11-nbb.{out,err} (両 exit=0)
- method: `cl/apply-fill` (clearing.cljc:410, `(update :fees-collected (fnil + 0) (- fee share))`) は各 fill の fee を `fx/mul-rate` で fx/check するが累算 sum は無検査 — falsify-8 (collateral) / falsify-9 (:deficit) / falsify-10 (:funding-residue) と同一クラス (delta 検査済み / sum 無検査) の**第 4 例**。`:fees-collected` は state root に入る (state.cljc:1602 encode-clearing-totals)。probe は実物 `st/apply-block` の `:order` tx (maker → taker cross, taker-fee-rate 12500 / maker-fee-rate 0) を使用。fees-collected のみ時間圧縮 seed (probe B)、accumulator と適用経路は無修正の production code。

## 境界距離 trick の fee 経路への適用可否 (NEXT の「まず検証」への答え: **適用可**)

fee = `fx/mul-rate(|level×qty|, rate)` = floor(N×R/1e9) で、falsify-10 と同一構成が成立する:

- N = level×qty = **720 × 1000000001500 = 720000001080000** (< 2^53 — level は book ladder が tick-indexed (n-levels 1024) のため 720、qty を ~1e12 に取る)
- R = 12500 (market `:taker-fee-rate`、実運用スケール)
- 積 P = N×R = **9000000013500000000** (< 2^63 — falsify-9 の cap)
- 境界距離 = P mod 1e9 = **500000000** (5e8 » double 誤差 ≤1024 @ ~9e18)
- fee = floor(P/1e9) = **9000000013 (奇数)**

## Measured — probe A: payment-identity (両 runtime)

| runtime | notional | product | boundary-dist | fee | fee-odd |
|---|---|---|---|---|---|
| JVM | 720000001080000 | 9000000013500000000 | **500000000** | **9000000013** | true |
| nbb | 720000001080000 | 9000000013500000000 | 500000256 (丸め +256, 商不変) | **9000000013** | true |

fee 完全一致 ✓ — NEXT が問いていた「fill 1 回の fee 経路への境界距離 trick 適用可否」は**可**と実測確定。

## Measured — probe B: fees-seeded-crossing (両 runtime, throw 皆無)

seed: `:fees-collected = F0 = 9007190254740980` (= 2^53+1−fee, even, < 2^53, double 可逆 — ~1e6 honest fee-charged cross が残す形の時間圧縮のみ)。accounts 1/2 に i53-max deposit。1 cross = taker fill 1 回につき fees-collected +9000000013 (maker rate 0)。

| cross | JVM fees / root | nbb fees / root | 一致? |
|---|---|---|---|
| seed | 9007190254740980 / b264b448… | 同 / b264b448… | ✅ (seed 完全一致) |
| 1 | **9007199254740993 = 2^53+1 (奇数, 非可逆)** / **d13ec02e…** | **9007199254740992** (丸め) / **2c059f52…** | ❌ **発散** |
| 2 | 9007208254741006 / 16b23892… | 9007208254741004 / ad546332… | ❌ 分岐持続 (Δ2) |
| 3 | 9007217254741019 / ca50c7cc… | 9007217254741016 / 1133eace… | ❌ 分岐持続 |
| 4 | 9007226254741032 / 9716dbe3… | 9007226254741028 / b33a2253… | ❌ 分岐持続 |

collateral は全 cross で両 runtime 完全一致 (coll1 = i53-max 固定, coll2 = i53-max → 9007163254740939) — **分岐は `:fees-collected` のみが運び**、それは state root に入るため検知手段は root 比較のみ。throw は 1 件もない。falsify-10 と同じく奇数 fee の parity 保存で丸め残差 ±2 が持続し再収束しない。

## Measured — probe C: honest-walk (JVM のみ, seed なし)

fees-collected 0 から実 cross のみで累算 (2 taker 口座を中間ローテーション, side 交互で open/flip のみ, oracle = level で unrealized 0 / liquidation sweep 不発):

- **cross 981,177 で crossing: fees = 9007204512755301 (奇数 ≥ 2^53 → nbb なら非可逆, ここで root 分岐するはず)**, throw 皆無
- cross 981175: fees 9007186152755275 (odd, < 2^53, 可逆) / cross 981176: 9007195332755288 (even, 可逆)
- max-abs-coll = i53-max (全程 i53 域内), 実行時間 ~66 min JVM

seed なしでも到達することを別途実証 (falsify-10 probe B と同様の役割)。

## Verdict

**Confirmed.** `:fees-collected` 累算 (clearing.cljc:410 apply-fill / :560 spot-fill) は falsify-8/9/10 と同一クラスの第 4 例: 各 fill の fee は fx/check を通る (in-domain) が sum は無検査。in-domain な `:order` のみで fees-collected は 2^53 を越えて double 非可逆になり、**throw 皆無のまま同一演算列で JVM/nbb の state-root が不一致** — 署名済み tx 列で validator 間合意不能 (無音 consensus break)。検知は root 比較のみ。

### 副次発見 (NEXT への追加)

1. **reduce 経路の unchecked 積 overflow (新規, falsify-9 クラス)**: `apply-fill-to-position` の REDUCING 分岐 (clearing.cljc:157) が `(* entry-notional closed)` を無検査で計算する。entry-notional 7.2e14 × closed 1e12 lots で **JVM は bare "long overflow" throw (where=nil, apply-block halt 型)、nbb は double 丸めで無音通過の非対称** (honest-walk 2 実行で実測, stack: clearing.cljc:158)。fix への含意: reduce 分岐にも `entry-notional × closed < 2^63` の事前界限 (または両 runtime 同一 overflow 挙動) が必要。fill がちょうど position を 0 に戻す (new-size = 0) ケースは FLIP ではなく REDUCING に落ちる (flip 判定は pos?/neg? のみ) — 境界ケースとして実務でも到達可能。
2. **position 線形増大の organic notional halt**: 同一 account が同一方向の fill を重ねると position size が 13×qty に達した時点で `fx/check :notional` が throw (value = 9360000014040000, falsify-7 の in-domain halt クラスを通常取引列で到達)。
3. fee 経路のスケール上限: mul-rate 積 < 2^63 により 1 fill fee ≤ ~9.2e9、かつ book ladder が tick-indexed (n-levels ≤ 1048576) のため fee を大きくするには qty を ~1e12 にする必要がある — probe 設計上の制約として記録。

## Fix への含意 (NEXT への反映)

- falsify-8/9/10/11 で共通化された fix パッケージに **`:fees-collected` (clearing.cljc:410/560) を追加**: 累算後の値が i53 domain を出る seek を validate 層か no-op clamp で拒否、fx/check throw による gate は禁止 (falsify-6/7/9 型 halt を作る)。
- **`apply-fill-to-position` REDUCING 分岐 (clearing.cljc:157) の `(* entry-notional closed)` に事前界限**を追加 (JVM unchecked halt / nbb 無音通過の非対称解消)。

## 経緯 (harness 修正の記録 — src/test は未変更)

1. 初版 level=30000000005×qty=24000000 は積 7.2e17 (因数分解ミス) で nbb が :notional throw。
2. 2 版 level=6000000010 は N=720000001200000 で boundary-dist 512 — double ulp 2048 に対して狭く nbb fee が 9000000015 に丸め変位 (商不変だが採用せず)、かつ level > n-levels 1024 で cross 不成立を発見 (book は tick-indexed)。
3. 3 版 level=720×qty=1000000001500 で確定。probe B の F0 を `(inc i53-max)` から計算すると nbb で丸め差が出るためリテラル化。
4. honest-walk は 3 度の設計修正を要した: (a) 線形 position → :notional halt (13×N 実測); (b) full-size reduce → JVM long overflow 実測; (c) ローテーション直後の new-size=0 reduce → 同 overflow 実測。最終形は open/flip のみで通過。
