# falsify-14 — 複数アカウント合算の集計経路 (merkle-sum root sum / commit reserves・shortfall) は個別 leaf が全て可逆でも集計値が非可逆に達し、throw 皆無の cross-runtime root 分岐を起こす (集計 site 第 7 例・累算 overflow クラスの最後の未実測経路確定)

- date: 2026-09-14 02:2x JST (host load gate: 2:19 直測 1-min 10.71 / 5-min 10.01 / 15-min 10.20 全 <20 通過 — frame 437)
- hypothesis: evidence/2026-09-14-0222-falsify14-hypothesis.md (実行前登録, frame-407 登録仮説「複数アカウント deficit 合算の集計経路」の実測; frame-433 static 調査で per-account 累算は settle-deficit `:deficit (fnil + 0)` に帰着済みのため、本測定は残る**単一集計値経路** = commit の merkle-sum root `:sum` / `reserves` / `shortfall` を対象にした)
- runtimes: nbb `kbb --backend sci --classpath "$(kbb --backend sci --classpath '../text/src' script/nbb-classpath.cljk)" evidence/falsify14-nbb-driver.cljk` → falsify14-nbb.{out,err} (exit 0); JVM は **kbb cutover 既知故障のため代替経路**: `kbb -M` は 5 git dep 非解決 (falsify14-jvm.err 初回, frame-427/436 と同型)、ネイティブ `clojure -M` は `.cljk` を classpath 解決できない (frame-436 発見の再確認) — より **src tree を /tmp へ複製し `.cljk`→`.cljc` 改名 + gitlibs から依存 src を手動 flatten** した実測専用 copy で `clojure -Sdeps '{:paths ["."]}' -M -e '(load-file …)'` を実行 (src/ 本体は無変更、byte で同一内容の rename のみ)。exit 0。
- method: `st/new-exchange` → accounts 4/6/8 の collateral を seed → `st/state-root` と `torihiki.commit/reserves` (commit.cljk:127 → merkle-sum root `:sum`) を比較。accumulator/集計経路は無修正の production code。src/ test/ 本体は未変更 (HEAD 8af801db, 本枠で直接実測)。

## Measured — probe aggregate-sum: 3×(2^53+2), 全 leaf 個別可逆 (両 runtime, throw 皆無)

各アカウント collateral = **9007199254740994 (= 2^53+2, 偶数 < 2^54 → double 可逆, falsify-8 規則 (i))**:

| runtime | reserves (= root sum) | state-root |
|---|---|---|
| JVM | **27021597764222982** (long 正確) | 48ca7e613c72e3ad… |
| nbb | **27021597764222984** (丸め +2) | 55a6cf066f177cff… |

**発散**。集計値 3×(2^53+2) = 27021597764222982 は [2^54, 2^55) で ≡2 (mod 4) → double 非可逆。分岐は root のみで運ばれ、per-account leaf bytes は一致する。`reserves` は proof-of-reserves の負債側の数であり、2 validator が異なる reserves を報告する。

## Measured — probe control: 3×2^53 (集計値も可逆) — 両 runtime 完全一致

collateral 3×9007199254740992, 集計 27021597764222976 ≡0 (mod 4) 可逆 → reserves も state-root も **JVM = nbb 完全一致 (8b1ba1bd…)**。分岐の原因が集計経路の存在ではなく**集計値の double 非可逆性**であることを control で確定。

## Verdict

**Confirmed.** 複数アカウント合算の集計経路 (commit.cljk:113 shortfall の `(reduce + 0 (map :sum leaves))` / merkle root `:sum`) は sum 無検査で、個別 leaf が全て i53 域内・double 可逆でも**集計値が非可逆に達した瞬間に throw 皆無で cross-runtime root 分岐**する。これで frame-407 登録仮説と falsify-12 verdict の未実測残項 (a) が確定 — **累算 overflow クラスの site は per-account 累算 6 site (f8–f13) に加え、集計 site (merkle root sum / shortfall) が第 7 経路として確定**。falsify-8 の実測発散規則 (i)(ii) は集計値にもそのまま成立 (control で (ii) 側も再確認)。

## Fix への含意 (NEXT への反映)

- fix パッケージの sum gate スコープに **commit の集計 sum (`shortfall` の reduce / merkle-sum root `:sum`)** を追加: 集計 leaf sum の合計が i53 域外へ抜ける前に validate 層で拒否するか、merkle-sum 側に deterministic な域外挙動を課す。per-account gate のみでは集計 site の分岐が残る (本測定)。
- JVM 呼び出し規約の追加発見: kbb cutover 後、**JVM での .cljk 実測は `kbb -M` (dep 非解決) も `clojure -M` (.cljk 不解決) も直接は不可能** — /tmp rename copy 経路が現時点の唯一の JVM 実測手段 (次の genuine 枠での JVM suite 再実測にも適用可)。cutover 後 JVM suite 呼び出し規約確立の残作業に本経路を記載。

## 関連 (本枠)

- test/parity/bench: 未実測 (budget) — falsify-14 優先。正本引用 frame-413 対 428 部分実測 base 維持 (suite 136 / parity 39 / bench 赤 32 実行目 / fuzz 32 度目)。
