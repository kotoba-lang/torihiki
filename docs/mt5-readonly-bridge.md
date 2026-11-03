# MT5 読み取り専用ブリッジ（P2）

Status: ソースと純粋アダプタのみ。**実機の MT5 では未コンパイル・未実行。** 正の実装は Kotoba（JUN-816）。実行は `AMU_HOME=<amu> script/kotoba-venue-test.sh`。
設計: `docs/multi-asset-and-metatrader-design.md` §3.2 方式 A、§5 P2。Linear JUN-814。

## 構成

```
MT5 ターミナル（ユーザー環境、デモ口座）
  └─ EA mt5/TorihikiReadOnlyBridge.mq5
        │  loopback TCP、1 行 1 フレーム（v2 テキスト。EA から外向きの一方通行）
        ▼
  loopback 待受（未実装）→ 1 行ずつ ingest に渡す
        ▼
  kotoba/torihiki/venue_mt.kotoba（Kotoba、正）→ Venue port
  src/torihiki/venue_mt.cljk（v1 JSON 用の参照 oracle。EA は v2 に移行済み）
```

EA は接続して送るだけで、ソケットから読まない。コマンドを受け取る経路がなく、発注関数も含まない。
アダプタの `:place` `:cancel` `:orders` `:history` は `{:error :read-only}`。

## 守らせていること

| 項目 | どこで |
|---|---|
| デモ口座以外では EA が起動しない（`ACCOUNT_TRADE_MODE_DEMO`） | EA `OnInit` |
| ホストは `127.0.0.1` / `localhost` のみ | EA `OnInit` |
| `HELLO` の mode が `demo` 以外ならアダプタがロック（以後すべて `ERR locked`） | `venue_mt.kotoba` `ingest` |
| 発注・ソケット読み取り・ファイル・DLL import の語が EA に無い | `script/check-ea-readonly.sh`（文字列検査。証明ではない） |
| 不正フレームは捨てて数える（状態不変） | `ingest` |
| 受信時刻から 5 秒超は `ERR stale`（EA の時刻は比較しない） | `venue` |
| positions は `pos-begin`…`pos-end` で原子的に差し替え。volume step の倍数でなければ全体を拒否 | `ingest` |
| 接続ごとに `n` が単調増加。新しい `hello` で前の接続の状態を全消去 | `ingest` |

## フレーム v2

JSON をやめ、Amu の GPU プロトコルと同じ 1 行テキストにした（JSON の読み取りは Kotoba で書くとフレームの価値に見合わない量になるため）。
ASCII の印字可能文字と単一の空白だけ。先頭・末尾・連続の空白、非 ASCII、タブはその行ごと `bad-line` で捨てる。
価格・数量・金額はすべて十進テキスト（`DoubleToString`）。`HELLO` 以外は先頭に `n`（接続内で単調増加の整数）。

```
HELLO 2 demo
1 SYM EURUSD 5 0.01 0.01 100.00 0.00001 full
2 TICK EURUSD 1.08490 1.08500
3 ACCT 10000.00 10010.50 0.00 10010.50 USD
4 POS-BEGIN
5 POS EURUSD buy 0.30
6 POS-END
```

| フレーム | 欄 |
|---|---|
| `HELLO 2 <mode>` | mode が `demo` 以外なら以後ロック。新しい `HELLO` は新しい接続で、前の状態を全消去 |
| `n SYM sym digits step min max tick trade` | `trade` は `full\|longonly\|shortonly\|closeonly\|disabled`。`full` だけ `trade-allowed?` が真 |
| `n TICK sym bid ask` | bid, ask > 0 かつ ask ≥ bid |
| `n ACCT balance equity margin free ccy` | ccy は英数字 1〜8 文字 |
| `n POS-BEGIN` … `n POS sym buy\|sell vol` … `n POS-END` | `POS-END` で原子的に差し替え |

口座番号など口座を特定する値は送らない。照会は `OK ...` / `ERR <reason>` の行で答える
（`ERR locked` `ERR no-data` `ERR stale` `ERR no-quote`）。
cljk 版との違い：拒否は件数と直近の理由だけ保持（cljk は直近 20 件）、positions は銘柄の通知順（cljk は名前順）、
アダプタには `place` 等の関数自体が無い（cljk は `{:error :read-only}` を返していた）。

## 動かす手順（実機検証用。未実施）

1. MT5 のデモ口座を用意する（ブローカーのデモ。macOS は Windows 環境か Wine/VM が必要）。
2. `mt5/TorihikiReadOnlyBridge.mq5` を MetaEditor でコンパイルする。**エラーが出る可能性がある**（下記）。
3. ターミナルの「ツール > オプション > Expert Advisors」で `127.0.0.1` を許可リストに入れ、アルゴ取引を許可する。
4. loopback 待受（未実装）を起動してから EA をチャートに付ける。

## 未検証・未実装

- **コンパイルしていない。** MQL5 の構文・関数シグネチャは公式ドキュメントで次を確認した範囲で書いた：ソケット関数が EA から使えること（許可アドレス設定が要る）、`ACCOUNT_TRADE_MODE_DEMO`、`SYMBOL_*` と `ACCOUNT_*` の定数と getter、`MqlTick` の項目。`PositionsTotal` / `PositionGetTicket`、`SocketSend` の引数型、`StringToCharArray` の戻り値は**未確認**（記憶による）。
- 許可リストがソケットの `ホスト:ポート` 単位か、ホスト単位か、`127.0.0.1` が使えるかは**未確認**。
- ヘッジ口座で同一銘柄に複数ポジションが立つ挙動、ネッティング口座の符号の扱いは実機で確認する。
- 銘柄名のブローカー固有接尾辞（`EURUSD.m` 等）は `sym?` の文字種（英数字と `._#-`）で通る前提。
- **loopback TCP の待受は未実装。** 実装は Kotoba（Amu selfhost）で書く方針。Kotoba のプログラムはホストのファイル・プロセス権限を持たないので、ソケットを扱える capability があるかを先に確認する（JUN-816 段 4）。アダプタは「1 行を受け取って新しい状態を返す」関数なので、待受側は行を読んで `ingest` に渡すだけでよい。
- 受け入れ基準「デモ口座で 24 時間、突合差分 0／切断復帰試験」は**未実施**。
- EA の配布・利用規約（MQL5 Market 以外で自分で使う限りの扱い）は未確認。
