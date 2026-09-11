# falsify-1: account-binding race (Byzantine leader front-runs the pending binding)

- 日時: 2026-09-03 19:3x JST (host load ~10-19, 実行可)
- runtime: JVM (clojure 1.12.0), repo pin 現状
- harness: `evidence/falsify-1-race.cljk` (実行: `kbb -M -e '(load-file "evidence/falsify-1-race.cljk")'`)
- 前例: engi `torihiki-on-engi` harness (engi/README.md:160-205) — account 1 = -50, owner 34 件 :wrong-key
- repo コード変更: なし (src/ test/ 無編集。evidence/ スクリプトのみ)

## hypothesis

README + auth.cljc:42-56 の主張を反証しようとした:
**derive-fn なしでは、ブロック proposer (Byzantine leader) が未バインド id の
pending binding を先取りでき、所有者は自分の id で永久に :wrong-key になる。**
再現しなければ refuted、再現すれば derive-fn 必須化が正しいことの反証として survived。

## 実測

- sign/verify は test と同形のスタブ (payload+key)。engine seam は `st/apply-block`
  (auth はその中で検査される — engi harness と同一位置)。
- leader の block 1: `[thief-claim (id 1, key-thief 署名, sell 5@1010), owner deposit+4 orders]`

| scenario | rejected | binding id 1 | owner collateral |
|---|---|---|---|
| A: derive-fn なし | {:wrong-key 5} — owner の全 tx | **"key-thief"** | nil |
| A 続き: owner が次 block で nonce 更新して再送 | {:wrong-key 3} — 永続 | "key-thief" | nil |
| B: derive-account あり (owner→1, thief→999) | {:not-your-account 1} — thief の 1 件のみ | **"key-owner"** | 1000 |
| C: A を 3 回再実行 | IDENTICAL (replica parity) | — | — |

## verdict: **survived** (race は現在のコードで再現する)

- derive-fn なしでは 1 block で id 1 が leader の鍵に束縛され、owner は以後の
  block でも nonce を更新しても :wrong-key — silent・permanent。engi 前例と同形。
- derive-fn ありでは thief の 1 件だけが可視の :not-your-account で拒否され、
  owner は全 tx を適用。README の「collision は可視 / race は silent・永久」
  の比較も実測通り。
- したがって「derive-fn 必須化」は反証されなかった — 主張は実装と一致する。
  コンセンサス未接続の L1 でも、race は engine の認証層だけで再現する
  (proposer の順序操作だけで起きる)。

## 残された観測

- rejected エントリには :account が記録されない (:index :tx :reason のみ) —
  反証 harness が被害者の拒否を数えるには envelope 側で reconstruct する必要が
  あった。rejected に account を載せるかどうかは spec 判断 (state root に入る)。
- falsify-2 (snapshot/restore の free-slot / oid 一貫性) は未実施 — 次回。
