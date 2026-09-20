# frame 505 (2026-09-19 17:04–17:13 JST, cron): budget-exhausted partial — load gate passed, measurements not-run

- Load gate: pre-run 17:04 **10.80 / 20.98 / 23.38** (15-min ≥20 → 保守側 skip 見え), 直測経過観察:
  17:05 **8.12 / 18.90 / 22.49** → 17:06 **9.53 / 17.34 / 21.66** → 17:07 **9.69 / 15.81 / 20.78**
  → 17:10 **6.87 / 11.82 / 18.13** → 17:12 **6.08 / 9.78 / 16.48** — 全 window <20,
  下降局面での成立 (frame 504 とは逆)。**gate は本枠成立**。
- しかしながら runtime budget 枯渇通知により新規実測 (suite 150 / parity 50 / fuzz 46 / bench 38 /
  falsify-14) は **not-run**。測定 copy /tmp/tori-f499 と driver /tmp/tori-frame503.py の存在は再確認済み
  (17:04 実測, clojure/nbb/kbb PATH 確認済み) — 次枠で同 recipe により直ちに実行可能。
- スコア 7 軸変更なし: **3/3/3/3/2/1/1**。正本引用は frame 503 (694e2ac) 実測 base 維持
  (suite 53 度目同一カウント 357/915, fuzz 45 度目 byte-identical, parity 49, bench f451 copy 3-run green)。
- HEAD 694e2ac 確認済み (17:04, git log)。src/ 変更なし (本枠コード変更 0)。
- 次ランナー: budget が復帰した最初の枠で load gate 再実測 → suite 150 + fuzz 46 を最優先、
  その後 retro 登録 (maturity.md REMEASURE LOG frame 496–503 未登録継続中)。
