# 2026-09-13 01:12 frame 430 — genuine run (load gate passed), cutover JVM/bench invocation breakage re-confirmed

## Load gate (01:12 uptime direct)

1-min **8.75** / 5-min **9.24** / 15-min **9.56** — all <20 → gate 成立 (genuine run).

## HEAD / src invariance (direct measurement)

- HEAD **8af801d** (frame 428 append commit; frame 428 partial-genuine = 1e2d09f, frame 423 skip = 8ceab61).
- `git diff HEAD --stat -- src/ script/ deps.edn` = **0 行** (DIFF_LINES=0, /tmp/tori_frame429.txt).
- untracked: evidence skip 記録 + 診断/_427call*/_f14-look* 作業ファイル + bench-427.out のみ, src/ 変更なし.

## Gates attempted

- **JVM suite / bench / parity: not-run this frame** — cutover 後呼び出し規約未確立のまま (frame 428 発覚の継続)。
  直接検収: `kbb -Spath` が **5 dep 非解決を再確認** (io.github.kotoba-lang/text, org.clojure/clojure, chain, merkle-sum, bytes — kbb は Maven/git coordinates を解決しない; /tmp/tori_spath.txt)。`kbb -Saliases` 実測: bench/parity/test alias は現存 (test は cognitect.test-runner 名指し) が、classpath が deps.edn pin を解決できず alias 呼びは既知落ちのまま。
- nbb suite: 未実測 (budget) — frame 428 の 40 度目実測 (test-nbb-427c.out 357/915 pass) を引用維持。

## Canonical reference

正本引用 **frame-413 対 428 部分実測** 踏襲: suite **136** (両 runtime 最後の同一カウントは frame-413/test-412 群; nbb 単独 40 度目 = frame-428) / parity **39 度目** / fuzz digest **32 度目** / bench 赤累計 **32 実行目**。

## NEXT (highest-leverage)

1. **kbb cutover 呼び出し規約の確立** — JVM suite / bench / parity を `--classpath` 明示構成 (`../text/src:<chain>/src:<merkle-sum>/src:<bytes>/src:src:test:resources` 相当) で動かす規約を 1 本に固定し全 gate 再実測 (suite 137 / parity 40 / bench 33)。これは fix パッケージの前置条件 (gate が走らなければ fix 検証も走らない)。
2. falsify-14 (複数アカウント deficit 合算, frame-407 登録) — 予備調査済 (liquidation.cljk:228 付近 reduce, f9/12 流 2 アカウント seed 複製)。
3. validate-i53-halt fix パッケージ (累算 sum gate 6 site + notional/balance-domain/mul-rate/REDUCING 積/rate 上限) + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + nbb-classpath bootstrap (2 段構成)。

## Scores

7 軸変更なし **3/3/3/3/2/1/1** (反証 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。発覚 0 件 (cutover 呼び出し不備は frame-428 済の継続), 新規 hypothesis なし。次枠番号 = **431**。
