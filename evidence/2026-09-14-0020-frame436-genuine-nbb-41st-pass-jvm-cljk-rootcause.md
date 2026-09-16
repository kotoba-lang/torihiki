# 2026-09-14 00:20 frame 436 — genuine run (load gate passed): nbb suite 41st pass; JVM suite root cause narrowed to .cljk extension (cognitect runner finds 0 tests)

## Load gate (00:20–00:31 uptime direct, /tmp/f436a.txt 由来実測)

1-min **8.54** / 5-min **8.84** / 15-min **9.77** — all <20 → gate 成立 (genuine run).

## HEAD / src invariance

- HEAD **8af801db076a19c9eeaad02696da93e1169aceb0** (frame 428 append commit; 430/433/434/435 と同一)。
- src/ script/ deps.edn 変更なし (本枠もコード変更 0)。

## Gates 実測 (本枠)

1. **nbb suite: PASS — 41 度目**。2 段 classpath 規約どおり:
   第 1 段 `kbb --backend sci --classpath "../text/src" script/nbb-classpath.cljk` → exit 0
   (7 dep, 656 bytes)。第 2 段 `kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk` →
   **Ran 357 tests containing 915 assertions. 0 failures, 0 errors.** (exit 0,
   evidence/test-nbb-436.out / .err 空 / .exit)。
2. **JVM suite: 0 tests — 根本原因を 1 段絞り込み**。`clojure` CLI 実在確認
   (1.12.5.1654, /opt/homebrew/bin/clojure)。`clojure -Spath -M:test` は **全依存解決成功**
   (test-runner dfb30dd を含む — frame-430 の「kbb -Spath 5 dep 非解決」は kbb の kbb 側の
   話で, JVM clojure CLI は解決する)。
   - `clojure -M:test` (cognitect.test-runner 既定): **Ran 0 tests containing 0 assertions**
     (exit 0, evidence/test-jvm-436d.*)。
   - 原因特定: test/ 配下は **全ファイル `.cljk` 拡張子** (address_test.cljk 等 17 ns) —
     cognitect test-runner の既定 `-r #".*-test$"` は `.clj` のみ走査し .cljk を 1 件も
     見つけられない (evidence/test-jvm-436e/f は -m 二重付与の usage error, 無効 —
     正しい直接起動は `clojure -M:test -r '.*-test$'` = 436g/d と同一 0 tests)。
   - つまり cutover 後 JVM suite 落ちの正体は「依存非解決」+「`.cljk` を拾わない runner」の
     2 段。前者は JVM clojure では既に解決済み、後者が残存。
   - `kbb --classpath "$CP" -M:test` 試行 3 件 (test-jvm-436/.err 1410 bytes: kbb は
     Maven/git 非解決を再確認 + `kotoba.bytes.sha256` namespace 不解決; 436b: kbb が
     `-M:test` を file 引数と誤読; 436c: `@noble/hashes/sha2.js` node_modules 不在) —
     kbb 側経路は解決せず。
3. bench / parity / falsify-14: not-run (budget)。

## Canonical reference

正本引用 frame-413 対 428 部分実測: suite **136** / parity **39 度目** / fuzz digest **32 度目** /
bench 赤累計 **32 実行目**。nbb 単独は本枠で **41 度目** 実測 (test-nbb-436.out)。

## NEXT

1. **JVM suite `.cljk` 対応**: cognitect runner が .cljk を走査しない。最小 fix は
   runner 呼び出しに `-r` ではなく test dir の明示 + 拡張子許容 (tools.namespace の
   `clojure.tools.namespace.find/read-ns-decl` は clj(cljc) 既定 — .cljk は第三拡張子で
   `:clj` `:cljc` `:cljs` のどれにも属さない可能性が高い = **runner 側で .cljk→ シンボリック
   対応か build 時 rename かを次枠で 1 件実測して決める**)。suite 137 / parity 40 / bench 33 は
   この直後に。
2. falsify-14 (複数アカウント deficit 合算 → settle-deficit `:deficit (fnil + 0)`
   clearing.cljk:677–690, f12 流 2 アカウント seed 複製)。
3. validate-i53-halt fix パッケージ + bench-tape-cancel-arity fix + fuzz 常設化。

## Scores / 判定

- 発覚 0 件 (JVM suite 0 tests は既知「cutover 後呼び出し不備」の原因特定の進展 — 新規 defect なし)。
  新規 hypothesis なし。スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 13 件, f8–f13 累算 overflow OPEN のまま)。
- 次枠番号 = **437** (新規実測時 suite **137** / parity **40** / bench **33** 実行目;
  JVM .cljk runner 対応 + falsify-14 優先)。
