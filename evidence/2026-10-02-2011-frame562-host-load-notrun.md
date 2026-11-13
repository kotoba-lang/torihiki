# 2026-10-02 20:11 frame562 host-load not-run

- Host: main-2.local (hostname 確認済み, 誤ルートなし)
- Load: 167.89 / 167.65 / 174.68 — gate (>20) を大きく超過
- Decision: iteration を実行せず not-run 記録のみ。hypothesis/verdict の計測は低負荷枠に持ち越し。
- State: NEXT ① 1ceb357 再起点 (IN-FLIGHT fixed.cljk +36 / slab.cljk の landing/revert 確認が先) は変更せず維持。
- Evidence rule: host load > 20 → not-run evidence only (本ファイル)。
