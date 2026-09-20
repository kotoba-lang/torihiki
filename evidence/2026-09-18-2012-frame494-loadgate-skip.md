# frame 494 load gate skip (2026-09-18 20:12 JST, no code changes, cron)

負荷直測 3 サンプル (20:10 pre-run 53.75/61.99/52.30、20:11:17 51.33/59.64/52.07、20:12:29 45.93/56.31/51.45) — 全 window ≥20 が全サンプルで成立 (最低でも 1-min 45.93) → 「全 window <20」不成立のため test/parity/fuzz/bench/falsify 新規実測 not-run (skip-check-0918-2012.txt)。

terminal stdout 空出力 fault (既知高負荷型) 継続 — 全出力 redirect + read_file 迂回で実測。

- `git rev-parse --short HEAD` = **366321a** (frames 478–493 と同一)、`git diff HEAD --stat -- src/ script/ bench/ deps.edn test/` = **0 行 (空)** → コード不変、working tree untracked は evidence のみ
- repo bench/torihiki/bench.cljk:112 を今枠も直接実測: `(bk/cancel! b oid)` の **2 引数のまま** — 3-part fix (f15+f16+f17) 未着地 (cron code-change 禁止のため不変)
- **/tmp 喪失を新規実測**: `/tmp/f491_runner.sh` (self-contained recipe) と **`/tmp/tori-f451` (owner-wired 測定コピー) の両方が消滅** (macOS /tmp クリーンアップ推定)。次 genuine 枠は frame 451 流の手順 (repo コピー + f15+f16+f17 を /tmp 写しに適用 — repo 本体は不変) で測定コピーを再作成してから bench に進むこと。fuzz/parity/suite 側の recipe は memory の frame-476 recipe (KOTOBA_CHECKOUTS/NODE_PATH + gitlibs text classpath 前置) で再現可能
- 正本引用 (latest genuine) **frame 489 (2026-09-18 0915)** 維持: suite **148** (357/915 0F/0E 両 runtime, same-count 51 度目) / fuzz digest **43 度目** byte-identical / parity **47** (frame 484, FLAT/STATE root) / bench f451 copy n=1M 3-run green cancelled=153,767 — src 不変 (diff 0 行直接実測) のため引用有効
- 新規 hypothesis 0 / 発覚 0 件 (反証実施 **16 件**, f8–f17 OPEN)、NEXT 未実測変化 0 (fix 未着手)
- スコア 7 軸変更なし: **3/3/3/3/2/1/1**

次ランナー (frame 495): 全 window <20 突入後最初の枠で全 gate 再実測。実行順 (1) JVM suite → (2) nbb suite → (3) fuzz → (4) parity、bench は最後に回す (HEAD fix 待ち)。/tmp 測定コピー・runner の再作成を先に。新規実測時 counters: suite **149** / parity **48** / fuzz **44**。
