# frame (2026-10-13 00:2x) host load not-run

- Pre-run load: 10.29 / 16.10 / 22.01 (1/5/15min, 15min > 20)
- Wait + recheck 1: 10.79 / 15.49 / 21.51 (15min > 20)
- Wait + recheck 2 (+4min): 18.32 / 18.95 / 21.55 (5min 上昇, 15min > 20 — host 側で別 load 発生)
- Wait + recheck 3 (+5min): 11.54 / 20.43 / 21.98 (5min/15min とも > 20)

Loadgate FAIL (15min が 3 回測定すべて > 20, 5min も 2 回 > 20) → suite/parity/fuzz/bench 実行せず not-run。

- コード diff: 0 (本枠で touch なし)
- IN-FLIGHT (uncommitted evidence files) は既存のまま、本枠で追記なし以外変更なし
- NEXT: 次枠 genuine 条件 (持続 load <20) 到達時に /tmp/tori-c11 rename-copy base で
  suite / parity / fuzz / bench 全再計測。直近の blocker は 2026-10-11 18:15 枠の
  kotoba.lang.edn compile error "Unable to resolve symbol: escape-controls" — その解明が
  suite 再実行の前提。reboot 後 /tmp/tori-c11 が消失している可能性に注意 (要存在確認)。
