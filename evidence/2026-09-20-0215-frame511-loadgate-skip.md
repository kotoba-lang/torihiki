# frame 511 load gate skip (2026-09-20 02:09–02:15)

## 結論
test/parity/fuzz/bench/falsify 新規実測 **not-run** — 「全 window <20」不成立 (5-min / 15-min が 3 測定すべて ≥20 かつ 1-min は**上昇**局面で 20 を再超過)。

## 負荷実測 (uptime 直叩き, terminal 空出力 fault 既知のため redirect→read_file 迂回)
- pre-run (torihiki_state.sh block / state script) 02:09: 1-min **26.67** / 5-min **26.52** / 15-min **24.70**
- direct 02:11:56: 1-min **18.00** / 5-min **22.50** / 15-min **23.33** (/tmp/f511_probe0.txt)
- direct 02:12:59: 1-min **18.36** / 5-min **21.79** / 15-min **23.00** (/tmp/f511_probe1.txt)
- direct 02:13:59: 1-min **19.57** / 5-min **21.70** / 15-min **22.90** (/tmp/f511_probe2.txt)
- direct 02:15:00: 1-min **20.92** / 5-min **21.61** / 15-min **22.78** (/tmp/f511_probe3.txt)

1-min は一旦 20 未満に落ちたが 4 測定で **18.00 → 18.36 → 19.57 → 20.92 と単調上昇**し 02:15 に 20 を再超過。5-min (22.50→21.79→21.70→21.61) と 15-min (23.33→23.00→22.90→22.78) は低下局面だが**すべての測定で ≥20**。frame-464/473/486/504/508 規約「全 window <20」厳密不成立のため skip。frame-397/401 前例 (低下局面 1-min <20 を genuine 解釈) とは逆 — 本枠は 1-min が上昇して 20 を割っていないため genuine-lite の前提が崩れる。5/15-min は低下局面なので次枠で gate 成立公算大。

## コード不変確認 (02:12 直接実測)
- HEAD **694e2ac7aecdc7bf2f5468beba3d35faeab81305** (git rev-parse; frame 503/505/506/507/508/509/510 と同一)
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** (空出力直接実測)
- working tree: status/maturity.md (M) + untracked evidence のみ — src/ 変更なし
- 測定 copy **/tmp/tori-f506 現存** (f510 で再利用済み); /tmp/tori-f510 は不在。bench/torihiki/bench.cljk:112 は直接行読みで `(bk/cancel! b oid)` **2 引数のまま** 3-part fix (f15+f16+f17) 未着地を再確認。

## 正本引用
**frame-510 (2026-09-20 0111) 実測 base** 維持: suite **153** 両 runtime PASS (same-count 57 度目, 357/915 0F/0E) / fuzz digest **50** 度目 byte-identical (f488/f505/f506/f509 baseline と同一構成) / parity 52 + bench not-run 繰越 (repo bench/torihiki/bench.cljk:112 2 引数 3-part fix 未着地 — bench 39 実行目繰越)。7 軸 **3/3/3/3/2/1/1** (反証実施 16 件, f8–f17 OPEN)。

## 発見 / hypothesis
- 発見 0 件, 新規 hypothesis 0 件。
- NEXT 未実測変化なし: 3-part bench fix (f15 owner wiring + f16 arg-order + f17 ring-owner) HEAD 着地 (インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。

## スコア
7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。

## 次枠
次枠番号 = **512** (新規実測時 suite 154 / parity 53 / fuzz 51 / bench 39 実行目)。skip-check: skip-check-0920-0215.txt (負荷 5 測定 + HEAD + diff 記録)。
