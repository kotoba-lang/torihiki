# 2026-09-11 0904 frame 416 — frame-415 orphaned runner evidence 収納 + 本枠負荷 gate skip (no code changes, cron)

## 負荷 gate (本枠)

- HOST LOAD 9:04 (pre-run): 1-min **37.14** / 5-min 30.64 / 15-min 23.63 — 全 window ≥20 で「全 <20」不成立のため **本枠新規実測 not-run**。
- terminal backend 空出力 fault (既知高負荷型): `bash ~/.hermes/scripts/torihiki_state.sh` / `uptime` / `git` 直叩き 4 回すべて exit 0 / stdout 空 — HEAD 直接検収は not-run、コード不変は frame-414 (HEAD d2b0407) の引用契約で踏襲。
- uptime 迂回実測は terminal 空出力のため不成立 — 負荷判定は pre-run HOST LOAD 値のみに依拠。

## frame-415 orphaned runner evidence 収納 (IN-FLIGHT 実検収)

- `evidence/test-jvm-415.out`: **Ran 357 tests containing 915 assertions. 0 failures, 0 errors. JVM_EXIT=0**
- `evidence/test-nbb-415.out`: **357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass** (`test-nbb-415.err` = 0 bytes)
- → 両 runtime 同一カウント **40 度目実測**, **suite 137** として本枠で正本採用 (frame-413 の 412 収納先例どおり)。
- `evidence/bench-415.err` (実検収): 既知赤 bench-tape-cancel-arity **33 実行目** — `ArityException at torihiki.bench/run-tape (bench.clj:112). Wrong number of args (2) passed to: torihiki.book/cancel!`。再現性 2 のまま。
- `evidence/fuzz-jvm-412.out` = `FUZZ_JVM_EXIT=1` (frame-413 確定どおり呼び出し不備で無効) — fuzz digest 消化カウントは **32 度目のまま変化なし**。
- falsify-14 (複数アカウント deficit 合算集計経路) の evidence は本枠 IN-FLIGHT に無し — **未実測のまま次 genuine 枠へ繰越**。

## 判定

- 発覚 0 件, 新規 hypothesis なし。
- スコア 7 軸すべて変更なし **3/3/3/3/2/1/1** (反証実施 13 件, falsify-8〜13 累算 overflow クラス OPEN のまま)。
- 残作業不変: validate-i53-halt fix パッケージ + bench 3 箇所 (bench.clj:112 / probe.clj:31 / curve.clj:21) + fuzz 常設化 + fuzz driver 起動規約 harness 統一 + nbb-classpath bootstrap fix (2 段構成)。
- 次ランナー: 負荷 <20 (全 window) 突入後最初の枠で全 gate 再実測 (新規実測時 suite **138** / bench-tape-cancel-arity **34 実行目** 相当; **falsify-14 実測優先**)。
- 次枠番号 = **417**。
