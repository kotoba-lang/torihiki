# falsify-11 (hypothesis, pre-run) — `:fees-collected` 累算 (clearing.cljc:410/560) は sum 無検査で 2^53 を越え、無音 cross-runtime root 分岐を起こす

- date: 2026-09-04 (JST)
- 登録時 host load: 1-min 34.28 → 120s/240s 待機で 14.72 に低下 (< 20 gate 通過後に実測へ)
- 仮説: `cl/apply-fill` (clearing.cljc:410, `(update :fees-collected (fnil + 0) (- fee share))`) と `cl/spot-fill` (:560, 同型) は、各 fill の fee は `fx/mul-rate` の fx/check を通る (in-domain) が **累算 sum は無検査** — falsify-8 (collateral) / falsify-9 (:deficit) / falsify-10 (:funding-residue) と同一クラスの第 4 例。`:fees-collected` は state root に入る (state.cljc:1602 encode-clearing-totals) ので、非可逆値 (奇数 ≥ 2^53) に着地した瞬間、throw 皆無のまま JVM/nbb の root が分岐するはず。

## 境界距離 trick の fee 経路への適用可否 (NEXT が「まず検証」とした点)

fill 1 回の fee = `fx/mul-rate(|level×qty|, taker-fee-rate)` = floor(N×R/1e9)。falsify-10 と同一の構成を流用:

- N = level×qty = 30000000005 × 24000000 = **720000000120000** (< 2^53, fx/notional 域内)
- R = 12500 (falsify-10 と同じ実運用スケールの rate; market spec `:taker-fee-rate 12500`, `:maker-fee-rate 0`)
- 積 P = N×R = **9000000001500000000** (< 2^63 — falsify-9 の JVM long overflow 回避条件)
- 境界距離 = P mod 1e9 = **500000000** (5e8 » double 誤差 ≤1024) → fee は両 runtime bit-identical のはず
- fee = floor(P/1e9) = **9000000001 (奇数)** — falsify-10 の p と同一値

予測: probe A (payment-identity) で JVM/nbb とも fee = 9000000001, boundary-dist 5e8 (nbb は丸めで ±数百ずれるが商は不変)。

## Seeded crossing probe の予測

- seed: `:fees-collected = F0 = 2^53+1 − fee = 9007190254740992` (even, < 2^53, double 可逆 — 時間圧縮のみで accumulator と apply 経路は無修正の production code, falsify-9/10 流 synthetic-seed)。maker 側 rate 0 なので 1 cross = taker fill 1 回につき fees-collected は +fee (奇数) ずつ増える。
- cross 1: F1 = **9007199254740993 = 2^53+1 (奇数, 非可逆)** → JVM は ...993, nbb は ...992 に丸め → **root 分岐, throw 皆無** のはず
- cross 2–4: 奇数 fee の累算で parity が保存され、falsify-10 と同様に丸め残差 ±2 が持続 → 分岐持続のはず
- collateral は全程 i53 域内 (各 cross で taker が −fee されるだけ, realized PnL は同一 level なので 0) → 分岐は `:fees-collected` のみが運ぶはず

## 実測予定

- probe A (両 runtime): payment-identity (N, P, boundary-dist, fee)
- probe B (両 runtime): fees-seeded-crossing — 実物 `st/apply-block` の `:order` tx で maker (account 1 bid) → taker (account 2 sell) の cross を 4 回, 各 cross 後に {fees-collected, coll1, coll2, root} を snapshot
- probe C (JVM のみ): honest-walk — fees-collected 0 から seed なしで実 cross を ~1,000,801 回 (fee ≈ 9e9 なので ~1e6 cross で 2^53 crossing) → seed なしでも到達することの実証

verdict は実測後に evidence/2026-09-04-falsify-11-fees-collected-accum-overflow.md へ。
