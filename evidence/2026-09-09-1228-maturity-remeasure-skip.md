# torihiki maturity remeasure — 2026-09-09 12:28 枠 (frame 396) load gate skip

- cron, no code changes.
- 負荷: pre-run 直測 12:28 uptime 1-min **36.35** / 5-min **59.05** / 15-min **49.22** — 全 window ≥20 で「全 <20」不成立のため test/parity/bench/falsify 新規実測は not-run (高負荷続行)。
- terminal backend 空出力 fault (既知高負荷型, frame 387–389 と同型): uptime/git/torihiki_state.sh 直叩きが複数回 exit 0 かつ stdout 空 — HEAD/src diff の直接検収は not-run。
- コード不変: pre-run script の IN-FLIGHT は `M status/maturity.md` + untracked 診断スクリプト (_diag*.py / _ins*.py) のみで src/ script/ deps.edn 変更なし → frame-395 (falsify-13 枠, suite 132, test-jvm/test-nbb-395) の引用契約で踏襲。
- 正本引用: frame-394/395 実測 base (e81a243 逸脱確定後の移行済み) — suite **132** (35 度目両 runtime 同一カウント 357/915), fuzz digest 31 度目 byte-identical, bench 既知赤 bench-tape-cancel-arity 28 実行目。
- OPEN 赤 7 件 + bench 常設 1 件のまま, 引用行 6 site は frame 384/386 実読検収 + frame-394 以降の実測で有効。NEXT 未実測 0 件 (fix 未着手), 発覚 0 件, 新規 hypothesis なし。
- スコア 7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 13 件確定, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ (i53/notional/balance-domain gates + 累算 sum gates 6 site + fx/mul-rate pre-check + REDUCING 積 pre-limit + rate 上限) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix (frame-394 発見, `--classpath "../text/src"` 必須)。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **133** / parity **39** / bench **92** / bench 既知赤 29 実行目想定)。
- 次枠番号 = **397**。
