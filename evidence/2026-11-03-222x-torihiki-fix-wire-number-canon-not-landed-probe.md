# 2026-11-03 22:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# the mandated probe). No test written, no src change, no PR. "not run" !=
# "passed": nothing is claimed green here except the two probe runs quoted
# below. maturity.md untouched (measurement bots' file).

Load gate: uptime direct 22:17 — 2.95 / 2.17 / 1.86, all windows < 20, gate
passed. torihiki_state.sh ABSENT in this profile (chronic-fault precedent).
Terminal default cwd missing — explicit workdir on every call (known).

Worktree ~/github/wt/fleet/bot/20261103-2229, branch
bot/torihiki-fix-20261103-2229 off origin/main 3ab13e5 (PR #89 merge — the
0222/0817/142x scoping evidence is on main; 1621/1837 branches unmerged,
their carry-forward read and followed).

## Measured this tick (exact)

### 1. Digest-risk check (the 1837 open risk) — CLEARED
test/torihiki/fuzz_seeded_test.cljk generates only in-domain integers
(boundary fixtures use fx/i53-max and (quot fx/i53-max 2) — in-domain) and
pins INVARIANTS, not digests ("A pinned digest breaks on every legitimate
engine change" — its own docstring). script/kotoba-parity.cljk contains no
tx->wire / signing-payload / enc-val usage (grep 0 matches).
evidence/fuzz-seeded.cljk (measurement oracle) contains no log/ or tx->wire
usage (grep 0 matches). Nothing pinned shifts with the enc-val ladder change.

### 2. JVM ground truth regenerated (clojure -M /tmp/wnc-jvm-lits.clj, EXIT=0,
openjdk 27) — matches the 0817 contract literals byte-for-byte:
5.5 -> "5.5"; -2.5 -> "-2.5"; 2.5 -> "2.5";
0.1 -> "0.1000000000000000055511151231257827021181583404541015625";
0.1+0.2 -> "0.3000000000000000444089209850062616169452667236328125";
1e-300 -> "0." + 299 zeros + "100000000000000002505909183520875968569614
680770370524992534231990046604318405148467630281218195010089496230627027
82541489103114649988041308122460916061901827194266279345842755104147827870
15070222639260603793613924359775094030143866141479125513590882591017341692
22292122040491862182202915561954185941852588326204092831631787205015401996
98661694898041067655794243192165254180873224255430058507393834020333099315
76464674336384790655316617248125995985949062937824937596171778618887929704
76530542335134710418229637566637950767497147854236589795152044892049176025
28975670926176708182492472010563233775561653805064365381258305022465963115
93005632365079290253988781538115540139860095879780811674328049363596311404
19153283449560376539011485874652862548828125" (verbatim in the test pin
— regenerate from /tmp/wnc-jvm-lits.clj rather than hand-copying a fold);
1e17 -> "100000000000000000"; -2.5e18 -> "-2500000000000000000";
1.5e300 -> the 309-digit literal (probe output, identical on kbb);
(str 1e17) -> "1.0E17" (Gap 3 confirmed); (integer? 1e17) -> false (JVM
type test — Gap 1's JVM fall-through confirmed); (integer? 5.5) -> false.

### 3. kbb/SCI probe (/tmp/wnc-kbb-probe.cljk via kbb --backend sci, EXIT=0)

- **BigInt multiplication is CORRECTNESS-BROKEN on kbb/SCI (root cause
  isolated)**: (js/BigInt "6192449487634928") * 5^50, computed BOTH as a
  single string-seeded multiply AND as a 50-step (* acc 5) loop, gives
  "550000000000044053649617126211524009704589843750000" on both routes —
  same wrong digits, so the corruption is in BigInt `*` (or SCI's dispatch
  of it), NOT the loop shape. Expected "550000000000000000000000000000000
  000000000000000000". This closes the 0817 open question: the drafted
  BigInt multiply route is dead on kbb; `(* big big)` must never be used
  on the :cljs number path (also note: SCI cannot resolve BigInt's `.pow`
  instance method at all).
- **Digit-vector x5-with-carry route VALIDATED byte-for-byte against the
  JVM literals** (5.5, -2.5, 2.5, 0.1's full 55-digit expansion, 0.1+0.2;
  1e-300 expansion = 1051 chars, 299 zeros after "0.", tail "28125") —
  this is the settled :cljs "d" expansion route: decompose v = m*2^-k by
  exact doubling ((= x (js/Math.floor x)) loop, |m| < 2^53), then digits
  of m*5^k by repeated x5-with-carry on an int vector, point placed k
  digits from the right. No BigInt arithmetic anywhere in the route (the
  only BigInt use is (js/BigInt x).toString for m's digits and the "u"
  digits — string conversion only, never `*`).
- kbb semantics measured: (integer? 1e17) => **true** (value test — Gap 1
  does NOT throw on kbb; the BROKEN red there is the mis-pin, bare digits
  instead of ["u" ...]); (integer? (js/BigInt 5)) and (number? (js/BigInt
  5)) => false (BigInt submissions are refused by enc-val on kbb today and
  stay refused — unchanged); reader conditionals select :cljs; Number.is-
  Finite available; (str ##Inf) "Infinity"; NaN detect (= v v) works;
  (js/parseFloat "Infinity") => ##Inf; (js/BigInt v).toString exact for
  real doubles (1e17 / -2.5e18 / 1.5e300 all byte-identical to the JVM
  literals — the "u" route re-validated; 2^53+1 as a LITERAL is rounded to
  …992 at read time, an input property, not a BigInt defect).
- decomp scaling loop: 5.5 -> m 11 k 1; 0.1 -> m 3602879701896397 k 55;
  1e-300 -> m 6032057205060441 k 1049 — all |m| < 2^53, exact.

## Carry-forward (implementation order, next tick — design unchanged)

1. test/torihiki/log_test.cljk pins (BROKEN-first per leg): wire literals
   ["u" "100000000000000000"] ({:account 1e17}), ["u" "-2500000000000000000"],
   ["u" <1.5e300 literal>], ["d" "5.5"], ["d" "-2.5"], ["d" "2.5"],
   ["d" <0.1 55-digit literal>], ["d" <0.1+0.2 literal>], ["d" <1e-300
   1051-char literal>], ["d" "Infinity"]/["d" "-Infinity"]; bare 5 for
   5.0 and bare 0 for -0.0; wire-idempotence
   (= (tx->wire tx) (tx->wire (wire->tx (tx->wire tx)))) over 1e17 /
   -2.5e18 / 5.5 / 0.1 / -0.0 / 1e-300 / 1.5e300 / ±Inf (WIRE-level law —
   never type-sensitive `=` across a submitted double and a decoded
   BigInt); "d" values `=`-recovered; "u" recovered pinned via (str
   decoded) = digit string (works on every leg: JVM decodes BigInt, JS
   decodes Number); NaN refused (thrown? at tx->wire, both runtimes);
   signing-payload literal for {:tx :deposit :account 1e17 :amount 5.5};
   kind {:tx 5} no-throw; :a/y renders "a/y"; fn pin unchanged;
   integral_double_test.cljk:275 (5.0 byte-identical to 5) rides along.
2. src/torihiki/log.cljk only: enc-val ladder — (and int-like? in-domain?)
   (long v) UNCHANGED; int-like? otherwise -> ["u" <exact digits>] (JVM
   BigDecimal(double)-ctor -> toBigIntegerExact -> str for doubles,
   (str v) for integer types; :cljs (js/BigInt v).toString — string
   conversion only); (number? v) -> ["d" <exact expansion>] (JVM
   (.toPlainString (BigDecimal. (double v))); :cljs the validated
   digit-vector x5 route; +/-Inf -> "Infinity"/"-Infinity"; NaN -> the
   existing "no wire form" throw, loud and symmetric; JVM Ratio/BigDecimal
   stay refused — doubles only, unchanged); dec-val "u" (JVM (bigint s),
   :cljs (js/Number (js/BigInt s)) — BigInt-submission caveat documented),
   "d" (Double/parseDouble / js/parseFloat); signing-payload: total kind
   helper (keyword -> (subs (str k) 1), string -> as-is, number -> canon
   digits, else (str v)) and the 9 slots through the same number canon
   (digits/expansion, never (str v)'s scientific form; non-numbers via
   (str v) — unchanged bytes for everything previously committable).
3. BROKEN once per leg where the red fires (record OBSERVED modes; known:
   Gap 1 throws only on the JVM leg — kbb encodes 1e17 bare today), then
   GREEN kbb -M:test, script/jvm-test.sh (renamed copy),
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh
   — never claim both runtimes from one. Baseline to beat: 600 / 5216 / 0F
   / 0E (re-confirm first; 142x measured it on jacob.local).
4. Land: commit, push (git -c credential.helper=osxkeychain), PR + merge
   (gh auth still unverified this tick — if dead per 142x/162x/1837, the
   pushed branch is the handoff channel and the PR step is BLOCKED until a
   human runs gh auth login).

Compat note for the record (from the probes): a js/BigInt submission is
refused on kbb (not number?/integer? there) but may be int-like on nbb
(cljs integer? includes js/BigInt) — a pre-existing leg divergence in what
is ADMITTED to the wire; the new ladder keeps kbb's refusal and routes
nbb's admission through ["u" digits] where it round-trips. Namespaced
kind rendering changes from (name k) ("y") to "a/y" per the 0817 design —
signature payloads of OLD committed blocks carrying a namespaced kind
would not re-verify; such blocks are engine-rejected (:unknown-tx) but
committable, so note it in the landing record.

## Not run / not claimed
Tests, BROKEN runs, all three GREEN legs, the same-count claim: NOT RUN.
No PR, no src change. Branch kept for handoff.
