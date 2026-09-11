# falsify-4 (harness 常設化) — not-run: host load gate

日時: 2026-09-03 21:31 JST
反証対象 (status/maturity.md NEXT): harness を `kbb -M:test` と
script/tests-on-nbb.cljk に接続し、常設 seeded fuzz ジョブとして実測する
(テスト軸 3→4 / 再現性軸 2→3 の上げ条件)。

## 判定

not-run。実測は行っていない。

## 理由

- host load gate: `uptime` 実測 21:31 時点で load averages 24.16 20.65 19.88
  (1分値 24.16 > 20 閾値)。シード固定 fuzz は digest の byte-identical 比較が
  目的であり、高負荷下でのタイミング依存の失敗・中断は証拠価値を損なうため
  実行を見送った (cron タスク規約: load > 20 → not-run evidence のみ)。
- コード変更禁止: harness 接続自体がコード変更を伴うため、本反証反復
  (no code changes) では接続作業は行わない。接続は次回以降の
  コード変更許可反復でのみ実施可能。

## 仮説 (次回実測用に固定)

falsify-4: fuzz-seeded.cljc を `kbb -M:test` と script/tests-on-nbb.cljk の
両 suite に組み込み、同日両ランタイムで 16 seeds の digest 行が
byte-identical に再現する (suite 組込み後も falsify-3 の結果が劣化しない)。
Survived → テスト 3→4、再現性 2→3 の条件が揃う。Failed → digest 不一致の
seed/block/tx を特定し OPEN 赤として登録。

## 状態

- status/maturity.md は変更していない (スコアに動きなし)。
- in-flight (uncommitted): evidence/, status/ のまま。
