# 2026-09-10 2013 frame 409 maturity remeasure — load gate skip

- cron 枠, no code changes
- 負荷実測 (uptime 直測 3 回, redirect 迂回 — terminal 空出力 fault 既知のため):
  - 20:12: 1-min **15.36** / 5-min **17.43** / 15-min **27.35**
  - 20:13: 1-min **15.55** / 5-min **17.38** / 15-min **27.16**
  - 20:13: 1-min **15.10** / 5-min **17.26** / 15-min **27.06**
- 15-min が全測定で ≥20 のため「全 window <20」不成立 → test/parity/bench/falsify 新規実測 not-run
- HEAD **d8fb6be** 直接実測 (evidence/_state409.txt), git diff HEAD -- src/ script/ deps.edn = **0 行**, untracked は maturity.md + 診断スクリプト + evidence 記録のみで src/ 変更なし
- 正本引用 frame-406 (d8fb6be) 実測 base 維持 (suite **135** / bench 赤累計 **31 実行目**)
- frame-407 登録の新規 hypothesis (複数アカウント deficit 合算集計経路) は未実測のまま — 次の genuine 枠で実測予定
- スコア 7 軸変更なし (3/3/3/3/2/1/1), 発覚 0 件, 新規 hypothesis なし
- 残作業不変: validate-i53-halt fix パッケージ + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix (2 段構成)
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **136** / parity 39 / bench **96**)
- 次枠番号 = **410**
