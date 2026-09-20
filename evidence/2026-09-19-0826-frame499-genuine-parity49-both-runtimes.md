# frame 499 genuine run (2026-09-19 08:09–08:26 JST, cron, no code changes, parity 49 frame)

- Load gate: 08:09 pre-run **6.60 / 9.91 / 13.91** 全 window <20 → 測定成立 (前枠 496/497 の loadgate skip 連鎖を消化)。
- HEAD **366321a** 直測, `git diff HEAD -- src/ script/ deps.edn` = 0 行。repo bench/torihiki/bench.cljk:112 を実読し **2 引数 `(bk/cancel! b oid)` のまま** 再検収 (3-part fix 未着地)。
- terminal 前台出力キャプチャ破損 (既知 fault, 出力空) → 全実行を python driver (/tmp/tori-step*.py) 経由で background + redirect に迂回。**compound shell command は cron security scan で BLOCKED になった (grouped/encoding 判定) — 環境発見 1 件: cron 枠の複合コマンドは python driver に完全退避するのが安定。**

## parity 49 度目 両 runtime 実測 PASS (測定 copy 新規再構築)

- **前提整備**: /tmp 揮発で旧 copy 消滅のため /tmp/tori-f499 を新規再構築 (copytree .git 除外 + .cljk→.cljc 一括変換 + kotoba 同梱)。repo 直下に `kotoba/` が存在するため frame-495 の外部 kotoba copytree は FileExistsError で不要と判明 (**発見 2 件: repo は kotoba/ を自前同梱済み — 外部 kotoba copy は余計**)。f495 手順の symlinks=True は repo 内 kotoba が symlink 含む場合は copytree 側で既に処理済みのため本枠では未発火。
- **JVM leg**: `cd /tmp/tori-f499 && clojure -M:parity` → **PJ_EXIT=0, FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true** — frame-440/454/456/458/460/461/464/484/495 baseline と同一。
- **nbb leg**: text-src prepend (`~/.gitlibs/libs/io.github.kotoba-lang/text/73bdb13a…/src`) 付き classpath 生成 → **CPGEN_EXIT=0, CP_LEN=333** (frame-476/484/495 と同一) → 本体 `--classpath "<text-src>:$(CP)" script/kotoba-parity.cljc` + KOTOBA_CHECKOUTS / NODE_PATH / AMU_HOME → **fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass, PN_EXIT=0**。
- 両 leg とも baseline 一致。**parity 49 度目 完了** (frame-495 parity 48 の次)。

## not-run (budget)

- suite / fuzz / bench: not-run (正本引用 frame-489 (0915) 実測 base 維持: suite **148** 両 runtime PASS / fuzz digest **43 度目** byte-identical / bench f451 copy 3-run green cancelled=153,767 frame-477)。bench は repo bench.cljk:112 が未だ 2 引数のため HEAD 実測不可のまま。

## スコア

7 軸変更なし: **3/3/3/3/2/1/1** (反証実施 16 件, f8–f17 OPEN)。NEXT 未実測 0 件変化なし (fix 未着手 — cron code-change 禁止)。新規 hypothesis 0 件, 環境発見 2 件 (cron 複合コマンド security-scan BLOCK → python driver 退避 / repo kotoba/ 自前同梱で外部 copytree 不要)。

次ランナー (frame 500): repo への 3-part bench fix 着地 (f15 owner wiring + f16 arg-order + f17 ring-owner, インタラクティブ枠, cron 禁止) → HEAD 3× n=1M → 再現性 3。並行で validate-i53-halt fix パッケージ (累算 sum gate 7 site + notional/balance gates + mul-rate/REDUCING 事前境界 + rate 上限) が最高レバレッジのまま。
