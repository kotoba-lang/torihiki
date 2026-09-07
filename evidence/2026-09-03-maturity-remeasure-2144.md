# maturity re-measure — 2026-09-03 21:44 JST

反復: torihiki maturity rank iteration (cron, "No code changes" 制約)。

## 実測

- JVM `clojure -M:test`: Ran 357 tests containing 915 assertions. 0 failures, 0 errors. (21:44 実測)
- nbb `script/tests-on-nbb.cljs` (pins 由来 classpath): namespaces 17/17, Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass. (同時刻実測)

両ランタイム同日同カウント → テスト軸 3 の根拠は再確認。

## not-run (host load gate)

- `uptime` 実測 21:44: load averages 20.06 22.27 21.85。1 分平均 20.06 > 20 閾値。
- cron 規約 (load > 20 → not-run) に従い、seeded fuzz 再実行 (digest byte-identical 比較) と bench 3 条件実測は見送り。
  再現性軸 2 / 反証軸 3 のスコアは 20:48–21:11 の既存 evidence (fuzz-jvm-2111.out / fuzz-nbb-2111.out / remeasure-2111) を据え置き引用。
  注: fuzz digest 比較自体は timing 非依存だが、規約は先行 not-run 記録 (falsify-4-notrun-load, 21:31) と同一運用で踏襲した。

## スコア判定

7 軸すべて前回 (remeasure-2111) から変動なし。上げ条件 (テスト 3→4 / 再現性 2→3) は falsify-4 の harness suite 接続待ちで、これはコード変更を伴うため本反復では不可能。

## NEXT (改訂)

falsify-4 hypothesis は据え置き: fuzz-seeded.cljc を両 suite に接続し常設化する
(次回コード変更許可反復で実施)。通れば テスト 3→4 と 再現性 2→3 が同時に根拠づけられる
(1 反復 2 軸分の leverage)。governor/運用 (ともに 1) はさらに先。
