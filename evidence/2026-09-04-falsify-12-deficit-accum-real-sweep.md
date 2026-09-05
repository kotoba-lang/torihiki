# falsify-12 — `:deficit` 累算 (clearing.cljc:711) は実 sweep 経路でも sum 無検査で 2^53 を越え、無音 cross-runtime root 分岐を起こす (累算クラス第 5 例・単一 site 系は完了)

- date: 2026-09-04 20:29–21:56 JST (host load gate: 開始時 1-min 18.87 < 20 通過, 実測中 21.56–59.60 変動)
- hypothesis: evidence/notrun-2026-09-04-1245.md (実行前登録, 12:45; 10:55–20:29 は負荷ゲート不成立で not-run 継続 — falsify12-notrun-load-1412.md ほか 9 件)。
- runtimes: JVM `clojure -M -e '(load-file "evidence/falsify12-multi-deficit.cljc") (load-file "evidence/falsify12-jvm-driver.clj")'` → falsify12-jvm.{out,err} (exit=0); nbb `nbb --classpath "$(nbb script/nbb-classpath.cljs)" evidence/falsify12-driver.cljs` → falsify12-nbb.{out,err} (exit=0)。両 stdout は REPL echo 行を除き diff で比較。
- method: `st/apply-block` (state.cljc:1189 end-of-block sweep) → `sweep-liquidations` (state.cljc:1052) → `liq/liquidate` (liquidation.cljc:228) → `cl/settle-deficit` (clearing.cljc:677) の**実物 sweep 経路のみ**を使用。probe A/B は seed なしの実清算、probe C は falsify-9 流 labelled synthetic-seed (新鮮な清算が残す state = collateral −d + live position を block 間で再現し、sweep 機構に実 delta を食わせる)。accumulator と適用経路は無修正の production code。src/ test/ は未変更 (HEAD dd55c85)。

## ハーネス構築で実測した前提 (verdict に効く知見)

1. **cl/market 由来の market でなければ誰も清算されない**: 裸 `{:id 1 ...}` map は `:maintenance-margin-rate` を持たず mm=0 → `liquidatable?` が常に false。falsify-9/10/11 の harness が通っていたのはこのため (probe は direct 呼び or seed 前提)。
2. **sweep は `:marks` を読む** (state.cljc:1072; oracle への fallback は mark 未計算時のみ): oracle crash は実 `:oracle` tx で通す必要がある (`reprice!` state.cljc:367 が mark を再計算)。素の `assoc-in [:oracle 1]` だけでは sweep は no-op。
3. **発見 (副次): settle-deficit の delta check が overflow 状態を halt に変換する**: collateral −i53-max + live position で実 sweep を回すと `fx/check :deficit` (clearing.cljc:711) が `value 9007199255739991` (deficit delta が delta 自身の check で域外) で **apply-block 内 throw** — falsify-6/7 型 halt が、falsify-8 クラスの域外状態から sweep 経由で自然に到達する。すなわち balance-domain gate が無いまま deficit 経路で「divergence か halt か」が delta の大きさで切替わる。fix の累算 sum gate は delta check とは独立に sum 側に必要 (既存 NEXT の確認)。

## Measured — probe A: real-sweep-walk (seed なし, 両 runtime root 完全一致)

S=100000 lots, C=1e6, oracle tx 1000→1, 60 空ブロック。sweep は block 6 時点で 3 アカウント全員を清算し終え、各 deficit = **78,920,000** (in-domain, even), collateral 0。k6–k65 の root は JVM/nbb 完全一致 — 実運用スケールの deficit では分岐しない (2^53 まで 1.1e8 倍必要)。

## Measured — probe B: deficit-crossing S=1e6 C=1e9 (seed なし, 両 runtime root 完全一致)

実損失 ~1e9/アカウントでは **deficit は形成されず** (ADL が counterprofit で吸収, collateral 1e9 のまま)。probe A と合わせ、**素の実清算だけでは 2^53 crossing に到達しない** — crossing は累算 (同一アカウントへの反復 settle) で到達する種類の問題であり、probe C がそれを実測する。

## Measured — probe C: deficit-accum 3×d through the REAL sweep (両 runtime, throw 皆無)

seed: collateral **−d = −9007190000000001 (奇数)** + live position 1000 lots, mark 1。各 block の end-of-block sweep が実物 `liquidate` → stage-1 fill (実測損失 delta 999000) → **settle-deficit** で deficit を累算。

| block | JVM deficit / root | nbb deficit / root | 一致? |
|---|---|---|---|
| k1 | 9007190000999001 (奇数 < 2^53, 可逆) / f290a175… | 同 / f290a175… | ✅ |
| k2 | 18014380001998002 (偶数 ∈ [2^53, 2^54), even grid で可逆) / 282073aa… | 同 / 282073aa… | ✅ |
| k3 | **27021570002997003 (奇数 ≥ 2^53, double 非可逆)** / **97e35070…** | **27021570002997004** (丸め +1) / **065f44e4…** | ❌ **発散** |

throw 皆無、同一 apply-block 列で JVM/nbb state-root が k3 で不一致 — falsify-8/9/10/11 と同一クラスの**第 5 例**で、かつ **falsify-9 の deficit crossing を direct 呼び出しではなく実 sweep 経路 (state.cljc:1189 → 1052 → liquidation.cljc:228 → clearing.cljc:677) で再現**した。`:deficit` は state root に入る (state.cljc:1640 encode-clearing-account) ので検知は root 比較のみ。

## Verdict

**Confirmed (partial-scope).** `:deficit` 累算 (clearing.cljc:711) は delta-checked / sum-unchecked で、実 sweep 経路でも 2^53+1 crossing → 無音 cross-runtime root 分岐が成立する (累算クラス第 5 例)。これで累算 site の単一アカウント系実測は **collateral (f8) / :deficit (f9+f12) / :funding-residue (f10) / :fees-collected (f11) の 4 site すべてで確定** — NEXT の「累算 sum gate」スコープは実測裏付けが揃った。未実測として残るのは (a) 複数アカウントの deficit **合算**が単一集計値 (例: commit の merkle-sum) を通る経路 (per-account accumulator ではないので別仮説), (b) `:insurance-fund` 累算 (liquidation.cljc:195, 同型) — いずれも同一 fix パッケージ (sum gate) で閉じるためブロックはしない。

## Fix への含意 (NEXT への反映)

- 累算 sum gate の対象 site リスト確定: **:collateral 各 (fnil +/− 0) / :deficit (clearing.cljc:711) / :funding-residue (funding.cljc:138) / :fees-collected (clearing.cljc:410/560) / :insurance-fund (liquidation.cljc:195)**。実装は validate 層か no-op clamp、fx/check throw による gate 禁止 (f6/f7/f12 発見の halt を作る)。
- **settle-deficit の delta check 自体が halt gate になる** (fx/check :deficit (- c) が域外 deficit delta で throw, apply-block 内) — balance-domain gate が先に deficit を発生させない設計でないと、sum gate を入れても delta 側の halt が残る。gate は「deficit を作らせない」方向 (清算 waterfall の shortfall を i53 域内に clamp) が必須。
