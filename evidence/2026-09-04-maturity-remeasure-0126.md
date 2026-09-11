# maturity re-measure 2026-09-04 01:2x (15th dual-runtime iteration)

## gate

- iteration start host load (01:26): 19.88 / 18.44 / 19.42 — 15-min < 20 → runnable (borderline)
- iteration end host load (01:29): 15.00 / 17.90 / 19.07

HEAD dd55c85 (unchanged since remeasure-0114)。コード変更なし。

## 実測

1. JVM `clojure -M:test` → evidence/test-jvm-0126.out:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors.`
2. nbb `nbb --classpath "$(nbb script/nbb-classpath.cljk)" script/tests-on-nbb.cljk` → evidence/test-nbb-0127.out:
   `Ran 357 tests containing 915 assertions. 0 failures, 0 errors. TESTS-ON-NBB: pass`
   → 両ランタイム同カウント **15 度目** の実測。
3. seeded fuzz 16 seeds, 両ランタイム:
   - JVM → evidence/fuzz-jvm-0128.out, nbb → evidence/fuzz-nbb-0128.out (both exit 0)
   - diff (grep '^seed') → **identical (JVM==NBB)**
   - かつ 0119 baseline とも identical
     → seed 固定 digest byte-identical は **14 度目** の実測で成立。
4. bench `clojure -M:bench` → exit=1, `Execution error (ArityException) at
   torihiki.bench/run-tape (bench.clj:112)` (evidence/bench-0128.out/.err — 6 実行目)。
   ソース追認: bench.clj:112 は 2 引数 `(bk/cancel! b oid)` のまま。

## 前回 (0114) 以降の新規エビデンス取り込み

- falsify-5 (00:29–31, ring offset 境界): bench の cancel 経路は n ≤ 524288 で
  完全に素通りし (tape 内 cancel op 450,447 個全 skip 実測)、crash 下限は
  n = 524289 を実測 (524288 exit 0 / 524290 exit 1)。bench 赤の本質は
  ArityException に留まらず「bench が cancel を exercise しない」点まで拡大。
  → 再現性 3 の条件 (低負荷 3 回安定) は **n ≥ 1M** で行うことが確定。
- falsify-6 (00:48–01:01, H6 survived, 両 runtime 実測): validate が i53 域外
  amount (2^53) を通過させ (validate=nil)、apply-block が
  `torihiki.fixed: value escaped the i53 domain` を throw → 署名済み 1 tx で
  全 validator を同一位置で halt できる liveness 違反。state-root は
  i53-max 境界で JVM==nbb byte-identical を追認。
  → falsify-6 の verdict が指示した **OPEN 赤 validate-i53-halt の
  maturity.md への登録が未完了だった**ため、本 iteration で登録 (下記)。
  fix: api/validate に :deposit / :withdraw / :order qty の i53 上限検査
  (:bad-amount) を追加 (api.cljc:202, 245, 68)。storage 層 throw では足りない。

## スコア判定

- テスト 3 維持: 15 度目の両ランタイム同カウント。fuzz suite 未接続のため 4 見送り
- 反証 3 維持: byte-identical 14 度目 + falsify-5/6 が反証ループ機能の実例。
  harness 常設化未達のため 4 見送り
- 再現性 2 維持: fuzz 再現 14 度目だが bench 3 条件が OPEN 赤のため不達
  (falsify-5 により条件は n ≥ 1M で 3 回と確定)
- spec 3 / 実装 3 / governor 1 / 運用 1: 本 iter で動きなし
  (validate-i53-halt は新規赤だが既存 spec/実装スコアの根拠記述を変えない —
  「validate is total, never thrown from」契約の違反は次反復の fix で証明)

## OPEN 赤 (2 件)

1. bench-tape-cancel-arity: bench.clj:112, 6 実行目で再確認 (bench-0128.err)
2. validate-i53-halt: falsify-6 実測 (evidence/falsify6-{jvm,nbb}.out),
   本 iteration で maturity.md に正式登録

## NEXT (提案)

優先順: (1) validate-i53-halt fix — 単 tx chain-halt は bench 赤より深刻
(セーフティ > 測定)。api/validate に :bad-amount i53 検査追加は小差分で、
テスト追加も容易。(2) bench-tape-cancel-arity fix (owner 付き 3 引数) と
n ≥ 1M 低負荷 3 回安定実測で 再現性 3。(3) fuzz suite 常設化で テスト/反証 4。
