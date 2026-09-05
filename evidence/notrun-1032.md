# maturity remeasure not-run — 2026-09-04 10:32 JST

- 判定: **not-run** (host load > 20 gate)
- load averages 実測:
  - 10:27 (torihiki_state.sh) `25.44 20.59 21.34`
  - 10:30 `32.92 25.54 23.22`
  - 10:32 `21.87 24.32 23.08` — 3 つすべて継続 > 20
- 09:54/09:58 に続く同日 3 回目の not-run。falsify / test / fuzz / bench 計測はいずれも未実施。コード変更なし
- 次回 (高負荷解消後) の対象: maturity.md NEXT の validate-i53-halt fix 検証 — 累算 sum gate 対象 site (:deficit clearing.cljc:711 / :funding-residue funding.cljc:138 / :fees-collected clearing.cljc:410/560)、REDUCING 分岐 `(* entry-notional closed)` 事前境界 (clearing.cljc:157)、balance-domain gate `collateral + amount > i53-max`、bench-tape-cancel-arity (bench.clj:112)
- 直近の確定計測 (変更なし): falsify-11 fees-collected-accum-overflow (0745), fuzz digest byte-identical 28 度目 (0841/0842), 両 runtime 357 tests / 915 assertions (0839/0840), bench-tape-cancel-arity 24 実行目 (0843)
