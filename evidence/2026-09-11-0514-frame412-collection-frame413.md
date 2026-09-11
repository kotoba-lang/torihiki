# 2026-09-11 05:14 frame 413 — frame-412 orphaned runner evidence 収納 + 負荷 gate 実測 (no code changes, cron)

## 負荷 gate (frame 413 本枠, 05:14 直測 4 回, /tmp/tori_diag.txt)

| 測定 | 1-min | 5-min | 15-min |
|---|---|---|---|
| 05:14:36 | 6.99 | 12.65 | 14.16 |
| 05:14:39 | 7.23 | 12.60 | 14.14 |
| 05:14:42 | 6.73 | 12.41 | 14.06 |
| 05:14:45 | 6.73 | 12.41 | 14.06 |

全測定で全 window <20 → **gate 成立**。実行プロセス検査: 412 runner 由来の clojure/nbb/torihiki 実行プロセスは無し (412 orphaned runner は完了済み)。

## frame-412 orphaned runner evidence 収納 (04:25 起動 → 05:08 完了, mtime 実測)

- `evidence/test-jvm-412.out` (05:08:24): **357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0**
- `evidence/test-nbb-412.out` (05:08:33): **357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0**
- → 両 runtime 同一カウント **39 度目実測**, **suite 136** として本枠で正本採用 (411 skip 枠の採用先例どおり)。411 orphaned runner (01:08, test-*-411.{out}) も同一 HEAD 状態 7ec6411 で同カウントだが二重計上を避けるため不採用 (併記のみ)。
- `evidence/bench-412.err` (05:08:56): 既知赤 bench-tape-cancel-arity **32 実行目** — `Execution error (ArityException) at torihiki.bench/run-tape (bench.clj:112). Wrong number of args (2) passed to: torihiki.book/cancel!`, n=5,000,000, BENCH_EXIT=1。再現性 2 のまま。
- `evidence/fuzz-jvm-412.{out,err}`: **呼び出し不備で無効** — `FUZZ_JVM_EXIT=1` + `FileNotFoundException: evidence/fuzz-jvm-driver.cljc (No such file or directory)`。存在しない driver ファイル名で起動された (正呼び: JVM は `(load-file …) (fuzz-seeded/run)` 構成, nbb は 2 段 classpath 経由 fuzz-nbb-driver.cljs)。**fuzz digest 消化カウントは 32 度目のまま (変化なし)**。harness 側の fuzz 起動規約ずれは新規残作業候補。
- root `evidence-1789068311.out` (04:25, repo 直下 untracked): nbb `scripts/anatomy_evidence.cljs` ENOENT — 存在しない landing/診断 script を呼んだ失敗 (src/ と無関係, 412 runner の別エラー)。収納対象外のまま現置。

## HEAD / src 不変

- HEAD **d2b0407** (frame 411 skip commit; 直前 landing = 7ec6411) — `git log -6` / `git status --porcelain` / `git diff HEAD --stat -- src/ script/ deps.edn` = **0 bytes** 実測 (05:14)。untracked は maturity.md 正本が既commit済みのため本枠記録 (本ファイル) と上記 412 evidence + root 1 ファイルのみ。
- 412 runner は ledger 未記入・未 commit のまま完了 (orphaned) — 本枠が収納。

## 判定

- 発覚 0 件, 新規 hypothesis なし (frame-407 登録 hypothesis: falsify-14 候補「複数アカウント deficit 合算集計経路」は本枠未実測 — run budget 枯渇につき次 genuine 枠へ繰越)。
- スコア 7 軸すべて変更なし **3/3/3/3/2/1/1** (反証実施 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap fix (2 段構成) + **fuzz driver 起動規約の harness 統一** (本枠新規, 412 fuzz 無効の教訓)。
- 次枠番号 = **414** (新規実測時 suite **137** / parity **39** / bench **97** 相当; falsify-14 実測優先)。
