# falsify-9 — delta-checked / sum-unchecked accumulator: :deficit (clearing.cljc:711) crosses 2^53 silently

- date: 2026-09-04 (JST, host load 17.70/18.29/16.99 — under gate, measured)
- hypothesis: evidence/2026-09-04-falsify-9-fees-collected-accum-overflow-notrun.md (registered pre-run)。**仮説の測定対象は実行中に修正された** (下記「過程」)。
- runtimes: JVM `clojure -M -e '(load-file "evidence/falsify9-accum-paths.cljk") (load-file "evidence/falsify9-jvm-driver.cljk")'` → falsify9-jvm.{out,err} (exit=0); nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" evidence/falsify9-driver.cljk` → falsify9-nbb.{out,err} (exit=0)
- method: `cl/settle-deficit` (clearing.cljc:706–712) は production では liquidation waterfall (liquidation.cljc:252) 経由でのみ到達可能。probe は清算損失が残すのと同じ形 (`collateral = −i53-max`) を直接 seed し、**実物の** `cl/settle-deficit` を実 exchange state に呼ぶ (accumulator 自体は無修正)。synthetic-seeded と明記。

## Measured (both runtimes, throw 皆無)

| step | JVM deficit / root | nbb deficit / root | 一致? |
|---|---|---|---|
| settle 1 (delta i53-max, fx/check :deficit 通過) | 9007199254740991 / 4f8a8b52… | 同 | ✅ |
| settle 2 (累算) | 18014398509481982 (=2×i53-max, even, [2^53,2^54) は even grid で可逆) / 82e06c6e… | 同 | ✅ |
| settle 3 | **27021597764222973** (奇数, ≥2^54 = double 非可逆) / root **af4c278d…** | **27021597764222972** (丸め) / root **58b49391…** | ❌ **発散** |
| settle 4 | 36028797018963964 / 664c03dc… | 36028797018963964 / 664c03dc… | ✅ 再収束 (falsify-8 と同じ偶然: nbb 丸め値 …972 + i53-max が 4×i53-max (even 可逆) に着地) |
| 3x 後 deposit 1 (pay-down) | deficit …972 (偶数に着地) / root 58b49391… | deficit …972 / root 58b49391… | ✅ **post-deposit で再収束** (1 unit の返済が JVM 側を even grid に戻した) |

## Verdict

**Confirmed.** `:deficit` は falsify-8 の collateral と同一クラス: 各 delta は `fx/check :deficit` を通るが、累算 `(fnil + 0)` (clearing.cljc:711) は sum 無検査。in-domain な deficit event 3 回 (各 ≤ i53-max) で deficit = 27021597764222973 (奇数 ≥2^54) に到達し、**throw 皆無のまま同一演算列で JVM/nbb の state-root が不一致** — 署名済み tx 列で validator 間合意不能 (halt 型より検知困難な無音 consensus break)。発散規則も falsify-8 の実測則を複製: (i) 非可逆値滞在中は root 不一致、(ii) 以後の演算が even 可逆値に着地すると再収束するが保証されない。

## 副次発見 (同一 harness で実測)

- **fx/mul-rate (fixed.cljc:99–103) は check 前に積を計算する**: `(* i53-max rate-scale)` = 9.007e24 は i64 を溢れ、**JVM は unchecked ArithmeticException "long overflow"** (where/value なし) を fee 経路の途中で throw → falsify-6/7 型の apply-block 内 halt。**nbb は double で丸めた積を /1e9 し value=9007199254740991 を無音で返す** (fx/check 通過)。同一呼び出しで両 runtime が異なる失敗モード (halt vs 無音通過)。fix 要件: mul-rate は積が < 2^63 に収まる amount×rate 界限を check 前に置くか、積 overflow を両 runtime 同一挙動にする設計が必須。
- **fees-collected (clearing.cljc:410) の 2^53 crossing は本反証では実測不能と判定**: mul-rate の積 < 2^63 制約により 1 fill の fee は ~9.2e9 が上限で、fees-collected を 2^53 越えの奇数に到達させるには ~653k cross (nbb で apply-block ループは非現実的スケール)。クラスとしては :deficit 実測と同一 (delta 検査済み / sum 無検査) だが、**未実測のまま**。funding / fee pooling も同様。

## Fix への含意 (NEXT への反映)

- falsify-8 の fix パッケージに **累算 site の sum 検査** を追加: `:deficit` 累算は `collateral + amount > i53-max` の balance-domain gate と同型に、累算後の値が i53 domain を出る seek を validate 層か no-op で拒否すること (fx/check throw による gate は falsify-6/7/9 の apply-block halt を作る — deficit の場合は delta 側は既に fx/check 済みなので、**sum 側** を no-op clamp するか、清算側で累算前に cap)。
- mul-rate の pre-check 積 overflow は独立赤 (JVM halt / nbb 無音通過の非対称)。amount×rate の事前界限 or 両 runtime 同一の overflow 挙動が必要。
- 未実測: fees-collected / funding / fee pooling (同クラス, スケール理由で未計測)。

## 過程の教訓

notrun で登録した仮説は fees-collected 対象だったが、第一回 JVM 実測で mul-rate の pre-check 積 overflow (fee=100% 設計が不可能) が判明し、測定対象を NEXT が指名する :deficit (:711) に修正した。仮説ファイルと報告の両方に修正経緯を残す。probe 実装では `cl/settle-deficit` / `cl/deposit` が exchange ではなく **clearing map** を取ることに一度気づかず no-op を測定しかけた (liquidation.cljc:252 の呼び形 `(:state r)` を確認して修正)。
