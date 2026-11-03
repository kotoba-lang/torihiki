# MT5 読み取り専用ブリッジ（P2）

Status: ソースと純粋アダプタのみ。**実機の MT5 では未コンパイル・未実行。**
設計: `docs/multi-asset-and-metatrader-design.md` §3.2 方式 A、§5 P2。Linear JUN-814。

## 構成

```
MT5 ターミナル（ユーザー環境、デモ口座）
  └─ EA mt5/TorihikiReadOnlyBridge.mq5
        │  loopback TCP、1 行 1 JSON（EA から外向きの一方通行）
        ▼
  loopback 待受（P2 では未実装）→ フレームを map に parse
        ▼
  torihiki.venue-mt  → Venue port（P1）→ bind-markets / reconcile!
```

EA は接続して送るだけで、ソケットから読まない。コマンドを受け取る経路がなく、発注関数も含まない。
アダプタの `:place` `:cancel` `:orders` `:history` は `{:error :read-only}`。

## 守らせていること

| 項目 | どこで |
|---|---|
| デモ口座以外では EA が起動しない（`ACCOUNT_TRADE_MODE_DEMO`） | EA `OnInit` |
| ホストは `127.0.0.1` / `localhost` のみ | EA `OnInit` |
| `hello` の `mode` が `"demo"` 以外ならアダプタがロック（以後すべて `:locked`） | `venue-mt/ingest` |
| 発注・ソケット読み取り・ファイル・DLL import の語が EA に無い | `script/check-ea-readonly.sh`（文字列検査。証明ではない） |
| 不正フレームは捨てて数える（状態不変、直近 20 件） | `ingest` |
| 受信時刻から 5 秒超は `:stale`（EA の時刻は比較しない） | `venue` |
| positions は `pos-begin`…`pos-end` で原子的に差し替え。volume step の倍数でなければ全体を拒否 | `ingest` |
| 接続ごとに `n` が単調増加。新しい `hello` で前の接続の状態を全消去 | `ingest` |

## フレーム v1

UTF-8、1 行 1 オブジェクト、価格・数量・金額はすべて **十進文字列**（`DoubleToString`）。
`hello` 以外は `n`（接続内で単調増加の整数）を持つ。

```
{"t":"hello","v":1,"n":0,"mode":"demo"}
{"t":"sym","n":1,"s":"EURUSD","digits":5,"step":"0.01","min":"0.01","max":"100.00","tick":"0.00001","trade":"full"}
{"t":"tick","n":2,"s":"EURUSD","bid":"1.08490","ask":"1.08500"}
{"t":"acct","n":3,"balance":"10000.00","equity":"10010.50","margin":"0.00","free":"10010.50","ccy":"USD"}
{"t":"pos-begin","n":4}
{"t":"pos","n":5,"s":"EURUSD","type":"buy","vol":"0.30"}
{"t":"pos-end","n":6}
```

`trade` は `full | longonly | shortonly | closeonly | disabled`。`full` のときだけ `trade-allowed?` が真。
口座番号など口座を特定する値は送らない。

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
- **loopback TCP の待受は未実装。** 実行系の選定が先：JVM と nbb は使わない方針のため、Node の小さな待受を置くのか、kotoba の native で書くのかを決める必要がある。アダプタは parse 済み map を受け取る純粋関数として切ってあるので、待受側は「行を読んで JSON を parse して `ingest` に渡す」だけでよい。
- 受け入れ基準「デモ口座で 24 時間、突合差分 0／切断復帰試験」は**未実施**。
- EA の配布・利用規約（MQL5 Market 以外で自分で使う限りの扱い）は未確認。
