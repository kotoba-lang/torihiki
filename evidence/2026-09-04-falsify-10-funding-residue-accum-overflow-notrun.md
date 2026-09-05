# falsify-10 (仮説・実行前登録) — funding 累算 `:funding-residue` / `:fees-collected` の 2^53 crossing

- date: 2026-09-04 (JST, 実行前登録)
- 仮説: maturity.md NEXT の「falsify-10 候補: funding / fee pooling / fees-collected は falsify-9 と同クラスだが mul-rate 積上限のため 2^53 crossing が ~653k cross — 小規模 crossing (奇数 ≥2^53 を通る rate/qty 組み合わせ探索) か funding 累算の直接 probe で実測」

## 測定対象 (コード根拠)

- `fnd/apply-funding` (funding.cljc:130–140): 支払い `p = fx/mul-rate (fx/notional oracle size) rate` は **fx/check 済み** (in-domain)。だが `(update :funding-residue (fnil + 0) p)` と `(update-in [:accounts acct :collateral] (fnil - 0) p)` は**累算 sum 無検査** — falsify-8 (collateral) / falsify-9 (:deficit) と同一クラス (delta 検査済み / sum 無検査)。
- `:funding-residue` は state root に入る (state.cljc:1603 `encode-clearing-totals`) — 累算が非可逆値になれば root が直接分岐する。

## スケール分析 (mul-rate 積上限との整合)

1 settle あたりの p は falsify-9 の積上限 `(* amount rate) < 2^63` (JVM long overflow 回避) で界隈付けされる:
- empty-accumulator の実レート = 12500 (interest 1bp/8h → hourly fdiv 1e5 8 = 12500, cap 4e7 に達しない実運用値)
- nbb で p を両 runtime 同一にするには積が double 可逆であることが安全条件。積 < 2^53 なら p < 9.007e6 → crossing に ~1M settle (falsify-9 が「非現実的」と判定したスケール)
- **本研究の小規模化**: 積が 2^53〜2^63 の間でも、積が 1e9 の倍数から十分離れていれば (誤差 ≤1024 « 1e9/2) floor 商は両 runtime 一致する。notional = 80000×p + 40000 (p = 9000000001, odd) とすると積 = 1e9×p + 5e8 (境界距離 5e8) → p は JVM/nbb で同一。**p_max ≈ 9.2e9 → crossing に ~979k settle**。これでも nbb で 1M apply-block ループは未実測スケール。

## 予測される walk (probe A: residue を直下に synthetic-seed, falsify-9 と同方式)

- seed: `:funding-residue = R0 = 2^53 + 1 − 2p = 8989199254740991` (奇数, < 2^53 = double 可逆 — 実運用の ~979k 時間の累算が残す形の時間圧縮), account 7 long position size = 720000000120000, oracle = 1, collateral = i53-max
- settle 1: R1 = 8998199254740992 (even, < 2^53) — 両 runtime 一致予測
- settle 2: R2 = **9007199254740993 = 2^53 + 1 (奇数, ≥ 2^53 = double 非可逆)** — JVM …993 / nbb …992 (丸め) → **state-root 分岐予測, throw 皆無**
- settle 3/4: R3 = R2 + p は even 可逆に着地し再収束→次の奇数で再分岐 (falsify-8 の発散則 (i)(ii) の複製予測)

## probe B (JVM のみの honest walk)

residue 0 から実レートで 1,000,801 settle (JVM のみ, nbb は 1M apply-block ループを未実測スケールとして skip)。予測: residue(1000800) = 9007200001000800 (even, 可逆), residue(1000801) = **9007209001000801 (奇数 → nbb なら非可逆)**, throw 皆無 → seeding なしでも in-domain 入力だけで crossing に到達することの実証。

## 予測される verdict

funding 累算 `:funding-residue` は delta (p) が fx/check :mul-rate を通っても sum が無検査のため、in-domain な funding-settle のみで 2^53 を越え、falsify-8/9 と同一の無音 cross-runtime root 分岐を起こす。fix は falsify-8/9 の累算 sum gate パッケージに `:funding-residue` (と `:fees-collected` 同型 site) を加えること。証跡は evidence/falsify10-{jvm,nbb}.{out,err}。
