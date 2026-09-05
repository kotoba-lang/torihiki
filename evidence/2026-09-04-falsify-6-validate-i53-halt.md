# falsify-6: validate-passing 2^53 超の deposit が apply-block を throw で halt させる — 生存 (liveness 契約違反を両 runtime で実測)

日時: 2026-09-04 00:48–01:01 JST / コード変更なし (1 反復 = 1 hypothesis, 1 measured verdict)
load gate: 反復開始 (0:54) 15-min = 16.54 < 20 → 実行可。実測完了後 (1:01) 41.12 / 32.35 / 23.96
(実測中に load が上昇し終了時点で 15-min > 20 — 全実測は gate 内に完了済み。追加実測はこの時点で停止)。

## Hypothesis (H6)

`api/validate` の :deposit / :withdraw / :order は金額・数量を
`(integer? …) (pos? …)` のみで検査し i53 上限がない (api.cljc:202, 245, 68)。
一方 README:397 は「All values are integers in the i53 domain, **checked on
the way into storage**」、api.cljc ns docstring と state.cljc:1118 は
「validation is total … **nothing that throws**」を契約とする。
→ **validate を通過した amount ≥ 2^53 の tx は `fx/check` (clearing.cljc:721
`(fx/check :deposit amount)`) で apply-block 内で throw し、
「誰でも 1 tx で chain を halt できる」liveness 違反になる**。

## 方法 (リポジトリ内コード変更なし — 追加は evidence/ の 3 ファイルのみ)

- 共有 harness `evidence/falsify6-i53.cljc` (try/catch を含まない — 下記罠参照)
- JVM driver `evidence/falsify6-jvm-driver.clj` (`catch Exception`)
- nbb driver `evidence/falsify6-driver.cljs` (`catch :default`, pre-require +
  load-string — falsify-4 と同型)
- 実行: `clojure -M -e '(load-file "evidence/falsify6-i53.cljc") (load-file "evidence/falsify6-jvm-driver.clj")'`
  → evidence/falsify6-jvm.{out,err} (exit=0)
  `nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/falsify6-driver.cljs`
  → evidence/falsify6-nbb.{out,err} (exit=0)
- 測定対象: amount ∈ {i53-max=9007199254740991, 2^53=9007199254740992,
  2^53+1=(+ 9007199254740992 1)} を :deposit :account 1 で validate → apply-block。

## 実測 (verdict)

| amount | in-domain | validate (JVM==nbb) | 結果 (両 runtime) |
|---|---|---|---|
| 9007199254740991 | true | nil (通過) | **applied** — collateral=9007199254740991, state-root 両 runtime 完全一致 |
| 9007199254740992 (2^53) | false | **nil (通過)** | **threw** ex-info `torihiki.fixed: value escaped the i53 domain` where=:deposit |
| (+ 2^53 1) | false | **nil (通過)** | **threw** 同上 |

- **H6 は生存 (欠陥実在)。** `fx/in-domain?` が false を返す値を validate は
  平気で通し (validate=nil)、直後の apply-block が throw する。
  state.cljc:1118 の契約「Every transaction … is skipped and recorded in
  `:rejected`, **never applied and never thrown from**」が破られる:
  署名済みアカウントは amount 2^53 の deposit 1 tx で全 validator を
  同一位置で停止させられる (typo でも攻撃でもよい)。
- i53-max 自体は applied で正常 — 境界そのものは健全。欠陥は
  「i53 域外を validate が拒否しない」点のみ。
- JVM==nbb の比較: `diff falsify6-jvm.out falsify6-nbb.out` の差分は
  (1) JVM のみ `#'falsify6-i53/header` 行 (load-file の戻り値, ノイズ) と
  (2) 2^53+1 行の value のみ (下記読み込み時丸め)。applied 行の
  state-root は両 runtime byte-identical — 既存の cross-runtime 一致に抵触なし。

## 副次知見 (読み込み時丸め — 同一ソース式でも value が runtime 間で分裂)

`(+ 9007199254740992 1)` は JVM で 9007199254740993、nbb で 9007199254740992
に評価される (out の 4 行目比較)。今回の結末は「両方 throw で同一」だが、
これが throw で止まるのは `fx/check` が 2^53 も拒むからで、
**域外整数がどこかの経路で丸め受容された瞬間に JVM (long[], exact) と
nbb (Float64Array, 丸め) の state root が分裂する**。fix は validate 層で
i53 上限 (`:bad-amount`) を拒否するのが唯一の正しい位置 — storage 層の
throw は halt にしかならない。

## 新規 nbb trap (falsify-4 の 3 罠に続く 4 つ目)

nbb の `load-string` は **reader conditional を解決しない**
(`(load-string "#?(:clj 1 :cljs 2)")` → nil, 2026-09-04 実測)。
`catch #?(:clj Exception :cljs :default)` は load-string 経由では
「Unable to resolve symbol: e」で落ちる (evidence/falsify6-nbb.err 初回分)。
回避: 共有 .cljc から try/catch を追放し、driver 側 (ファイル読み) が
catch を持ち、出力整形を共有 `line` / `line-threw` に寄せる構成。
suite 常設化 (NEXT 2) の driver にも同じ制約が適用される。

## Verdict / スコア判定

- 反証: H6 **survived** — validate の total-application 契約に対する
  実欠陥 (halt-by-single-tx) を両 runtime で実測。反証軸 3 は維持
  (score 条件不変。むしろ本実測は反証ループが機能したことの実例)。
- OPEN 赤 に **validate-i53-halt** を新規登録 (status/maturity.md):
  fix は api/validate に amount/qty の i53 上限検査 (:bad-amount) を追加。
  コード変更のため本 iteration では未実施。
- 再現性 2 / テスト 3 / その他の軸: 変化なし (NEXT の既存 2 件が優先)。
- 静的補助観測 (未測定, 次の仮説候補): :withdraw (api.cljc:245) と
  :order qty (api.cljc:68) も同一の integer?/pos? のみ検査 — 同経路の
  i53 域外値が同様に throw するかは未確認。

## Files

- evidence/falsify6-i53.cljc (共有 harness, try/catch なし)
- evidence/falsify6-jvm-driver.clj / falsify6-driver.cljs (drivers)
- evidence/falsify6-jvm.{out,err} (JVM, exit=0)
- evidence/falsify6-nbb.{out,err} (nbb, exit=0)
