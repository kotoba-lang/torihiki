# frame 564 (2026-09-30 08:2x, cron) — load gate PASSED, budget exhausted → re-anchor re-measure NOT-RUN

## 判定
**NOT-RUN (budget)** — load gate は**通過**したが、実行予算が 2-runtime 実測に着手できない段階で
尽きた。本 frame は new measurement を生成していない (既存 baseline frame-548 を引用, fabricate なし)。

## 負荷ゲート (実測)
- pre-run (torihiki_state.sh 経由 Script Output): 8:15 **57.28 / 28.37 / 19.92** (1/5/15-min)
- 直測 1 回目 8:21 (uptime, redirect→read_file): **16.17 / 19.52 / 18.49** — 全 window <20
- 直測 2 回目 8:21: **15.28 / 18.99 / 18.33** — 全 window <20, 低下局面
- 判定: 直測 2 回整合 + 低下局面 → **gate PASSED** (frame-535 前例型; pre-run 高値は
  直後 6 分で崩落した一過性スパイク)

## コード状態 (直接実測, redirect→read_file 迂回)
- HEAD **1ceb357** (kotoba-migrate-all-20260925 merge; frame-548 の 92b9fc9 から移行)
- `git diff 92b9fc9..1ceb357 --stat -- src/ script/ deps.edn`: deps.edn +28 / script/native-bench.cljk 新設 110 行 / script/nbb-classpath.cljk / src/torihiki/address.cljk / src/torihiki/snapshot.cljk — **コード変更有** (kotoba codemod 群 + clojure.test→kotoba.test d3ebe68)
- uncommitted: `M src/torihiki/fixed.cljk` (+36 行) / `M src/torihiki/slab.cljk` (prior frame 由来の常駐 in-flight, 本 frame 未着手)

## 本 frame の試行 (予算内で中断)
- 測定 copy 再構築を開始: rsync → /tmp/tori-f564 (1 回目作成分は rename 誤命令で
  破損したため rm -rf 後にやり直し) — **JVM/nbb suite・parity・fuzz・bench は全く未実行**
  (suite 計数なし → 引用継続は frame-548 (92b9fc9) 実測 base のまま)
- 次枠 (565) は copy 作り直し (.cljk→.cljc rename を安全に実施すること —
  `find … -exec mv {} 単一宛先 \;` は全ファイルを 1 つに潰す**禁止**; 許可済み方法は
  rename 前例 2026-09-19 frame-506 の python copytree レシピ) 後,
  **1ceb357 上で suite + parity + fuzz を全量新規実測して正本引用 base を re-anchor** すること
  (frame-561 指示の継承; test 数は kotoba.test 移行 d3ebe68 で変動の可能性あり)。

## 引用 (正本, 変更なし)
- frame-548 (2026-09-23 21:2x, 92b9fc9): suite 162 (same-count 66 度目, 357/915 両 runtime PASS) /
  parity 59 / fuzz digest 59 / bench 46 実行目 known-red — ただし HEAD 1ceb357 は src 変更を含むため
  frame-548 引用は「最新 HEAD に対する実測」としては**期限切れ** (次 genuine 枠の re-anchor が必須)

## 次枠番号
**565** (新規実測時: suite 163 / parity 60 / fuzz 60 / bench 47 実行目 — 1ceb357 re-anchor 枠)
