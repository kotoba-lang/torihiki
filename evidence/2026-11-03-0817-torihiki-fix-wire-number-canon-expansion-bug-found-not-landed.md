# 2026-11-03 08:1x torihiki-fix tick — NOT LANDED (run budget exhausted after
# the kbb expansion probe + debug). NO tests written, NO suite, NO BROKEN run,
# NO PR, no src/test change. "not run" != "passed": nothing is claimed green
# here. maturity.md untouched (measurement bots' file).
#
# Load gate: torihiki_state.sh 08:17 — 9.12 / 10.59 / 11.80, all windows
# < 20, gate passed.
#
# Worktree: continued the KEPT handoff ~/github/wt/fleet/bot-torihiki-fix-
# 20261103-0222, branch bot/torihiki-fix-20261103-0222 at f504e36; `git fetch
# origin` this tick confirms origin/main still bf36fee (PR #88 merge) and the
# branch contains it. Branch + worktree KEPT again — next tick starts here.

## Measured this tick

### 1. probe-nums3 ran on kbb (`kbb --backend sci <scratch>/probe-nums3.cljk`)
(kbb/SCI has NO `js*` — the probe's pow line died "Unable to resolve symbol:
js*"; removed, re-run clean. Ratios/BigDecimal literals read as Number on
SCI — `(str (type 1/2))` shows function Number; NaN `==` false; `(= -0.0 0)`
true on kbb.)

- reader conditionals resolve :cljs on kbb ("reader-cond: JS") — the :cljs
  branches in log.cljk are the ones kbb executes.
- "u" digits route VALIDATED byte-for-byte against JVM (see below):
  (js/BigInt 1e17).toString = "100000000000000000",
  (js/BigInt -2.5e18).toString = "-2500000000000000000",
  (js/BigInt 1.5e300).toString = "1500000000000000078757140382806630373056702871662238732373781173267703686983362293679557062620671796065556665749325817265413784853040645863467188277180060474272580801389863705606745350692182135089053429852456199917621678558451461320111979114170213741868888183230085264257173504208294580298189100810240"
  — the exact double value digits (NOT 1.5e300 rounded), identical to the
  JVM BigDecimal-ctor route.
- (js/parseFloat "Infinity") -> ##Inf; (str ##Inf) "Infinity" both agree.

### 2. JVM literals regenerated (clojure -M <scratch>/jvm-lits.cljk, EXIT=0)
Use these as the test pin literals:
- ["u" "100000000000000000"] for 1e17; ["u" "-2500000000000000000"] for
  -2.5e18; ["u" "1500000000000000078757140382806630373056702871662238732373781173267703686983362293679557062620671796065556665749325817265413784853040645863467188277180060474272580801389863705606745350692182135089053429852456199917621678558451461320111979114170213741868888183230085264257173504208294580298189100810240"
  for 1.5e300 (kbb byte-identical, above).
- ["d" "5.5"] 5.5; ["d" "-2.5"] -2.5;
  ["d" "0.1000000000000000055511151231257827021181583404541015625"] 0.1;
  ["d" "0.3000000000000000444089209850062616169452667236328125"] 0.1+0.2;
  1e-300's expansion = "0." + 299 zeros + "100000000000000002505909183520875968569614680770370524992534231990046604318405148467630281218195010089496230627027825414891031146499880413081224609160619018271942662793458427551041478278701507022263926060379361392435977509403014386614147912551359088259101734169222292122040491862182202915561954185941852588326204092831631787205015401996986616948980410676557942431921652541808732242554300585073938340203330993157646467433638479065531661724812599598594906293782493759617177861888792970476530542335134710418229637566637950767497147854236589795152044892049176025289756709261767081824924720105632337755616538050643653812583050224659631159300563236507929025398878153811554013986009587978081167432804936359631140419153283449560376539011485874652862548828125"
  (full literal is in the jvm-lits output; regenerate rather than hand-copy —
  the zero-count is 299 after "0.").
- Verified: (Double/parseDouble <0.1 expansion>) = 0.1 true; 1e-300 same
  true; (bigint "9007199254740993") = 2^53+1 `=` true (BigInt decode is
  =-clean for integer-typed inputs).

### 3. DESIGN-CRITICAL FINDING: the drafted :cljs "d" expansion is WRONG on kbb
The 06:17 handoff's js-expansion (DataView -> m,k -> BigInt m*5^k loop ->
trailing-zero strip -> point placement) was run on kbb and DISAGREES with the
JVM exact expansions at byte level:
- exp 5.5  => "5.5000000000004405364961712621152400970458984375"   (JVM "5.5")
- exp -2.5 => "-2.50000000000022026824808563105762004852294921875" (JVM "-2.5")
- exp 0.1  => "0.1000000000000068889338677990963333286345005035400390625"
  (JVM "0.1000000000000000055511151231257827021181583404541015625")
- 1e-300 / 0.1+0.2 mantissa tails also wrong.

Debug probe (probe-nums4, RUN on kbb) shows the bit extraction is exact:
5.5 hi 1075183616 / lo 0 / e-bits 1025 / frac 1688849860263936 /
m 6192449487634928 / k 50 — all verified correct (m = 11*2^49, so m*5^50 must
be 55 followed by 49 zeros). The loop printed digits
"550000000000044053649617126211524009704589843750000" (51 chars, right
length, WRONG middle: deviation 44053649617126211524009704589843750000
~ 4.4e37 ~ double-rounding scale). 0.1: m 7205759403793290 / k 56 likewise
exact input, wrong product. So the corruption is INSIDE the BigInt
multiply-loop arithmetic on kbb/SCI (or its number path) — root cause NOT
confirmed; probe-nums5.cljk (scratch) was written to isolate single
mult vs loop50 vs string-seed and was NOT RUN (budget).

Implication for the package: the "u" digits route is good; the "d" expansion
must not use the drafted BigInt multiply loop. Next tick: run probe-nums5,
then pick an exact route (likely pure-Clojure digit-string x5-with-carry
multiplication — no BigInt needed — or whatever probe-nums5 shows is sound),
then write the log_test.cljk pins against the JVM literals above.

## Not run / not claimed
Baseline suite (600 tests / 5216 assertions per PR #88's green), all
BROKEN/GREEN runs, all three legs: NOT RUN (budget). No PR, no src change.

## Environment note
execute_code is BLOCKED in this unattended cron profile ("cron jobs run
without a user present") — arithmetic ground truth must go through terminal /
kbb / clojure probes, not Python.

## Next tick order (06:17 plan with the expansion amendment)
1. probe-nums5 on kbb (isolate BigInt-loop corruption); settle the exact
   "d" expansion route for :cljs and re-verify byte identity against the
   JVM literals (0.1's 55-digit literal is the contract).
2. log_test.cljk pins per 06:17 (wire literals, wire= round-trip VALUE pins
   via == at numeric leaves, -0.0 IS 0, in-domain 5.0 stays bare 5,
   signing-payload literal for :account 1e17, kind helper {:tx 5} no-throw +
   namespaced :a/y renders "a/y", NaN refused, fn pin unchanged).
3. BROKEN once per leg where the red fires (record observed modes), implement
   log.cljk enc-val/dec-val + signing-payload number canon, GREEN kbb -M:test,
   then script/jvm-test.sh and KOTOBA_CHECKOUTS=... script/nbb-test.sh —
   never claim both runtimes from one.
4. Land: commit, push, PR, merge --delete-branch, worktree remove.
