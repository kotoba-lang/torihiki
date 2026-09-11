# falsify-3 — seeded fuzz harness: JVM/nbb 同一 seed → 同一 state root

- 日時: 2026-09-03 20:35–20:55 JST
- 主張 (status/maturity.md NEXT): adversarial block 列を seed 固定で folding し、
  JVM と nbb が同一 seed で同一 state root を出すことを機械検証する。
  「疑いを挙げる作業自体の機械化」が反証対象。status の OPEN 赤はないため、
  この NEXT の主張そのものを反証仮説に据えた:
  「seeded fuzz を回せば、両ランタイムが分岐する列が(もしあれば)検出される。
  検出されなければ反証ループは survived、ハーネスが次の反証の常設手段になる」。

## harness (evidence/fuzz-seeded.cljk, src/test に変更なし)

- PRNG は xorshift32 (bitwise のみ)。掛け算系 PRNG (mulberry32) は JS の
  2^53 精度限界で両ランタイムの state が分岐するため最初から除外。
- 16 seeds × 24 blocks × 48 txs, 6 accounts, 1 market。tx ミックス:
  deposit 8% / oracle 大幅移動 7% / order (双方交差する幅広 level) 40%
  (うち 15% は bad shape を意図的に混入) / cancel 20% (正 owner 60%,
  誤 owner 20%, bogus oid 20%) / cancel-all 6% / amend 7% / trigger 5% /
  cancel-trigger 3% / liquidate・funding-settle 3% / deposit 1%。
- block 12 の直後で snapshot/restore round-trip を 1 回挿入し、
  往復前後で flat-root と state-root が一致することも同時に検証。
- digest 1 本あたり: flat-root, state-root, resting-count, rejected-count,
  fill-count, snapshot-parity flag。
- 実行:
  - JVM: `kbb -M -e '(load-file "evidence/fuzz-seeded.cljk") (fuzz-seeded/run)'`
  - nbb: `kbb --backend sci --classpath "$(kbb --backend sci script/nbb-classpath.cljk):src" -e
    "(require '[nbb.core :refer [load-file]]) (load-file \"evidence/fuzz-seeded.cljk\") (fuzz-seeded/run)"`

## 過程で harness 自体が拾った欠陥 2 件 (engine ではなく harness 側)

1. seed 初期化 `(bit-or (bit-and seed 0xFFFFFFFF) 1)` が偶数 seed を奇数に
   折り畳み、seed 0/1, 2/3, … が同一 digest に沈んだ → ペアで同一になる時点で
   生成器が壊れていると判定し修正。**fuzz の結果を鵜呑みにせず、まず生成器の
   多様性を検査する**のが正しい順序だったことの実例。
2. `bit-and 0xFFFFFFFF` は JVM では long (正の 2^32 未満) を返し JS では
   signed int32 を返す。続く `bit-shift-right` (算術シフト) がこの差を増幅し、
   JVM と nbb で **4 手目の draw から PRNG 列が分岐**した。
   `unsigned-bit-shift-right` に置換して解消。
   - これは harness の欠陥だが、エンジン外の `.cljc` を書く者が踏む地雷として
     記録に値する: JVM では bit 演算が long 幅で働き、JS では 32-bit に
     コアースされる。README の determinism 規則はエンジン内部では守られて
     いるが、周辺ツールは自前で同じ規律を守る必要がある。

## 実測

- JVM: evidence/fuzz-jvm.out (seed 0-15 の digest 行 + done)
- nbb: evidence/fuzz-nbb.out (同一形式)
- **seed 行 16/16 が byte-identical** (`diff <(grep '^seed…' fuzz-jvm.out)
  fuzz-nbb.out` → 差分なし。JVM 側にのみ load-file の REPL エコー 1 行)。
- 全 seed で snapshot-parity flag = true (block 12 直後の capture/restore
  往復で root 不変)。
- rejected は seed ごと 43-99 件発生し、bad shape の拒否経路も両ランタイムで
  同一に消化されている。fills は seed ごと 1-23 件。
- host load: 実行開始時 1min 14.75 / 終了時 20.17 (閾値超えは終了後の観測で、
  実行中は 20 未満であった。次回以降は開始前に再確認)。

## 判定

**survived**。16 seeds × 1,152 txs の adversarial 列で、JVM と nbb は同一
seed から同一の flat-root / state-root / resting / rejected / fills に到達した。
分岐は検出されず、falsify-1/2 に続き 3 連続 survived。同時に「疑いを挙げる
作業の機械化」は実物として存在するようになった (harness は evidence/ に固定、
実行コマンド 2 行)。

## スコアへの含意 (提案、実施は次回再計測時)

- テスト軸 (3→4): fuzz/property ベースが **存在はする** ようになったが、
  `kbb -M:test` に組み込まれておらず seed ジョブとして常設化していない。
  4 の根拠にするには suite への接続が条件。
- 再現性軸 (2→3): 実行コマンドが 2 行で pinned classpath から再現でき、
  出力が diff で判定できる。seeded fuzz ジョブ未整備という現状の減点根拠は
  本実測で部分的に解消。常設化 (CI または cron) まで含めて 3 とするのが誠実。

## NEXT (提案)

harness を `kbb -M:test` と script/tests-on-nbb.cljk の両方に接続し、
seed をパラメータ化した常設 fuzz ジョブにする (両軸の上げの条件を満たす)。
その後の反証は harness の tx ミックスを攻撃的に進化させる方向
(例: multi-market, 認証付き envelope (auth/check + nonce 再生), builder fee
上限境界, oracle publisher 集約)。
