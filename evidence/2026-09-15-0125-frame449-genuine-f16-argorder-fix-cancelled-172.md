# 2026-09-15 01:2x frame 449 — genuine run (load gate passed): **falsify-16 arg-order fix measured — cancelled 0 → 172 (first >0), BENCH_EXIT=0 at n=1M**

## Load gate

1-min **15.77** (< 20) at 01:12 → gate 成立。

## Gates 実測 (本枠)

1. **JVM suite PASS (rename-copy /tmp/torihiki-renamecopy-445)**: Ran 357 tests / 915 assertions, 0F/0E, exit 0, 17/17 ns (`/tmp/torihiki-445-jvm.out`)。
2. **nbb suite PASS (2 段 classpath 規約)**: 357/915 0F/0E, 17/17, TESTS-ON-NBB pass, exit 0 (`/tmp/torihiki-445-nbb.out`) — **同一カウント suite 138 前進確定**。
3. **bench 既知赤 34 実行目 (未 fix copy)**: `/tmp/torihiki-renamecopy-445` (owner wiring あり f15 まま, arg 順未修正) で `-M:bench` → ArityException bench.cljc:112 cancel! 2-arg 再現 (既知赤の消化)。
4. **falsify-16 実測 (frame-448 指示の arg-order fix)**: rename-copy `/tmp/tori-falsify15` bench.cljc:112 を `(bk/cancel! b (bit-and i 1023) oid)` → `(bk/cancel! b oid (bit-and i 1023))` に修正 (f16orig からの 1 行 swap) → `clojure -M:bench 1000000` → **BENCH_EXIT=0, cancelled = 172 (>0 初実測)**, placed 450,908 / resting 376,818 / 134,541 ops/sec / 7,433 ns/op (`/tmp/torihiki-449-bench.out`)。

## 判定

- **発覚 1 件**: falsify-16 静的予測 (arg 順 swap が cancelled=0 の根因) は**実測で裏付け** — cancelled が 0 から正の値 (172) に変わった。ただし直観予測 (~1/1024 × cancel ops ≈ 2,200) より大幅に少ない — 残差候補 (ring の slot 選択が直近 524,287 ops のみ参照 + 生成時に pos? じゃない oid 残置、ring に gen-stale oid が残留して cancel! が 0 を返す等) がまだ大きい。**cancel 経路は実測可能になったが 3x stable の前提 (cancelled 比率の妥当性) は未確定** — ring freshness 候補を次枠で計測。
- parity / fuzz / falsify-14: not-run (budget) — 引用 frame-441 base 維持 (parity 40 / fuzz 33)。
- スコア 7 軸変更なし **3/3/3/3/2/1/1**。再現性 2 のまま (3x stable 未達)。
- 残作業更新: bench-tape-cancel fix 3 部構成の (1)(2) は実測済み — 残るは (3) n≥1M 3 回安定 (cancelled>0 は本枠で 1 回確認済み) + ring 残差の解釈。
- 次枠番号 = **450** (ring 残差計測 / bench 3x stable / falsify-14 / parity 41 / fuzz 34)。

evidence: /tmp/torihiki-449-{bench.out,err,exit}, /tmp/torihiki-445-{jvm,nbb}.out (rename-copy 側), /tmp/torihiki-449-diff.txt
