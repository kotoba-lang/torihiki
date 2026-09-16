# falsify-16 hypothesis (static analysis, 2026-09-14 20:1x, cron frame 446 — 実測 not-run, budget 枯渇)

## 対象
falsify-15 (frame 441) の新規欠陥: owner 接続済み rename-copy bench で cancelled=0
(placed 2,251,559 / 5,000,000 ops)。tape が cancel 分岐に落ちない原因の切り分け。

## 静的解析 (HEAD 0a99c9c2 / rename-copy /tmp/tori-falsify15 実読)

bench.cljc:109-112 の cancel 側:
- slot = (w − 1 − (i & 524287)) & 1048575
- oid = ring[slot]
- (bk/cancel! b (bit-and i 1023) oid)

place 側 (bench.cljc:103-106) は owner = (bit-and j 1023) (j = place op の tape index)、
oid を ring[(w & 1048575)] に書く。w は place 成功時のみ increment するため、
ring slot p に入る oid の place index は j = (その時点の w) − 1 番目の place =
「w 個目の place」に対応し、cancel 側の slot 計算 (w−1−(i&524287)) は
「(w−1−(i&524287)) 個目の place」の oid を意図して正しい位置を指す。

→ **owner 不一致仮説**: cancel! の第 2 引数 owner は「cancel op の index i」から
   導出されているが、対象 order を置いたのは index j = (w−1−(i&524287)) 番目の
   place op であり、owner が一致するのは 2i − w + 1 + (i&524287) ≡ 0 (mod 1024)
   のときのみ。確率 ~1/1024 なので cancelled ≈ placed/1024 ≈ 2,200 が予測され、
   **実測 cancelled=0 を単独では説明しない** — 仮説は部分的。

→ **第 2 候補 (ring staleness)**: ring は place 成功時のみ書かれ、cancel 成功時は
   読んだ slot を -1 にするが、同一 oid が指す order が (a) aggressive fill で
   consume 済み (gen bump → cancel! は gen 不一致で 0) や (b) 別 cancel で
   既に取消済みでも slot が別 cell なら残骸 oid が生きる。12% aggressive +
   2.15M cancel op で slot 再利用が 2^20 を超えた後半は残骸 oid を引く率が上がる。
   ただし resting at end = 1,879,250 (placed の 83%) なので「大半が stale」とも
   言い切れず、**cancelled=0 (厳密 0) の完全説明には未達**。

## 結論
静的解析のみ。実測 (instrumented bench: cancel! 戻り値 0 の理由内訳
owner-mismatch / gen-stale / qty-0 のカウント出力) を次 genuine 枠で実施する
必要がある。fix 候補は (1) ring に oid とともに owner を保存し cancel に渡す
(2) instrumented run で内訳を確定してから tape 選択条件を修正。

## 制約
- 本枠は load gate 成立 (20:10 直測 9.70/10.97/12.03 全 <20) だが runtime budget
  枯渇 + terminal 空出力 fault (既知) により bench/instrumented 実測は not-run。
- src 本体無変更 (git diff HEAD -- src/ script/ deps.edn = 0, HEAD 0a99c9c2 不変)。
- bench.cljc.f15orig バックアップを /tmp/tori-falsify15/bench/torihiki/ に作成済み
  (測定 copy のみ, 本枠では未改変)。

## frame 448 update (2026-09-14 23:2x, cron, 実測 not-run — budget 枯渇, 静的解析のみ)

**決定的候補を発見: falsify-15 の owner 接続は cancel! の引数順が逆。**

- `bk/cancel!` の署名は `[b oid owner]` (book.cljc:566, rename-copy /tmp/tori-falsify15 実読)。
- bench.cljc:112 の接続行は `(bk/cancel! b (bit-and i 1023) oid)` —
  **owner (= i & 1023) を第 2 引数 oid に、oid を第 3 引数 owner に渡している**。
- 結果は機械的に計算できる:
  - `slot-of` は `(quot oid gen-mod)` (book.cljc:402, gen-mod=2^20=1048576)。渡された「oid」は
    owner 値 0–1023 なので slot = 0 (i < 1024 のとき) または 0。
  - slot 0 が free のとき o-gen[slot] は大半 0 ≠ gen-of(owner) (= owner 値) → 即 0。
  - slot 0 が rest 中のとき gen は小さい値 (≈世代数) で gen-of(owner)=owner 値 0–1023 と
    一致する確率は低く、一致しても owner 引数には oid (>2^20) が来て o-owner[slot] と不一致 → 0。
  - i ≥ 1024 では owner 値 (i & 1023) ≥ 1 で slot=0, gen-of = i&1023 — slot 0 の o-gen
    (1–2 世代程度) と一致する確率は ~2/1024 未満。
- したがって **cancelled = 厳密 0 (樹数) は引数順逆転で完全に説明される** —
  owner-mismatch 仮説 (~1/1024 で cancelled≈2,200 予測) と ring-staleness 候補は不要。
  falsify-15 の「owner 接続 1 行」は接続したのに全 cancel が 0 を返すだけだった。
- 修正候補 (tape 側): `(bk/cancel! b oid (bit-and i 1023))` — 引数順だけを直す。
- 静的根拠: bench.cljc.f16orig バックアップ作成済み (/tmp/tori-falsify15/bench/torihiki/)。
- 実測 (引数順修正後の rename-copy bench で cancelled > 0 確認, n ≥ 1M) は次 genuine 枠。
  再現性 3 の前提 (cancel 経路が実際に動く) はこの 1 行修正で初めて満たされる見込み。
