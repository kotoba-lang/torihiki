# frame 494 load gate skip (2026-09-18 21:05–21:07 JST, cron, no code changes)

- 負荷: pre-run script block (21:04) **36.35 / 27.00 / 25.17**, 直測 21:05 `uptime` **41.23 / 30.64 / 26.71** — 両測定とも全 window ≥20 で「全 window <20」不成立 → test/parity/fuzz/bench/falsify 新規実測 **not-run**。
- torihiki_state.sh stdout 空 (既知高負荷 fault) + terminal 空出力 fault — `uptime` 直叩き + redirect→read_file 迂回実測 (frame-489 同型の迂回)。
- HEAD **366321a** 直測 (21:06, `git log` / `git status` 経由) — frames 477–489 と同一。`git diff HEAD --stat -- src script deps.edn` = **0 行** (空出力), working tree = maturity.md (M) + evidence のみ (untracked frame 467–493 記録)。
- 正本引用 **frame-489 (0915) 実測 base** 維持: suite **148** 両 runtime PASS (357/915 0F/0E, same-count 51 度目) / fuzz digest **43 度目** byte-identical / parity 47 (frame-484) / bench f451 copy 3-run green cancelled=153,767 (frame-477)。
- OPEN 赤 6 site 引用行を 366321a で直接行読み再検収: bench/torihiki/bench.cljk:112 `(bk/cancel! b oid)` 2-arg / src/torihiki/api.cljk:68 `:order` qty integer?/pos? のみ (no i53/notional cap) / api.cljk:202 `:deposit` amount integer?/pos? のみ (no i53 cap) / clearing.cljk:711 `:deficit` `(fnil + 0)` sum 無検査 / clearing.cljk:726 `:collateral` `(fnil + 0)` sum 無検査 / funding.cljk:138 `:funding-residue` `(fnil + 0)` sum 無検査 — 全引用現存有効。
- NEXT 未実測: 3-part bench fix (f15+f16+f17) HEAD 着地 (インタラクティブ枠, cron code-change 禁止) → HEAD 3× n=1M → 再現性 3。validate-i53-halt fix パッケージ + 累算 sum gate 6 site も未着手。
- 発覚 0 件, 新規 hypothesis 0 件。スコア 7 軸変更なし (**3/3/3/3/2/1/1**; 反証実施 16 件, f8–f17 OPEN)。
- 次枠番号 = **495** (parity 48 優先; 負荷 <20 全 window 突入後最初の枠で全 gate 再実測)。
