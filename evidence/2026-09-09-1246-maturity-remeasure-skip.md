# torihiki maturity remeasure — 2026-09-09 12:46 枠 (frame 398) load gate skip

- cron, no code changes.
- 負荷: 直測 12:46 uptime 1-min **24.48** / 5-min **23.16** / 15-min **31.28** — 全 window ≥20 (高負荷継続の慣行: 負荷 window 全体が <20 になるまで新規実測は not-run)。test/parity/bench/falsify 新規実測は not-run。
- terminal backend 空出力 fault (既知高負荷型, frame 387–389 と同型) は本枠も再発: date/uptime/ls 直叩きが exit 0 かつ stdout 空 — /tmp 経由の file 読み取りで代替検収 (12:42 state 出力, 12:46 date/uptime/git/evidence 検収とも取得済み)。
- 正本引用: frame-395 実測 base (commit 3d107f7): suite **132**, JVM+nbb 357/915 0F/0E (35 度目同一カウント, test-jvm-395 / test-nbb-395), fuzz digest 31 度目 byte-identical (fuzz-jvm-395 vs fuzz-nbb-395 = JVM echo 行のみ; vs baseline fuzz-jvm-2030 diff 空), bench 既知赤 28 実行目 (bench-395.err, bench.clj:112 cancel! 2-arg ArityException)。src diff vs HEAD = 0。
- falsify-13 引用検収済み: verdict + falsify13-{jvm,nbb}-driver + falsify13-{jvm,nbb}.out + err 0 バイトが evidence/ に常置 (12:19–12:26)。
- OPEN 赤 7 件 + bench 常設 1 件のまま, 発覚 0 件, 新規 hypothesis なし (負荷 gate により not-run)。
- スコア 7 軸すべて変更なし (**3/3/3/3/2/1/1**; 反証実施 13 件確定, 累算 overflow クラス 6 site OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ (i53/notional/balance-domain gates + 累算 sum gates 6 site [:collateral / :deficit / :funding-residue / :fees-collected / :insurance-fund + fx/mul-rate pre-check] + REDUCING 積 pre-limit + rate 上限) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix (frame-394 発見)。複数アカウント deficit 合算集計経路のみ未実測 (同一 fix パッケージで閉じる)。
- 次ランナー: 負荷 全 window <20 突入後最初の枠で全 gate 再実測 (新規実測時 suite **133** / parity **39** / bench **92** / bench 既知赤 29 実行目想定)。本枠 12:45–12:46 に他枠由来 test-396/test-nbb-396 (357/915 0F/0E 両緑) が evidence/ に出現中 — 未確定 frame として引用せず保留。
- 次枠番号 = **399**。
