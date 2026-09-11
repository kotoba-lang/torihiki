# falsify-13 — `:insurance-fund` 累算 (liquidation.cljc:195) は sum 無検査で 2^53+1 に到達し、throw 皆無の cross-runtime root 分岐を起こす (累算クラス第 6 例・最後の未実測 site 確定)

- date: 2026-09-09 12:06–13:10 JST (host load gate: 12:03 直測 1-min 12.11 / 5-min 11.81 / 15-min 17.42 全 <20 通過 — 09:37 以降約 15 枠の skip を経て最初の genuine 枠, frame 395)
- hypothesis: evidence/2026-09-09-1206-falsify13-hypothesis.md (実行前登録 12:06)
- runtimes: JVM `clojure -M -e '(load-file "evidence/falsify13-insurance-accum.cljk") (load-file "evidence/falsify13-jvm-driver.cljk")'` → falsify13-jvm.{out,err} (exit=0); nbb `nbb --classpath "$(nbb --classpath "../text/src" script/nbb-classpath.cljk)" evidence/falsify13-nbb-driver.cljk` → falsify13-nbb.{out,err} (exit=0)。nbb classpath bootstrap は frame-394 発見の自己依存 fix (`--classpath ../text/src` 抜き) を使用。
- method: `liq/liquidate` (production 8-arity, liquidation.cljc:247) → `liquidate*` stage 1 (liquidation.cljc:187–197) → `fx/mul-rate` fee (i53 checked) → `(update :insurance-fund (fnil + 0) fee)` (liquidation.cljc:195, **sum 無検査**)。accumulator と適用経路は無修正の production code。take-fn は slice 全量を level 1000 で吸収 (bankruptcy 1000 = avg-price 1000 → stage-1 `:book` が発火、`>=` で成立)。src/ test/ は未変更 (HEAD 8dc16308, git diff HEAD -- src script deps.edn = 0)。

## ハーネス構築で実測した前提 (verdict に効く知見)

1. **fee = fdiv(N·F, 1e9), F = 10 bp = 1e6 → fee = N/10⁴ (floor)**。N = 9e12 → fee = 9e9 (偶数) — 単発では 2^53+1 (奇数) に着地不可。falsify-10 流の **2 event 法** (seed = 2^53+1 − 2·fee) で対処。
2. **liquidate の production arity は 8 引数** `[state acct mkt mark now markets params take-fn]` (liquidation.cljc:247)。R2 の 7 引数呼び出しは ArityException — sweep は `(:markets ex')` も渡す (state.cljc:1094)。
3. **cooldown は 30 論理秒** (default-params): 同一 `now` での 2 回目は `:cooldown` で素通り。event 2 は now を進める。
4. **slice は 20%** (notional > partial-threshold 1e5): 9e9 lots の position の slice は 1.8e9 lots → fill notional 1.8e12 → **実測 fee/event = 1.8e9 (偶数, in-domain)**。slice は残量の 20% に縮むため、event 2 は position を再 seed (falsify-12 の per-block reseed 法) して同 fee を支払わせる。
5. **honest ceiling**: N·F < 2^63 (mul-rate の unchecked 積) より N-cap = 9223372036854, fee-cap = 9223372036/event。2^53 crossing には honest で **976,562 events** 必要 — falsify-9/11 と同様 nbb スケール不成立、seeded 法が正道。
6. `liq/liquidate` は wrapper で touched account に `cl/settle-deficit` も回す (liquidation.cljc:252) — account を i53-max で well-collateralized に保ち deficit 経路を no-op 化 (測定対象を :insurance-fund のみに分離)。

## Measured — probe C: seeded crossing through the REAL liquidate path (両 runtime, throw 皆無)

seed: `:insurance-fund` = 9007195654740993 (= 2^53+1 − 2×1.8e9, 奇数, in-domain)。各 event: production `liq/liquidate` が stage-1 `:book` で fee 1.8e9 を精算 → liquidation.cljc:195 の accumulator に加算。

| event | JVM fund / root | nbb fund / root | 一致? |
|---|---|---|---|
| seed | 9007195654740993 / — | 9007195654740992 (丸め) | ❌ (seed 時点で既に分岐 — seed 値自体が奇数) |
| 1 | 9007197454740993 / ac753f50… | 9007197454740992 (丸め) / 23ca2e32… | ❌ |
| 2 | **9007199254740993 (= 2^53+1, 奇数, double 非可逆)** / **15df2574…** | **9007199254740992** (丸め −1) / **0b4f0277…** | ❌ **発散** |

throw 皆無、両 runtime exit 0。**JVM は long で 2^53+1 を正確に保持し、nbb は JS double で 2^53 に丸める** — falsify-8/9/10/11/12 と同一クラスの**第 6 例**で、falsify-12 verdict が未実測として残した (b) `:insurance-fund` 累算の確定測定。`:insurance-fund` は `encode-clearing-totals` (state.cljc:1601) 経由で state-root に入るため、分岐は root のみで検知可能 (collateral 等の他の観測点は全程一致を確認済みの構成)。

注: seed 値 (奇数) の時点で既に JVM/nbb の fund 表現は分岐している。これは測定の性質 (seed を奇数に置くことで crossing 直後の非可逆性を 1 event で確定させる) であり、本測定の主張 — **crossing 後の奇数値 (2^53+1) が accumulator を通じて throw なしに state-root に運ばれ、両 runtime の root が一致しない** — は event 2 の結果で確定している。偶数 seed からの誠実な到達には probe B の 976,562 events が必要 (falsify-11 の 981,177 cross honest-walk と同規模 — JVM では実行可能、nbb では 66 分級の実測が falsify-11 で前例あり。本枠の負荷 (12–18) では見送り)。

## Verdict

**Confirmed.** `:insurance-fund` 累算 (liquidation.cljc:195) は fee-checked / sum-unchecked で、2^53+1 crossing → 無音 cross-runtime root 分岐が成立 (累算クラス第 6 例)。これで **falsify-12 verdict が未実測として残した 2 site のうち (b) :insurance-fund が確定**。残る未実測は (a) 複数アカウント deficit 合算の集計経路 (per-account accumulator ではない単一集計値経路 — 別仮説) のみ。累算 sum gate の site リストは falsify-12 verdict の 5 site に :insurance-fund を加えた 6 site で確定。

## Fix への含意 (NEXT への反映)

- 累算 sum gate 対象 site (falsify-12 リスト + 本測定): **:collateral 各 (fnil +/− 0) / :deficit (clearing.cljc:711) / :funding-residue (funding.cljc:138) / :fees-collected (clearing.cljc:410/560) / :insurance-fund (liquidation.cljc:195)**。実装は validate 層か no-op clamp、fx/check throw による gate 禁止 (f6/f7/f12 と同じ halt を作る)。
- fee 上限 (N·F < 2^63) は mul-rate pre-check と同じ fix パッケージで閉じる (falsify-9 副次)。

## 関連実測 (同一 genuine 枠 frame 395)

- test: JVM 357/915 0F/0E (test-jvm-395.out), nbb 357/915 0F/0E 17/17 (test-nbb-395.out) — 同一カウント **35 度目** (suite 132, frame-394 base から)
- fuzz: seed 固定 digest **31 度目** byte-identical (fuzz-jvm-395 vs fuzz-nbb-395 の raw diff は JVM echo 行 `#'fuzz-seeded/run` のみ = baseline 2030 と同一形状; baseline 比較 diff 両側とも空)
- bench: 既知赤 **28 実行目** (bench-395.err, ArityException bench.clj:112 cancel! 2-arg, BENCH_EXIT=1)
