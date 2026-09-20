# frame 500 genuine run (2026-09-19 09:08–09:13 JST, cron, no code changes, suite+fuzz frame)

- Load gate: 09:08 pre-run **11.74 / 13.84 / 16.97** 全 window <20 → gate passed (frame 496–498 loadgate skip 連鎖の後、frame 499 に続き 2 連続 gate 成立)。
- HEAD **366321a** (git status: maturity.md M + evidence のみ)。repo bench/torihiki/bench.cljc:112 は frame 499 実読のまま 2 引数 (3-part fix 未着地, 本枠は再読せず)。
- terminal stdout キャプチャ破損 (既知 fault) + compound command BLOCK (frame 499 発見どおり) → 全実行は python driver `/tmp/tori-frame500.py` (+ fuzz 修正 driver `/tmp/tori-fuzz500b.py`) 経由。
- measurement copy **/tmp/tori-f499** を再利用 (frame 499 で新規構築済み: copytree + .cljk→.cljc 変換 + kotoba/ 同梱)。

## suite 149 両 runtime PASS — 同一カウント **52 度目**実測

- JVM `clojure -M:test` (copy 上) → **357 tests / 915 assertions, 0 failures, 0 errors, JVM_EXIT=0, err 0 bytes** (evidence/f500-test-jvm, namespaces 17/17 相当, tail 実収)。
- nbb 2-stage classpath: text-src prepend (`~/.gitlibs/libs/io.github.kotoba-lang/text/73bdb13a…/src` 付き CPGEN, **CP_LEN=438** 本枠 — frame 476/484/495 の 333 と異なるが CPGEN_EXIT=0・suite 成立。長さ変動はパス構成由来の可能性、次枠以降要観察) → `kbb --backend sci --classpath "<text-src>:$(CP)" script/tests-on-nbb.cljc` → **357/915, 0 failures, 0 errors, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0, err 0 bytes** (evidence/f500-test-nbb)。

## seeded fuzz — digest **44 度目** byte-identical

- JVM `clojure -M -e '(do (load-file "evidence/fuzz-seeded.cljc") (fuzz-seeded/run))'` → 2715 bytes, EXIT=0 (evidence/f500-fuzz-jvm)。
- nbb driver (evidence/fuzz-nbb-driver.cljc, copy 側で load-string target を .cljc に 1 行修正 — **script-only, repo 変更なし**; copy の .cljk→.cljc 変換により driver が `.cljk` を参照して ENOENT になったのを修正。**発見 1 件: copy 側 rename 後は fuzz driver 内の .cljk 参照も変換対象に含める必要**) → **2715 bytes, EXIT=0** (evidence/f500-fuzz-nbb)。
- `diff` **空 = byte-identical, fuzzdiff exit 0** (evidence/f500-fuzz-diff.txt, f500-fuzzdiff.exit)。frame 489 (43 度目) に続き 44 度目。

## not-run (budget)

- bench / parity: not-run (bench は repo bench.cljk:112 が未だ 2 引数のため HEAD 実測不可 — copy 3-run green series は frame 477 正本引用。parity 49 度目は frame 499 正本引用)。

## スコア

7 軸変更なし: **3/3/3/3/2/1/1** (反証実施 16 件, f8–f17 OPEN)。新規 hypothesis 0 件, 発見 1 件 (copy 側 .cljk 参照の rename 転写漏れ — fuzz driver 1 行修正で解消, script-only)。

次ランナー (frame 501): repo への 3-part bench fix 着地 (f15 owner wiring + f16 arg-order + f17 ring-owner, インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。並行で validate-i53-halt fix パッケージ (累算 sum gate 7 site + notional/balance gates + mul-rate/REDUCING 事前境界 + rate 上限) が最高レバレッジのまま。
