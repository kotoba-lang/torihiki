# 2026-09-11 17:04 frame 418 — not-run (負荷 gate 不成立 + terminal 空出力 fault 再現, no code changes)

## 負荷 gate 実測 (本枠)

| 時刻 | 1-min | 5-min | 15-min | 判定 |
|---|---|---|---|---|
| 17:04 (cron pre-run HOST LOAD) | 60.56 | 58.11 | 42.82 | 全 window >20 |

- gate (全 window <20) 不成立かつ水準が極端 (1-min 60.56) → **本枠新規実測 not-run**。テスト/bench/fuzz 起動は実施せず。

## terminal 空出力 fault 再現 (既知高負荷型)

- 本枠で `bash ~/.hermes/scripts/torihiki_state.sh` / `uptime` / `git status` / `pwd` 直叩き複数回、いずれも exit 0 / stdout 空 — 414/416/417 枠と同一挙動。
- 迂回 (`uptime > /tmp/...` ファイル出力) も本枠では stdout 空で不成立 — 負荷判定は pre-run HOST LOAD 値のみに依拠。
- search_files も rg 未導入エラーで不使用可 (file 検収は read_file で実施)。

## 検収 (本枠 IN-FLIGHT / read_file 直読)

- 416 収納 (2026-09-11-0904-maturity-remeasure-frame415-collection.md): frame-415 orphaned runner 分を**正本採用 suite 137** — test-jvm-415.out 357/915 0F/0E JVM_EXIT=0, test-nbb-415.out 357/915 17/17 ns pass (両 runtime 同一カウント **40 度目**)。bench-415.err = bench-tape-cancel-arity **33 実行目**。fuzz-jvm-412.out FUZZ_JVM_EXIT=1 無効 (fuzz digest **32 度目のまま**)。
- frame-414 (06:14): not-run (1-min 20.98→29.77 一時超過 + budget 枯渇)。
- frame-417 (11:11): not-run (1-min 111–118)。/tmp 迂回は成立していた (111.02/119.73/118.59)。

## 判定

- 発覚 0 件, 新規 hypothesis なし。falsify-14 (複数アカウント deficit 合算集計経路) は**未実測のまま繰越**。
- スコア 7 軸すべて変更なし **3/3/3/3/2/1/1** (反証実施 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ (累算 sum gate + balance-domain gate + notional 上限 + fx/mul-rate 積 overflow + REDUCING 積事前界限 + rate 上限) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + fuzz driver 起動規約 harness 統一 + nbb-classpath bootstrap fix (2 段構成)。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **138** / bench-tape-cancel-arity **34 実行目** 相当; **falsify-14 実測優先**)。
- 次枠番号 = **419**。
