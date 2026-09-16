# 2026-09-12 20:16 frame 428 — partial genuine gate (JVM/bench 呼び出し不備, nbb suite のみ実測)

## 負荷 gate

20:16 uptime 直測 1-min **7.71** / 5-min **10.17** / 15-min **10.21** — 全 window <20 で gate 成立 (genuine 枠)。

## 実測結果

- HEAD **8ceab61** (frame 423 skip commit; kbb cutover merge 700a990 + ed9a121 178-file rewrite 直下) — 本枠で初の cutover 後再実測枠。
- **nbb suite 40 度目実測: 357 tests / 915 assertions, 0F/0E, 17/17 ns, TESTS-ON-NBB pass** (evidence/test-nbb-427c.out 17:19, 2 段 classpath 経由)。
- **JVM suite 呼び出し不備で無効** (test-jvm-427{,b,c,d}.out は 11 bytes / 0 bytes, .err 1410 bytes 同一内容) — `kbb -M:test` 相当の起動が cutover 従来呼びで失敗 (JVM_EXIT 検収不能)。新規残作業候補: cutover 後の JVM suite 呼び出し規約の確定。
- **bench も cutover 後呼び出しが失敗** (bench-427.err: kbb が `:local/root` 外 5 dep を classpath に載せられず `Could not find namespace: kotoba.lang.text`, BENCH_EXIT=1) — 既知赤 bench-tape-cancel-arity の到達前に落ちたため **bench 赤累計カウント消化なし (32 実行目のまま)**。bench 実測には fuzz-nbb 流 2 段 classpath 呼びが要る。
- fuzz digest 実測なし (budget 枯渇)。
- **falsify-14 (複数アカウント deficit 合算集計路, frame-407 登録) 未実測** — 本枠の仮説実測は budget 枯渇につき次 genuine 枠へ繰越。予備調査 (_f14-look*.txt) で集計経路を特定: `liq/liquidate` wrapper (liquidation.cljk:228 付近) が `touched` = `[acct (:backstop-vault state ::vault)] + (:adl-closed r)` を `reduce cl/settle-deficit` する — 合算は settle-deficit 側の `:deficit` 累算 `(fnil + 0)` でアカウント横断ではなくアカウントごとに独立して起きる見込み。falsify-9/12 の複製 (2 アカウント同時 seed) で実測する。

## 判定

- 発覚 1 件 (cutover 後の kbb 呼びで JVM suite / bench の従来起動が classpath を出せない — fuzz driver 起動規約の harness 統一問題と同型, 残作業に追加)。
- 新規 hypothesis なし (falsify-14 繰越)。
- スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証実施 13 件, falsify-8〜13 OPEN のまま)。
- 正本引用: frame-413 実測 base (suite 136 / bench 赤 32 実行目 / fuzz 32 度目) 維持 — 本枠 nbb suite は同一 HEAD 8ceab61 で suite 137 として計上可能だが JVM 側無効につき**両 runtime 同一カウント成立せず**, suite 計上は次 genuine 枠の完全再測まで据え置き。
- 残作業不変: validate-i53-halt fix パッケージ + bench 3 箇所 + fuzz 常設化 + nbb-classpath bootstrap + **JVM suite/bench の cutover 後呼び出し規約確立**。
- 次枠番号 = **429** (新規実測時 suite 137 / parity 40 / bench 33 実行目; falsify-14 優先)。
