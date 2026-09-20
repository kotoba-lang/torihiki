# frame 513 (2026-09-20 09:0x, cron) — genuine: suite 155 both runtimes PASS (same-count 59th, 357/915 0F/0E) / parity 52nd both runtimes PASS (canonical roots) / fuzz digest 52nd byte-identical (core lines, raw diff = echo+exit port lines only) / bench known-red repr (copy bench.cljc:112 2-arg, repo fix unlanded, BENCH_EXIT=1). Scores unchanged 3/3/3/3/2/1/1 (falsifications 16, f8-f17 OPEN). No code changes.

- load gate passed: pre-run 09:04 direct 12.89/10.67/10.87 → direct 09:06 13.24/11.40/11.13 all windows <20 (2 measurement agreement; declining/flat).
- HEAD 6552005c7cc96b02b62c5613974b3ccf0de5e5d6 = frame-512 ledger commit (git log -1 verified); git diff 694e2ac7..6552005c -- src/ script/ deps.edn = 0 bytes → landing ledger-only, code unchanged → canonical citations valid.
- working tree: all 237 porcelain rows untracked evidence only (frames 467-512 records uncommitted); status/maturity.md clean/committed.
- measurement copy /tmp/tori-f506 reused (f451 tmp-volatile vanished): script 5 files incl kotoba-parity.cljc + run-kotoba-wasm.mjs, kotoba/ intact, evidence/fuzz-seeded.{cljc,cljk} + fuzz-nbb-driver.cljc present → suite/parity/fuzz slots all feasible. deps.edn/CP conventions per frame-506/509 notes.
- OPEN red 8 citation sites re-read at 6552005c: api.cljk:68 (:order qty integer?/pos? only, no i53/notional cap), api.cljk:202 (:deposit amount integer?/pos? only), clearing.cljk:711 (:deficit (fnil + 0) unchecked sum), clearing.cljk:726 (:collateral (fnil + 0) unchecked sum), funding.cljk:138 (:funding-residue (fnil + 0)), liquidation.cljk:195 (:insurance-fund (fnil + 0)), commit.cljk:113 (reduce (fnil) aggregate) / commit.cljk:115 (reserves) — all present & valid; repo bench/torihiki/bench.cljk:112 `(bk/cancel! b oid)` 2-arg unlanded.

**suite 155 両 runtime PASS (same-count 59 度目)**:
- JVM: copy 上 `clojure -M:test` → 357 tests / 915 assertions / 0 failures / 0 errors, JVM_EXIT=0, 17/17 ns (evidence/f513-test-jvm.{out,err,exit}; err 0 bytes).
- nbb: 2 段 classpath (stage-1 `kbb --backend sci --classpath "/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/text/src" script/nbb-classpath.cljc` EXIT=0 空 err → stage-2 `NODE_PATH=/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/chain/node_modules kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljc`) → 357/915 0F/0E, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0 (evidence/f513-test-nbb.{out,err,exit}).

**parity 52 度目 両 runtime 実測 PASS**:
- JVM `clojure -M:parity` PJ_EXIT=0 → FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true (frame-440/484/495 baseline 同一, evidence/f513-parity-jvm.{out,err}).
- nbb `kbb --backend sci --classpath "$CP"` + AMU_HOME/KOTOBA_CHECKOUTS/NODE_PATH 規約 `script/kotoba-parity.cljc` → fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass PN_EXIT=0 (evidence/f513-parity-nbb.{out,err}).

**fuzz digest 52 度目 byte-identical**:
- JVM `clojure -M -e "(load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"` FJ_EXIT=0 (2743 bytes = core 2715 + echo 行 + FJ_EXIT 行); nbb driver `kbb … evidence/fuzz-nbb-driver.cljc` FN_EXIT=0 (2725 bytes = core 2715 + FN_EXIT 行) — seed 0–15 digest 行両側一致, raw diff は 1d0 JVM echo 行 + 20c19 FJ_EXIT vs FN_EXIT 差分のみ (evidence/f513-fuzz-{jvm,nbb}.out + f513-fuzzdiff.txt)。exit 行を raw 比較に含める場合の差分は常態であり canonical assert は digest 行一致 + fn-exit 除外解釈 — 参考: 前 frames は exit 行を含めて raw diff = echo+exit 行で同形状。

**bench (repo 3-part fix 未着地)**: copy 上 `clojure -M:bench 1000000` → BENCH_EXIT=1, `Execution error (ArityException) at torihiki.bench/run-tape (bench.cljc:112). Wrong number of args (2) passed to: torihiki.book/cancel!` + torihiki/bench.cljc:105/:114 boxed math warning 2 行 (evidence/f513-bench.{out,err} + bench.exit=1) — repo bench.cljk:112 2-arg 未着地の既知赤 repr (copy bench.cljc:112 実読一致)。または copy 上 f451 消滅であり 3x green series は frame-451/453/455/456/460 測定コピー由来のまま引用 (f506 copy での 3x green re-run は f511 前例に委ぬ). Built-in tape still ArityException — 再現性 2 まま (n>=1M cancel actually runs is met only on owner-wired copy).

- reproducibility: seeded fuzz byte-identical 51→52 度目成立; bench 3-part fix 未着地 (ArityException reproduced), score 2 stays.
- falsify-14 (複数アカウント deficit 合算集計経路) 未実測繰越 (frame-407 登録 hypothesis) — 次 genuine 枠優先.
- discoveries 0, new hypotheses 0. NEXT unmeasured 0 (fix unlanded, cron code-change prohibited).
- next frame = 514 (suite 156 / parity 53 / fuzz 52→53 / bench 40 実行目). Frame record: evidence/2026-09-20-0906-frame513-genuine-suite155-parity52-fuzz52.md.
