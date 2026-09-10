# torihiki maturity remeasure — 2026-09-09 12:41 枠 (frame 397) load gate skip

- cron, no code changes.
- 負荷: pre-run 12:40 uptime 1-min **16.79** / 5-min **28.69** / 15-min **37.07** — 1-min は初めて <20 に下振れしたが 5-min/15-min がまだ ≥20 (高負荷継続の慣行: 直近 実測 を canonical とし負荷 window 全体が <20 になるまで新規実測は not-run)。test/parity/bench/falsify 新規実測は not-run。
- terminal backend 空出力 fault (既知高負荷型, frame 387–389 と同型) は本枠も再発: date/uptime/ls 直叩きが exit 0 かつ stdout 空 — /tmp 経由の file 読み取りで代替検収 (12:40 date/uptime/state 出力とも取得済み)。
- コード不変: pre-run script の IN-FLIGHT は `M status/maturity.md` + untracked 診断スクリプト (_diag*.py / _ins*.py) のみで src/ script/ deps.edn 変更なし → frame-395 (falsify-13 枠, suite 132, test-jvm/test-nbb-395) の引用契約で踏襲。
- 正本引用: frame-394/395 実測 base (e81a243 逸脱確定後の移行済み) — suite **132** (35 度目両 runtime 同一カウント 357/915), fuzz digest 31 度目 byte-identical, bench 既知赤 bench-tape-cancel-arity 28 実行目。
- OPEN 赤 7 件 + bench 常設 1 件のまま, 発覚 0 件, 新規 hypothesis なし (1 hypothesis 要件は負荷 gate により not-run)。
- スコア 7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 13 件確定, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ (i53/notional/balance-domain gates + 累算 sum gates 6 site + fx/mul-rate pre-check + REDUCING 積 pre-limit + rate 上限) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix (frame-394 発見)。
- 次ランナー: 負荷 全 window <20 突入後最初の枠で全 gate 再実測 (新規実測時 suite **133** / parity **39** / bench **92** / bench 既知赤 29 実行目想定)。1-min が下振れ開始したため 5-min の <20 到達が次の目安。
- 次枠番号 = **398**。
