# frame 478 (2026-09-17 12:21–12:26 JST) — suite run attempts, both invocation paths failed

## 条件
- Host load: 13.75 / 15.34 / 16.86 (12:15) — 全 < 20, 実行可。
- HEAD: 366321a (frame 471/472 retro load-gate-skip)。
- cron ターミナル stdout キャプチャ破損 (echo すら空) → 全結果はリダイレクトファイルで回収。

## 実測 1: `clojure -M:test`
- evidence/2026-09-17-1221-frame478-suite.out / .exit
- exit 0 だが **"Testing user" / Ran 0 tests containing 0 assertions / 0 failures** — 実 suite (357 tests / 915 assertions) が全く走っていない。alias が空の test dir を見ている疑い。**無効測定 (0 tests)**。

## 実測 2: `kbb -M:test`
- evidence/2026-09-17-1225-frame478-suite-kbb.out / .exit
- `kbb: 5 dep(s) are not :local/root and are NOT on the classpath ... io.github.kotoba-lang/{text,chain,merkle-sum,bytes}, cognitect-labs/test-runner`
- `Could not find namespace: kotoba.bytes.sha256` → 異常終了。
- 既知の classpath bootstrap 問題と整合: 正手順は `kbb --classpath` 経由 (script/nbb-classpath.cljk / `.cljk` bootstrap, memory の 0551/0657/0712/0840/1113/2029 再確認群と同じ落とし穴)。**素 `kbb -M:test` は落ちる**。

## 判定
- 本 frame は **実 suite 計測不成立**。test/reproducibility スコア変更なし (3/2)。
- 次フレームへの申し送り: (1) JVM 実測は直近実績コマンド形 (`kbb -M:test` 成功時の evidence/test-2029.out 系と同一 alias/環境の再現手順) を evidence から復元してから実行; (2) nbb 側は必ず `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk)"` 経由; (3) `clojure -M:test` の 0-tests は alias/ソースパス設定の変化の可能性 → deps.edn の :test alias を確認してから触る (コード変更は本ジョブのポリシー上しない)。
