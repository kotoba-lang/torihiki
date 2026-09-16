# 2026-09-14 05:0x frame 438 — genuine run (load gate passed): **JVM suite 357/915 PASS via rename-copy — cutover 後初の両 runtime 同一カウント (suite 137) + nbb 42 度目**

## Load gate (05:07 uptime 直測, evidence/_f438-load-head.txt)

1-min **12.67** / 5-min **15.62** / 15-min **13.89** — 全 window <20 → gate 成立 (genuine run)。

## HEAD / src invariance (直接実測)

- HEAD **8af801db076a19c9eeaad02696da93e1169aceb0** (frame 428/430/433/434/435/436/437 と同一)。
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 行** (evidence/_f438-load-head.txt)。untracked は maturity.md (M) + evidence 作業ファイルのみ, src/ 変更なし。

## Gates 実測 (本枠)

1. **nbb suite: PASS — 42 度目**。2 段 classpath 規約どおり:
   第 1 段 `kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` → exit 0
   (7 dep, 663 bytes, evidence/_f438-cp.txt)。第 2 段 `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` →
   **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.** namespaces 17/17, TESTS-ON-NBB pass (exit 0,
   evidence/test-nbb-438.out / .err 空 / .exit)。
2. **JVM suite: PASS — 357/915 0F/0E (exit 0, evidence/test-jvm-438.out)** — **rename-copy 経路の suite 転用に成功** (frame-437 falsify-14 で確立した /tmp 実測専用 copy: src/test を byte 同一内容で `.cljk`→`.cljc` 改名 [src/torihiki 22 file + evm/interp + test 17 file] + deps.edn 複写; `clojure -Spath -M:test` 全依存解決成功 → `clojure -M:test` 実行)。src/ 本体は無変更 (rename は copy 側のみ)。17/17 ns 全走査, cognitect 既定 `-r` runner が .cljc を拾って **Ran 357 tests containing 915 assertions. 0 failures, 0 errors**。**これで kbb cutover 後初の両 runtime 同一カウント → suite 計上 136 → 137 前進** (frame-413 base を退役し本枠実測に移行)。
   - 運用上の注記: rename-copy 経路は **classpath 上の .cljk を JVM が解決できない**ことの回避策であり, runner 側 `.cljk` 対応 (tools.namespace 拡張子許容か build 時 rename) が恒久 fix。本枠の実測で「依存解決 + rename」の 2 条件で suite が緑になることを確定 — 呼び出し規約の残作業は「恒久化をどの層でやるか」のみ。
3. bench / parity / fuzz: not-run (budget) — bench 赤累計 **32 実行目** / parity **39 度目** / fuzz digest **32 度目** 引用維持 (正本引用 frame-413 対 428 部分実測; 本枠で suite のみ 137 に前進)。

## Scores / 判定

- 発覚 0 件 (rename-copy suite 転用成功は frame-437 発見の適用で新規 defect なし), 新規 hypothesis なし。
- スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 14 件, f8–f14 累算/集計 overflow OPEN のまま)。
  - テスト 3 の根拠を更新可能: 両 runtime 同一カウントが cutover 後に **再実測成立** (本枠) — ただし JVM 経路が rename-copy 依存のため常設化条件 (runner .cljk 対応) は未達, スコア据え置き。
- 次枠番号 = **439** (bench 33 実行目 / parity 40 / fuzz 33 度目を本経路で再実測可能 — bench も rename-copy + `-M:bench` を次枠で試行)。
