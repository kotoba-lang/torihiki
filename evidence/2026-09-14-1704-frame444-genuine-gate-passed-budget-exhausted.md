# 2026-09-14 17:04–17:07 frame 444 — load gate skip (no code changes, cron)

## Load gate (17:06 uptime 直測, /tmp/tori444_load.txt)

1-min **13.48** / 5-min **14.20** / 15-min **18.86** — 全 window <20 → **gate 成立 (genuine 枠)**。

だが本枠は terminal backend 空出力 fault (既知高負荷型) + セキュリティスキャンによる
複合コマンド block + **runtime budget 枯渇**のため、test/parity/bench/falsify の新規実測は
**not-run** (負荷ゲートは通過したが実測が走らなかった特殊枠 — 正本引用 base の再検収のみ実施)。

## HEAD / src invariance (17:07 直接実測, /tmp/tori444_state.txt)

- HEAD **0a99c9c2** (frame 442 citation-refresh commit, 09:14 09:13) — frame 443 記録と整合。
- git diff HEAD -- src/ script/ deps.edn = **0 行** (コード変更なし)。
- untracked: status/maturity.md (M) + evidence 作業ファイルのみ、src/ 変更なし。

## 正本引用の再検収 (実測 0 件のため引用維持)

- 正本引用 base = **frame-441 (5db79c6) 実測**: suite **137** / nbb **42 度目** / parity **40 度目** /
  fuzz digest **33 度目** / bench 赤累計 **33 実行目 (falsify-15 で ArityException 消化済み,
  cancelled=0 新規欠陥)** — evidence/2026-09-14-0826-falsify15-verdict-bench-owner-wired-cancel-zero.md,
  evidence/2026-09-14-0020-frame436-genuine-nbb-41st-pass-jvm-cljk-rootcause.md 再読で整合確認。
- frame-442 (0a99c9c) は maturity.md score-table 引用更新のみ (反証 13→15, スコア不変) —
  git show --stat で 1 file 1 insertion 確認済。
- frame-443 (13:15) 以降 13:18 再測で負荷上昇 (94.69/43.96/26.40) → 本枠 17:06 まで高負荷推移と整合。

## スコア / 判定

- 発覚 0 件, 新規 hypothesis 0 件, NEXT 未実測リスト変化なし。
- スコア 7 軸変更なし **3/3/3/3/2/1/1** (反証 15 件, f8–f15 OPEN のまま)。

## 残作業 (不変)

1. validate-i53-halt fix パッケージ (api i53/notional/balance-domain gates + 累算 sum gate 6 site +
   fx/mul-rate 事前界限 + REDUCING 積界限 + rate 上限 + settle-deficit delta clamp)。
2. bench-tape-cancel-arity fix 2 点: (1) bench.clj:112 owner 接続 (f15 実測済) +
   (2) tape 側 cancel 分岐到達性 fix → n ≥ 1M で cancelled > 0 3 回安定 → 再現性 3。
3. fuzz suite 常設化 + nbb-classpath bootstrap (2 段構成) + JVM .cljk runner 対応
   (cognitect runner が .cljk を走査しない — frame-436 根本原因特定済み)。
4. falsify-14 (複数アカウント deficit 合算集計経路, frame-407 登録) — 次 genuine 枠で優先。

## 次枠

次枠番号 = **445** (新規実測時 suite **138** / parity **41** / bench 34 実行目相当;
falsify-14 + bench tape cancel 到達性 + JVM .cljk runner 対応を優先)。
