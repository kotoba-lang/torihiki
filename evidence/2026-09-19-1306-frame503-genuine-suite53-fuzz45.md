# frame 503 genuine run (2026-09-19 13:02–13:06 JST, cron, no code changes, suite+fuzz frame)

- Load gate: 13:02 pre-run **20.64 / 18.17 / 17.32**, 13:06 post-run **16.27 / 17.28 / 17.05** — 15-min window <20 → gate passed (frame 502 は 15-min 21.01 で skip、今枠で復帰)。
- HEAD **dc55cf3** (git status: maturity.md M + evidence のみ)。repo bench/torihiki/bench.cljc:112 は frame 499 実読のまま 2 引数 (3-part fix 未着地, 本枠は再読せず)。
- terminal 前景 900s が background 昇格 → python driver `/tmp/tori-frame503.py` (frame 500 流用, evidence prefix f503) 経由で全実行、process_manage で待ち合流。measurement copy **/tmp/tori-f499** を再利用 (copy 側 fuzz driver は frame 500 の .cljc 1 行修正済みのまま)。

## suite 両 runtime PASS — 同一カウント **53 度目**実測

- JVM `clojure -M:test` (copy 上) → **357 tests / 915 assertions, 0 failures, 0 errors, exit 0** (evidence/f503-test-jvm)。
- nbb 2-stage classpath (text-src prepend, **CP_LEN=438** — frame 500 と同値, 333 変動は再発せず) → `kbb --backend sci --classpath "<text-src>:$(CP)" script/tests-on-nbb.cljc` → **357/915, 0 failures, 0 errors, TESTS-ON-NBB: pass, exit 0** (evidence/f503-test-nbb)。

## seeded fuzz — digest **45 度目** byte-identical

- JVM `(load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run)` → **2715 bytes, exit 0** (evidence/f503-fuzz-jvm)。
- nbb driver (evidence/fuzz-nbb-driver.cljc, copy 側 .cljc 修正版) → **2715 bytes, exit 0** (evidence/f503-fuzz-nbb)。
- `diff` **空 = byte-identical, fuzzdiff exit 0** (evidence/f503-fuzz-diff.txt, f503-fuzzdiff.exit)。frame 500 (44 度目) に続き 45 度目。

## not-run (budget)

- bench / parity: not-run (bench は repo bench.cljk:112 が未だ 2 引数のため HEAD 実測不可 — copy 3-run green series は frame 477 正本引用。parity は frame 499 正本引用)。

## スコア

7 軸変更なし: **3/3/3/3/2/1/1** (反証実施 16 件, f8–f17 OPEN)。新規 hypothesis 0 件, 新規発見 0 件。

次ランナー: repo への 3-part bench fix 着地 (f15 owner wiring + f16 arg-order + f17 ring-owner, インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。並行で validate-i53-halt fix パッケージ (累算 sum gate 7 site + notional/balance gates + mul-rate/REDUCING 事前境界 + rate 上限) が最高レバレッジのまま。
