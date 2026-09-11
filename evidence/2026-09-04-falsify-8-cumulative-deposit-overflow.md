# falsify-8 — cumulative in-domain deposit escapes i53: no halt, but cross-runtime state-root divergence

- date: 2026-09-04 02:48–03:05 JST (host load 18.85/17.35/18.14 — under gate, measured)
- hypothesis: evidence/2026-09-04-falsify-8-cumulative-deposit-overflow-notrun.md (registered 02:32 not-run)
- runtimes: JVM `clojure -M -e '(load-file "evidence/falsify8-halt-paths.cljk") (load-file "evidence/falsify8-jvm-driver.cljk")'` → falsify8-jvm.{out,err} (exit=0); nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" evidence/falsify8-driver.cljk` → falsify8-nbb.{out,err} (exit=0)

## Measured (both runtimes, validate passed nil at every step)

| probe | JVM | nbb | 一致? |
|---|---|---|---|
| deposit i53-max ×1 | coll=9007199254740991 | 同 | ✅ |
| deposit i53-max ×2 (per-block) | coll=18014398509481982 (=2^54−2, double 可逆) | 同 | ✅ |
| deposit i53-max ×3 | coll=**27021597764222973** / root3 ffea1289… | coll=**27021597764222972** / root3 24eca56c… | ❌ **発散** |
| 4th deposit onto 3×i53-max | coll4=36028797018963964 (4×i53-max=2^56−4), root4 f862cb52… | coll4=同 (rounded coll3 + deposit が再収束), root4 f862cb52… 同 | ✅ **再収束** |
| validate 2nd deposit | nil (通過 — fix (a) でも通過する) | nil | ✅ |
| 2dep 後 order rest qty=1 | applied, root 20425bb7… | 同 | ✅ (root 一致) |
| 2dep 後 cross fill (qty=1) | applied, root a1da6c4b… | 同 | ✅ (root 一致) |
| 2dep 後 withdraw 1 | coll=18014398509481981, root 577e27d0… | coll=18014398509481980, root 5603d419… | ❌ **発散** |
| 4th deposit onto 3×i53-max | applied — coll4=36028797018963964, root4 f862cb52… | applied — coll4=36028797018963964, root4 f862cb52… | ✅ **再収束** (rounded coll3 + deposit が 2^56−4 に戻る) |

## Verdict

**Confirmed (mechanism corrected).** fix (a) (per-tx `:bad-amount` i53 cap) は不十分 — 予測どおり。ただし失敗モードは仮説の「halt」では**なかった**:

1. **halt しない**。collateral が 2×/3× i53-max に抜けても `cl/deposit` (:726 無検査) は無音で credit し、測定した下流消費 site (order place, cross fill qty=1, withdraw の free-collateral, 追加 deposit) の**どれも fx/check を発火させなかった**。falsify-6/7 型の validate 通過後 throw は起きない。
2. **代わりに cross-runtime 状態分岐 (consensus break)**。JVM は BigInt で正確な 3×i53-max=…973 を保持するが、nbb (JS double) は 54-bit 奇数を丸めて …972。同一署名済み tx 列で **state-root が 3 deposit 目から不一致** (2^53−1 超かつ 2^53 奇数境界を跨ぐ演算で発生; 2dep 状態 = 2^54−2 は double で正確なため order/cross root は一致)。throw 皆無のまま validator 間で合意不能 — halt より悪い (検知も中断もない)。
3. 2×i53-max 状態からの withdraw も同様に発散 (2^54−3, 54-bit 奇数)。**発散の規則は「非可逆値 (≥2^54 の奇数など) が state に滞在している間は root が不一致、以後の演算がたまたま可逆値に着地すると再収束」** — 4th deposit (coll4=4×i53-max=2^56−4, 偶数可逆) で nbb の丸め済み coll3 から加算しても JVM と同値に戻り root4 は再一致した。再収束はこの演算列の偶然であり保証されない (nbb の丸め値が以後の演算に流用される)。要するに「i53 域外の値が state に入った瞬間から root の可逆性は壊れ、修復は演算次第の運任せ」。

## Fix への含意 (NEXT への反映)

- fix (a) は**balance-domain gate を必須化**: `collateral + amount > i53-max` の deposit を拒否 (:bad-amount)。falsify-8 実測により要件確定 (notrun 時の条件付き記述を解消)。
- gate は cl/deposit 内 (fx/check :balance or validate 層の state 依存検査) でもよいが、**fx/check で throw する実装にすると falsify-6/7 と同じ apply-block 内 halt を作る** — 拒否は validate 層 (:bad-amount) か no-op (cl/withdraw 流) であること。
- 未測定の残り site (funding, liquidation, fee pooling, :deficit 累算 clearing.cljc:711) も同じ i53 累算クラス — falsify-9 候補。

## 過程の教訓

仮説の予測 (「次の fx/check で halt」) は外れたが、実測はより深刻な欠陥 (無音の consensus 分岐) を出した。予測と実測を分けて記録するこの反証様式が効いた例。
