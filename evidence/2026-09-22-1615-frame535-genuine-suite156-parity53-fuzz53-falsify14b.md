# frame 535 (2026-09-22 16:1x JST, cron) — genuine: suite 156 both runtimes PASS (same-count 60th, 357/915 0F/0E) / parity 53rd both runtimes PASS (canonical roots) / fuzz digest 53rd byte-identical / **falsify-14b measured: multi-account :deficit aggregation CONFIRMED (aggregate root fork, zero throws)**. Scores unchanged 3/3/3/3/2/1/1 (falsifications 19, f8–f18 + f14b OPEN). No code changes.

- load gate passed: pre-run direct 09:64→ declining window series 19.10/57.60/39.94 (14:50) → 9.71/20.72/22.11 (15:17) → 6.88/15.88/28.12 (16:11) — measurements started when 1-min and 5-min < 20, 15-min still elevated at start but declining; all measurement runs completed EXIT=0 under load ≤ 28 (1-min).
- environment: terminal tool initially routed to a foreign host (dannoMac-mini, macOS 26.2, user dan; /Users/junkawasaki absent) — recovered mid-frame and re-landed on main-2.local (repo host); all measurements executed locally on main-2. torihiki_state.sh stdout empty (chronic fault) — state gathered by direct reads.
- HEAD **e819d694** (git log -1; unchanged since frame 523 — 13 枠連続不変), `git diff HEAD --stat -- src/ script/ deps.edn` = 0 bytes → code unchanged, frame-513 canonical citations valid.
- measurement base: **/tmp/tori-f506** reused (present; `diff -rq src` vs repo = rename-only .cljk→.cljc, no content diffs → code-identical to HEAD e819d694 via 6552005c lineage). Note: repo-tree `clojure -M:test` finds **0 tests** (repo tests are .cljk) — 357/915 requires the renamed copy, per frame-513 recipe.

**suite 156 両 runtime PASS (same-count 60 度目)**:
- JVM: copy 上 `clojure -M:test` → **357 tests / 915 assertions / 0 failures / 0 errors**, JVM_EXIT=0, 17/17 ns (evidence/f535-test-jvm.{out,err,exit}).
- nbb: stage-1 `kbb --backend sci --classpath "<text/src>" script/nbb-classpath.cljc` EXIT=0 → stage-2 `NODE_PATH=<chain>/node_modules kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljc` → **357/915 0F/0E**, namespaces 17/17, TESTS-ON-NBB: pass, NBB_EXIT=0 (evidence/f535-test-nbb.{out,err,exit}).
- pitfall re-confirmed: bare `kbb --backend sci script/tests-on-nbb.cljk` on the repo tree → `Could not find namespace: torihiki.address-test` (classpath 経由必須); on the renamed copy the script arg must be the **.cljc** name.

**parity 53 度目 両 runtime 実測 PASS**:
- JVM `clojure -M:parity` PJ_EXIT=0 → **FLAT ROOT b4322bed…43445a / STATE ROOT d1ebb9d3…7e3aed7f / PROOF a 10 verifies true** (frame-440/484/495/513 baseline 同一, evidence/f535-parity-jvm.{out,err}).
- nbb `AMU_HOME/KOTOBA_CHECKOUTS/NODE_PATH 規約 script/kotoba-parity.cljc` → fixed 38 cases 0 drift / fixed-result 20 cases 0 drift / KOTOBA-PARITY: pass PN_EXIT=0 (evidence/f535-parity-nbb.{out,err}).

**fuzz digest 53 度目 byte-identical**:
- JVM `clojure -M -e "(load-file \"evidence/fuzz-seeded.cljc\") (fuzz-seeded/run)"` FJ_EXIT=0 (2733 bytes = core 2715 + echo 行; exit 行は .exit 分離); nbb driver FN_EXIT=0 (2715 bytes) — **raw diff = JVM echo 行 1 行のみ** (evidence/f535-fuzz-{jvm,nbb}.out + f535-fuzzdiff.txt), seed 0–15 digest 行両側一致 → frame-513/510 baseline と byte-identical.

**falsify-14b (multi-account :deficit aggregation through the REAL sweep) — CONFIRMED, 両 runtime 実測**:
- hypothesis: falsify-12 probe C measured ONE account's :deficit crossing 2^53 through the real sweep (k3 root fork). The unmeasured residual is the multi-account aggregate: when TWO accounts accumulate double-irreversible :deficit simultaneously, does the aggregate state-root fork the same way, and does the second account change per-account values?
- method (no repo changes; copy evidence/ only): evidence/falsify14b-multi-deficit.cljk — two accounts (4, 6) seeded per block with in-domain double-exact ODD loss d = 9007190000000001 (falsify-12 probe C's seed), collateral −d + live position, then the REAL end-of-block sweep (state.cljc apply-block → sweep-liquidations → liq/liquidate → cl/settle-deficit (clearing.cljc:707–712, `(fnil + 0)` unchecked sum with per-delta fx/check)) runs 3 blocks. Per-block snapshot: per-account deficit/collateral + aggregate `st/state-root`. JVM driver evidence/falsify14b-jvm-driver.cljk (`clojure -M -e '(load-file …) (load-file …)'`), nbb driver evidence/falsify14b-nbb-driver.cljk (fs readFileSync + load-string + pre-require, classpath 経由).
- result (k3, both EXIT=0, throw 皆無 — `line-threw` 発火せず):
  - JVM: d4 = d6 = **27021570002997003** (odd ≥ 2^54, double-irreversible), root **8faa8952…6b1f** (evidence/falsify14b-jvm.out)
  - nbb: d4 = d6 = **27021570002997004** (double 丸め), root **b75fd3ca…7a70** (evidence/falsify14b-nbb.out)
  - **aggregate root fork at k3**, same shape as falsify-12 single-account (97e35070… vs 065f44e4…).
  - per-account values are **byte-identical to falsify-12's single-account trajectory** (27021570002997003/…004) — the second accumulating account introduces no aggregation interference (deficits are per-account fields, not summed into one accumulator); the fork is per-leaf, and the aggregate root simply commits both divergent leaves.
- verdict: **CONFIRMED** — the multi-account aggregation path does NOT close the falsify-12 hole and does NOT create a new cross-account sum site: the fork mechanism is the same per-account double-irreversibility, now proven to propagate into the aggregate root with N=2. The falsify-14 NEXT item (複数アカウント deficit 合算の集計経路) is thereby **measured and closed as same-fix-package**: the accum sum gate at clearing.cljc:707 (`(fnil + 0)` on :deficit) covers the multi-account case by construction (gate is per-account, per-delta — falsify-12 probe C's k2 gate timing applies per account identically).
- harness notes (for the next driver author): nbb load-string of a lib with an `ns` form requires the namespaces pre-required at driver top level (`(require '[torihiki.state :as st] …)`) before load-string — the lib's own ns require does not resolve inside load-string; `(require' […])` without the space is an unresolvable symbol (`require'`), falsify-12's correct form is `(require '[…])`.

**bench (repo 3-part fix 未着地)**: not re-run this frame — repo bench.cljk:112 2-arg state unchanged (verified by HEAD code-diff = 0 bytes since frame 513's direct line read); known-red repr carries over.

- reproducibility: seeded fuzz byte-identical 52→53 度目成立; bench 3-part fix 未着地 → score 2 stays.
- discoveries 0 new sites, 1 confirmation (f14b). falsify-14 ledger item measured — NEXT's parenthetical "未実測は複数アカウント deficit 合算の集計経路のみ" is now closed; the validate-i53-halt fix package scope is unchanged (sum gate at clearing.cljc:707 was already in it).
- scores 7 軸変更なし (**3/3/3/3/2/1/1**).
- next frame = **536** (suite 157 / parity 54 / fuzz 54 / bench 40 実行目). Frame record: evidence/2026-09-22-1615-frame535-genuine-suite156-parity53-fuzz53-falsify14b.md.
