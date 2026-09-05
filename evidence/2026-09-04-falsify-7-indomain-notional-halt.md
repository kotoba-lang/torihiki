# falsify-7: chain-halt は i53 域外値を必要としない — in-domain な qty 同士の fill notional 溢れで apply-block が throw (両 runtime 実測)。:withdraw は halt しない (no-op 実測)

日時: 2026-09-04 01:55–02:07 JST / コード変更なし (1 反復 = 1 hypothesis, 1 measured verdict)
load gate: 反復開始 (1:54) 15-min = 16.33 < 20 → 実行可。全実測 (01:55–02:05) は load 12–16 の gate 内に完了。
実測完了後 (2:07) 23.47 / 17.85 / 16.18 — 追加実測はこの時点で停止。

## Hypothesis (H7)

falsify-6 の verdict は halt 経路を `:deposit` (amount ≥ 2^53) で実測したが、
(1) OPEN 赤 の「:deposit / :withdraw / :order が i53 上限検査なし」という
表現は **:withdraw も同様に halt する** ことを含意するが未測定、
(2) そもそも halt に **域外の入力値** が必要かも未測定 —
`fx/notional` (fixed.cljc:120 `(check :notional (* price size))`) は
apply-tx :order の fill reduce (state.cljc:490 付近) の中で走るため、
**qty = i53-max (域内) の注文 2 枚が level ≥ 2 で cross すれば
notional = 2 × i53-max > i53-max で throw する**はず。
→ H7: 「:withdraw は :deposit 同様に halt する / かつ in-domain 入力だけでも
chain-halt できる (validate に :bad-amount を足す fix では塞げない経路)」。

## 方法 (リポジトリ内コード変更なし — 追加は evidence/ の 3 ファイル)

- 共有 harness `evidence/falsify7-halt-paths.cljc` (try/catch なし — falsify-6 と同型)
- JVM driver `evidence/falsify7-jvm-driver.clj` (`catch Exception`)
- nbb driver `evidence/falsify7-driver.cljs` (`catch :default`, pre-require + load-string)
- 実行:
  `clojure -M -e '(load-file "evidence/falsify7-halt-paths.cljc") (load-file "evidence/falsify7-jvm-driver.clj")'` → falsify7-jvm.{out,err} (exit=0)
  `nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/falsify7-driver.cljs` → falsify7-nbb.{out,err} (exit=0)
- 測定セル: (a) :withdraw 2^53 (seeded なし / i53-max 入金済み) ×2、
  (b) :order qty 2^53 flags=0、(c) :order qty 2^53 flags=4 (reduce-only)、
  (d) in-domain cross: block2 で account 1 が sell qty=i53-max @level 2 を rest、
  block3 で account 2 が buy qty=i53-max @level 2 で take。

## 実測 (verdict) — 両 runtime 完全一致 (diff は JVM の load-file 戻り値行のみ)

| セル | validate | 結果 (JVM == nbb) |
|---|---|---|
| withdraw 2^53 (残高 0) | nil (通過) | **applied — no-op** (collateral=0 不変, claim なし, root=4287…f176) |
| withdraw 2^53 (i53-max 入金済) | nil (通過) | **applied — no-op** (collateral=9007199254740991 不変, claims={}) |
| order qty 2^53 flags=0 | nil (通過) | **threw** where=**:place-qty** (book.cljc:500) |
| order qty 2^53 flags=4 (reduce-only) | nil (通過) | **applied — no-op** (cl/reducing-qty が flat 口座で 0 にクランプ, root=空 exchange と同一 4287…f176) |
| in-domain cross (qty=i53-max ×2, level 2) | 両方 nil (通過) | block2 applied (root=90bf…e7c0) → block3 **threw** where=**:notional** value=18014398509481982 (= 2 × i53-max) |

- **(1) は否定 (REFUTED)**: `cl/withdraw` (clearing.cljc:729–737) は fx/check を
  持たず free-collateral で gate するため、域外 amount は no-op に落ちる
  (collateral ≤ i53-max なので `(<= 2^53 free-coll)` は常に false)。
  OPEN 赤 の「:withdraw も halt」含意は誤り — validate の i53 検査欠落は
  :withdraw では**害なし** (hygiene としての追加は任意)。
- **(2) は生存 (CONFIRMED, falsify-6 より深い)**: **完全に in-domain で
  validate を通過する 2 tx で chain-halt する。** qty はどちらも i53-max なので、
  計画中の fix「validate に :bad-amount (i53 上限) を追加」**だけでは塞げない**。
  fill 時の `fx/notional` (level × qty) が throw し、state.cljc:1118 の
  「never applied and never thrown from」契約を in-domain 入力で破る。
- さらに book は mutate-in-place のため、`bk/place!` が **maker 側の queue を
  consume した後** に throw する (falsify7-diag: block3 throw 時の book は
  fill 済み状態で進行) — throw した block の適用は全か無かですらない。
- reduce-only (flags=4) は偶然の盾: flat 口座では qty 0 にクランプされ no-op。
  ポジション保持者の reduce-only 超過分は open 側に回るため、盾にはならない
  (未測定 — 次の仮説候補)。

## Verdict / スコア判定

- 反証: H7 は **半分生存** — 「:withdraw も halt」は反証されたが、
  「in-domain 入力のみで chain-halt 可能・validate の :bad-amount では不十分」
  を両 runtime で実測。halt 経路の公式な深度が falsify-6 より一段上がった。
- OPEN 赤 **validate-i53-halt** を更新 (status/maturity.md):
  - :withdraw は halt 対象から除外 (no-op 実測)。
  - fix 条件を拡張: validate 層に (a) :deposit amount / :order qty の i53 上限
    (:bad-amount) **に加えて** (b) :order の notional 上限
    (`level × qty ≤ i53-max` — 事実上の max-notional / 初証拠金制約) が必要。
    (b) がないと in-domain 2 tx halt が残る。
- スコア: 反証 3 / テスト 3 / 再現性 2 いずれも変化なし (NEXT の既定順序不変 —
  validate-i53-halt fix が (a)+(b) 両方を要する点のみ更新)。

## 過程の教訓 (driver bug — 初回の cell (d) は無効測定だった)

初回 JVM 実行で in-domain cross が「applied」と出たが、driver が take-tx を
**fresh exchange** に適用しており rest 注文が存在しない本質無測定だった
(harness の probe が ex1 を返さない構造のせい)。diag 実行 (evidence/falsify7-diag-jvm.out,
apply-block 直列 + book/position 計数) で throw を確認し、harness に
:block2-ex を持たせて再実測 → 上表。**「cross するはずの注文が applied」は
fill 窓 (event-count vs fills ring) の疑念を生むが、原因は driver 側だった** —
falsify-5 の ring offset とは無関係。

## Files

- evidence/falsify7-halt-paths.cljc (共有 harness, try/catch なし)
- evidence/falsify7-jvm-driver.clj / falsify7-driver.cljs (drivers)
- evidence/falsify7-jvm.{out,err} / falsify7-nbb.{out,err} (両 runtime, exit=0)
- evidence/falsify7-diag-jvm.out / falsify7-diag.err (fill 窓 diag, throw 確認)
