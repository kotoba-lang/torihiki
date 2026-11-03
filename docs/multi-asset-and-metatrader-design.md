# torihiki: FX / 株式 / コモディティ と MetaTrader 接続の設計

Status: proposed（設計のみ。コードは変更しない）
Date: 2026-11-04
Scope: `torihiki`（engine）、`torihiki-node`、`torihiki-terminal`、新規 `torihiki-venue-*`（アダプタ）

## 0. 前提（コードから確認した現状）

| 項目 | 現状 | 根拠 |
|---|---|---|
| 市場の種類 | `:kind` は perp と spot。`:asset` を持つ | `state.cljk` `:list-market` / `:amend-market` |
| 市場パラメータ | `id symbol max-leverage tick lot margin-tiers open-interest-cap`、手数料率 | `clearing.cljk` `market` |
| 不変項目 | `:tick` `:lot` は amend 不可。`:kind`/`:asset` は板に注文がある間は不可 | `:amend-market` |
| 価格 | oracle = 認可 publisher の fresh な提出の中央値（下位中央）、quorum 未満は stale、stale 中は清算停止 | `oracle.cljk` |
| mark | `oracle + clamp(impact-mid - oracle, ±band)` | `mark.cljk` |
| 数値 | i53 整数の固定小数。float 禁止 | `fixed.cljk`, `api.cljk` |
| 時計 | エンジンに時計なし。期限はブロック高 | `state.cljk` `expires` |
| 担保 | `:bridge-authority` のみ入金可。devnet は faucet（無裏付け） | README, decentralization-roadmap |
| 稼働 | devnet、単一運営者。D1–D4 未達の間は分散型と称さない | README |

**市場の営業時間・halt・満期・配当/分割・外部ブローカー接続は、いずれもエンジンに存在しない。**
これが今回追加する範囲。

## 1. 二つの経路を分ける

役割を混ぜない。**経路 A と B は別物で、同時に実装も承認もしない。**

| | 経路 A: on-chain 合成市場 | 経路 B: ブローカー接続（MetaTrader 等） |
|---|---|---|
| 何を取引するか | torihiki 上の perp。原資産は持たない。担保建てで損益のみ精算 | ブローカー口座の実際の FX/CFD/株/商品ポジション |
| 約定 | torihiki の板と清算 | ブローカー（MT サーバ） |
| 価格 | oracle publisher の中央値 | ブローカーの気配 |
| カストディ | torihiki の担保（bridge 経由） | ブローカー。torihiki は預からない |
| 規制・法務 | 他者へ提供するなら金融商品取引業等の検討が必要 | 自己口座の自動化なら比較的軽いが、他者の口座を扱うなら同様 |

どちらも **投資助言ではない**。法務判断（誰に提供するか）は owner 決定で、本書は技術設計のみ。
awai 経済圏の単位（EN / KUMO / YATA / BOT）は金融商品にしない規則（whitepaper）に従い、
これらの市場の担保・建値に使わず、相互換算も作らない。

## 2. 経路 A: 資産クラス対応（engine 変更）

### 2.1 市場メタデータ（state root に含める）

`:list-market` の spec に追加（v ゲート付き。`spot-version` と同じ前例）。

- `:class` `:crypto | :fx | :equity | :commodity | :index`
- `:status` `:active | :reduce-only | :halted`（halt 中は新規注文拒否、cancel は許可）
- `:session` 営業時間の表。**エンジンは時計を持たないので、ブロック高ではなく、認可 publisher が署名する `:session-open` / `:session-close` tx で状態遷移させる**（oracle と同じ quorum 方式。単独 publisher は halt できない）
- `:oracle-max-age` を class ごとに（FX は短く、株は営業時間外は stale が正常）
- `:band` mark の clamp 幅を class ごとに（FX は狭く、商品・株は広め）
- `:margin-tiers` / `:open-interest-cap` は既存機構をそのまま使う（FX は高レバ・小 tier、株・商品は低レバ）

### 2.2 資産クラス別の論点

- **FX**: 金利差（swap）。既存 `torihiki.funding` の premium-index では表現できないので、`:carry-rate`（通貨ペアごとの固定+oracle 提出）を funding の入力に追加。tick/lot は通貨ペアで異なる（JPY ペアは小数 3、他は 5 桁）。i53 に収まるよう price scale を市場ごとに宣言。
- **株式**: 配当・分割・合併。perp では配当相当を funding で調整、分割は **新市場へ移行**（tick/lot が不変のため amend しない）。取引時間外は `:halted`。空売り規制・単元は対象外（perp 扱い）。
- **コモディティ**: 先物の満期と roll。連続足 perp にして oracle 側で roll 規則を公開（規則は市場メタデータの hash で固定）。満期ありは `:settle-price` tx で決済し delist。
- **共通**: 週末・祝日ギャップ。再開時の最初の oracle 価格が前回から `:gap-limit` を超える場合は清算を 1 ブロック保留し、マージンコールの猶予を与える（清算は不可逆、待つ方が安いという oracle.cljk の原則に合わせる）。

### 2.3 oracle publisher の供給源

- MT の気配や各種データ源から **複数の独立 publisher** が提出し、既存の中央値と quorum を使う。単一ブローカー気配を単独の真実にしない。
- 株・商品のデータは利用規約（再配布制限）が問題になる。**利用許諾が取れたソースだけ**を publisher の入力にする（未確認）。

### 2.4 経路 A の前提条件

実資産相当の担保を受ける前に decentralization-roadmap の D1–D4（担保の裏付け、出金、oracle、validator）を満たす。それまでは devnet の合成担保で挙動検証のみ。

## 3. 経路 B: ブローカー接続（Venue port）

### 3.1 配置（Kotoba end-user app 規則に従う）

アプリ origin は静的配信のみ。注文ロジック・状態はユーザー側で実行する。

```
browser app (UI, 戦略, リスク, 状態)
   │  kotoba.app.client-runtime.v1 の operation（each-operation consent）
   ▼
Venue port（共通インタフェース）
   ├─ mt-bridge   : ユーザー環境のローカルエージェント（127.0.0.1）
   │                 └─ MT5/MT4 ターミナル + EA ─→ ブローカー MT サーバ
   ├─ broker-rest : OANDA v20 / cTrader Open API / IBKR / Alpaca 等（直接 HTTPS）
   └─ paper       : 既存 torihiki-paper-bot の方式（実注文なし）
```

- descriptor に service・endpoint origin・data category・各 operation の method/path を宣言。ローカルは `http://127.0.0.1:<port>` を宣言。承認は **操作ごと**。bot の tool 承認はブラウザ consent の代わりにならない。
- app origin へのプロキシ、リモートへの黙示フォールバックは作らない。
- 共有 broker（`kotoba-client-runtime.js`）が現リポジトリに存在するか未確認。**存在・レビュー前は「統合済み」と言わず blocker として記録**し、製品内にコピーしない。

### 3.2 MetaTrader 接続方式の比較

MT5/MT4 には小売向けの公開サーバ API がない（ブローカー向けの Manager/Gateway/Web API はライセンス契約が必要）。以下は私の知識に基づく整理で、**実装前に各公式資料で現行仕様を確認する（未検証）**。

| 方式 | 内容 | 長所 | 短所 |
|---|---|---|---|
| **A. EA ブリッジ（推奨）** | ターミナル上の EA が、ローカルの WebSocket/ZeroMQ/HTTP でエージェントと会話 | 資格情報がユーザー端末から出ない。MT4/MT5 とも可 | ターミナル常駐が必要。macOS は Wine/VM か Windows VPS |
| B. MetaTrader5 Python パッケージ | ローカルターミナルへ IPC | 実装が簡単 | Windows のみ。MT5 のみ |
| C. クラウド仲介（MetaApi 等） | 第三者 SaaS にログイン情報を預ける | ターミナル不要 | **口座資格情報を第三者へ送る**。独立した明示承認が必要。既定では採用しない |
| D. ブローカーの FIX / REST | MT を介さず直接 | 低遅延・堅牢 | ブローカー依存。MT と別実装 |

推奨は A（+ 将来 D を同じ Venue port で追加）。

### 3.3 Venue port（共通契約）

```
quote(symbol)            -> {bid ask ts}            ; 小数は文字列で受け渡し
account()                -> {balance equity margin free-margin currency leverage}
positions() / orders()
place(order)             -> {venue-order-id}        ; idempotent
cancel(venue-order-id)
history(since)
```

- **冪等性**: torihiki 側の注文 cid を MT の `magic`/`comment` に格納。再送しても二重発注しない。確認が取れない timeout は「未確定」とし、再送前に `orders()/history()` で突合する。
- **数値**: MT は double。境界で十進文字列にして i53 固定小数へ変換、丸めは市場の tick/lot に合わせて **安全側**（数量は切り捨て、拒否できるものは拒否）。float を state に入れない。
- **シンボル対応表**: `torihiki market id ↔ venue symbol`、`digits→tick`、`volume-step→lot`、`contract-size`、`min/max lot`、`trade-mode`（取引可否）、ブローカー固有接尾辞（`EURUSD.m` 等）。表は版管理し、起動時に venue 側の仕様と照合、不一致なら当該市場を無効化。
- **注文種別の写像**: market/limit/stop/stop-limit、SL/TP は venue ネイティブを優先（接続断でも保護が残るため）。torihiki の trigger とは二重化しない。
- **突合**: 定期的に venue の positions/equity と内部台帳を比較し、差分が閾値を超えたら halt。

### 3.4 リスク統制（必須。未実装の間は実注文不可）

- 口座単位・市場単位の上限（最大ポジション、1 回の最大数量、日次損失、最大注文頻度）。
- **キルスイッチ**: ユーザー操作と自動条件（接続断、突合差分、スプレッド異常、気配 stale）で全注文停止。停止時は新規のみ禁止し、**決済は許可**。
- 気配の鮮度・スプレッド・ギャップ検査（週末再開など）。
- 委任鍵: 既存 `:authorize-agent`（取引は可、出金は不可）の考え方を踏襲。Venue では「発注可・資金移動不可」の権限だけをエージェントに与える。
- 実注文は **操作ごとに表示→承認**（銘柄・売買・数量・概算約定価格・口座）。自動売買の場合は上限つきの有期ポリシーを人間が設定し、範囲外は都度承認。

## 4. 経路 B と torihiki の関係（任意の接続点）

- **記録**: 約定・突合結果を torihiki chain に `:anchor`（既存）で署名付き receipt として残す。ブローカー約定の証明にはならない（ブローカー明細が正本）と明記。
- **価格提供**: MT 気配を経路 A の oracle publisher の一つとして使える（§2.3）。
- **ヘッジ**: 「経路 A のユーザー建玉を経路 B でヘッジする」運営形態は、運営者が他者の相手方になるため規制・資金管理の別問題。本書の対象外で、owner と法務の判断後に別 ADR とする。

## 5. フェーズ

| Phase | 内容 | 完了条件（検証可能なもの） |
|---|---|---|
| P0 | paper のみ。読み取り専用の気配取得（既存 paper bot 流用） | 実注文経路が存在しないこと |
| P1 | Venue port と fake venue、シンボル対応表、冪等性/突合のテスト | fake に対する property test が通る |
| P2 | MT5 EA ブリッジ（読み取り: quote/account/positions）。**デモ口座のみ** | デモ口座で 24h 突合差分 0、切断復帰試験 |
| P3 | デモ口座へ発注（consent・上限・キルスイッチ込み） | 二重発注なし、timeout 後の突合が正しいこと |
| P4 | 経路 A: `:class/:status/:session` を engine に追加（版ゲート）、devnet で FX・株・商品を合成担保で | 全 replica が同一 state root、halt/再開/ギャップ試験、既存テスト無回帰 |
| P5 | 実口座・実担保 | **owner 決定 + 法務確認 + D1–D4**。本書では承認しない |

各 Phase 完了時に Linear・commit・push・PR を更新する（AGENTS.md の運用）。

## 6. 未確定・リスク

- MT の現行仕様、macOS での運用、EA 配布規約、各データ源の再配布条件は **未検証**。
- engine の版ゲートは state root を変える。既存 devnet は停止中（roadmap 参照）で、deploy は DO が新コードを拾わない問題が既知。公開は別承認。
- 株・商品の oracle は営業時間外 stale が通常状態で、既存の「stale = 清算停止」と整合させる必要がある（§2.2 のギャップ保留）。
- 一般公開・他者提供の可否は技術ではなく owner と法務の判断。
- 競合する既存市場（Hyperliquid の builder 市場等）との差別化は本書で扱わない。

## 7. 実装状況

- **P1（JUN-807）**: `src/torihiki/venue.cljk`（契約・十進境界・シンボル対応表）、`venue_fake.cljk`、`venue_router.cljk`（冪等性・キルスイッチ・上限・突合）と `test/torihiki/venue_test.cljk`。ネットワーク・実 venue アダプタは存在しない。
  - 検証: `kbb -M:test -n torihiki.venue-test` が 23 テスト 116 アサーションで通る。復旧経路を無効化した負の対照では 8 件失敗することを確認（2026-11-04）。
  - MT のコメント長上限（31）は未検証の想定値（`torihiki.venue/max-cid-len`）。
- **P2（JUN-814）**: 読み取り専用 MT5 ブリッジのソース・フレーム仕様・純粋アダプタ（`venue_mt.cljk`）まで。仕様は `docs/mt5-readonly-bridge.md`。EA は未コンパイル・未実機、loopback 待受は未実装。P2 の完了条件（デモ口座 24h 突合差分 0）は**未達**。
- **Kotoba 移植・段 1（JUN-816）**: owner 指示により venue コードは canonical `.kotoba`（Amu selfhost）で持つ。`kotoba/torihiki/venue.kotoba`（十進境界・シンボル対応表・注文変換）と `venue_test.kotoba`、実行は `AMU_HOME=<amu> script/kotoba-venue-test.sh`（JVM・nbb 不使用）。cljk 版（P1/P2）は参照 oracle として残す。
  - 検証: `amu check` ok、全ケース 0 失敗、CONTROL 変種はちょうど 1 失敗（2026-11-04、amu a4c16e7c4）。cljk 側と同じケースを手で移したもので、機械的な突き合わせではない。
  - 計測で分かった制約: `string-code-point-at` は UTF-8 **バイト**オフセットを取り、多バイト文字の途中を指すと SIGTRAP で無出力終了する。走査は読んだ文字の幅だけ進める。`result-match-of` + ラムダは `amu run` が EABI で拒否するため option と番兵で書いた。
  - 段 2 以降（MT フレーム reducer の行テキスト化、router/fake、loopback 待受）は未着手。
- **Kotoba 移植・段 2（JUN-816）**: MT フレームを v2 の 1 行テキストにし、reducer と照会を `kotoba/torihiki/venue_mt.kotoba` へ移植、EA も v2 に追随。状態は行テキスト（`:document` は整数が使えない）。`script/kotoba-venue-test.sh` が venue と venue_mt の 2 本を実行し、各 CONTROL 変種がちょうど 1 件失敗することを要求する。負の対照（デモ判定・鮮度判定・リスト外 POS の判定を無効化）で 13 件失敗することを確認（2026-11-04）。cljk の `venue_mt`（v1 JSON）は v1 の oracle として残るが、EA はもう v1 を話さない。router/fake（段 3）・loopback 待受（段 4）は未着手。
