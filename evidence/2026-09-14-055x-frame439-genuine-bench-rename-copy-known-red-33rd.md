# 2026-09-14 05:5x frame 439 — genuine run (load gate passed): **bench `-M:bench` rename-copy 転用実測 — 既知赤到達確認 (bench 赤累計 33 実行目)**

## Load gate (pre-run 直測 05:24)

1-min **14.00** / 5-min **14.44** / 15-min **13.69** — 全 window <20 → gate 成立 (genuine run)。

## HEAD / src invariance (直接実測)

- HEAD **8af801db** (frame 428/430/433/434/435/436/437/438 と同一)。
- `git status --porcelain` = 113 行 (maturity.md M + evidence 作業ファイルのみ), src/ script/ deps.edn 変更なし。

## Gates 实测 (本枠)

1. **bench: rename-copy + `-M:bench` 転用に成功し既知赤 bench-tape-cancel-arity に到達 — bench 赤累計 33 実行目**
   (bench-439.{out,err,exit})。構成:
   - /tmp/tori-jvm-suite-438 (frame-438 suite 実測 rename copy, byte 同一 .cljk→.cljc) に
     bench/torihiki/{bench,calib,curve,probe}.cljc を byte 同一改名 copy 追加 (src/ 本体は無変更)。
   - `clojure -Spath -M:bench` → **exit 0** (全依存解決成功, /tmp/tori_f439_spath.txt)。
   - `clojure -M:bench` → **BENCH_EXIT=1, ArityException at torihiki.bench/run-tape (bench.cljc:112):
     Wrong number of args (2) passed to: torihiki.book/cancel!** — cutover 後一時的だった「既知赤到達前の
     classpath bootstrap 落ち」(frame-428) が解消され, 既知赤そのものを到達再現。
     Boxed math warning (bench.cljc:105/114) は JVM 側警告のみで既知の範囲内。
2. nbb suite / parity / fuzz: not-run (budget, 本枠は NEXT 先頭 1 件を優先)。正本引用 frame-438 実測 base
   (suite **137** / nbb 42 度目 / JVM rename-copy PASS / fuzz digest 32 度目 / parity 39 度目) 維持。
   再現性 2 のまま (bench 修正 + n≥1M 低負荷 3 回安定が条件)。

## Scores / 判定

- 発覚 0 件 (既知赤の到達再現は frame-428 発見「JVM bench 呼び出し規約」の解消確認で新規 defect なし),
  新規 hypothesis なし。
- スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 14 件, f8–f14 累算/集計 overflow OPEN のまま)。
- NEXT 先頭 2 件 (JVM suite 呼び出し規約恒久化・bench 同経路転用実測) のうち bench 実測は本枠で完了。
  残: 恒久 fix 判定 (runner .cljk 対応か build 時 rename) + bench-tape-cancel-arity fix (bench.clj:112
  owner 付き 3 引数) + n ≥ 1M 低負荷 3 回安定実測 → 再現性 3。
- 次枠番号 = **440** (parity 40 度目 / fuzz 33 度目を rename-copy 経路で実測可; bench 修正後の 3 回安定は
  code change 枠のため本枠外)。
