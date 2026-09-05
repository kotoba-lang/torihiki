# falsify-9 (notrun → 実行中に修正) — unmeasured i53 accumulation site

- date: 2026-09-04 (JST)
- hypothesis (NEXT の falsify-9 候補のうち 1 site を実測): `cl/apply-fill` (clearing.cljc:410) は fill ごとに `:fees-collected` を `(fnil + 0)` で累算する。各 fill の fee は `fx/mul-rate` の `fx/check` で in-domain に保たれるが、**合計は無検査** — `settle-deficit` (:711, delta に fx/check あり) と同型の「delta は検査済み / sum 無検査」累算。
- 予測: 手数料率 100% (:taker-fee-rate/:maker-fee-rate = rate-scale) の市場で、in-domain な notional f=9007199254740987 (3×3002399751580329, 奇数) の cross を 2 回起こすと fees-collected は f → 2f → 3f → 4f と歩く。**3f = 27021597764222861 は 2^54 以上の奇数 = JS double 非可逆** → (i) どの site も throw せず全 applied、(ii) 3 回目の累算が属する apply-block 以降、JVM と nbb の state-root が不一致 (falsify-8 と同クラスの無音 consensus 分岐)。
- probe 設計: account 1–4 に各 1×i53-max deposit (per-tx in-domain) → acct1 sell qty=3002399751580329 level 3 rest → acct2 buy (cross #1, taker+maker 分で fees += 2f) → acct3 sell rest → acct4 buy (cross #2, fees が 3f を通過)。各 block 後の fees-collected と state-root を両 runtime で記録。
- 判定: throw の有無 / 3f 時点の fees 値 / root 一致で verdict。分岐すれば falsify-8 の「balance-domain gate 必須」に **累算 site 全般の sum 検査** が加わる (fees は収入なので validate 層拒否は不可 — gate は no-op clamp か cap 付き rate か別途設計要)。
- host load: 実行時 17.70/18.29/16.99 < 20 → 実測に移行した。
- **実行中の修正**: 第一回 JVM 実測で fee=100% 設計が `fx/mul-rate` の check 前積 `(* amount rate)` の i64 overflow (unchecked ArithmeticException) で不可能と判明。1 fill の fee は積 < 2^63 制約で ~9.2e9 上限 → fees-collected の 2^53 crossing は ~653k cross 必要で nbb スケール不成立。測定対象を NEXT 指名の `:deficit` (clearing.cljc:711, delta fx/check 済み / sum 無検査, 同一クラス) に修正 → **evidence/2026-09-04-falsify-9-deficit-accum-overflow.md (Confirmed)**。mul-rate pre-check 積 overflow (JVM halt vs nbb 無音通過の非対称) を副次発見として同報告に記録。
