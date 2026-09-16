# falsify-14 hypothesis (registered pre-run, 2026-09-14 02:2x JST, frame 437)

複数アカウントの合算集計経路 (frame-407 登録, frame-433 static 確定の残り未実測 site)。

## hypothesis

`torihiki.commit` の merkle-sum root は `cm/tree (canonical-leaves ex)` の root `:sum` として
**全アカウント collateral の単一集計値**を運ぶ (state.cljc:1931 state-root, commit.cljk:127 reserves,
commit.cljk:113 shortfall の `(reduce + 0 (map :sum leaves))`)。per-account accumulator は
falsify-8 で確定済みだが、**アカウント横断の集計 sum は検査なしかつ 2^53 域を跨げる**:

- 各アカウントの collateral を **個別には double 可逆**な値 (例: 2^53+2 = 9007199254740994,
  even < 2^54) に seed する。
- 3 アカウント (4/6/8) の合算 3×(2^53+2) = 27021597764222980 は **[2^54, 2^55) で ≡2 (mod 4)**
  → JS double 非可逆 → nbb は丸め、JVM は long 正確保持。
- per-account leaf bytes (enc-ints) は両 runtime 一致するはずで、分岐は **root の :sum のみ** —
  state-root / commit/reserves が 2 値に分岐し、throw 皆無であると予測。

これが成立すれば、集計 site は per-account 累算 (6 site 確定済み) に加え
**merkle-sum root sum / commit shortfall の reduce** が fix パッケージ対象に加わる。

## method

- 両 runtime で同一 driver 構成 (falsify-13 流): 共通 harness なし, driver ごと自己完結。
- `st/new-exchange` → accounts 4/6/8 collateral を assoc-in seed → `st/state-root` と
  `torihiki.commit` 経由の reserves を出力。src/ test/ 無変更。
- 予測: JVM reserves = 27021597764222980, nbb reserves = 丸め値 (±4), root 不一致, throw 皆無。
