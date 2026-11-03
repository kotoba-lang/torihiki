# 2026-11-04 00:28 torihiki-fix tick — NOT LANDED (run time budget exhausted
# before any test or src change). "not run" != "passed": nothing is claimed
# green here. No PR, no src/test change. maturity.md untouched (measurement
# bots' file).

Load gate: uptime direct 00:17 — 2.38 / 1.99 / 1.84, all windows < 20, gate
passed. torihiki_state.sh broken in this session (its cd target
~/github/wt/fleet/bot-torihiki-fix is missing — chronic-fault precedent,
direct uptime used). Terminal default cwd broken — explicit workdir on every
call (known, same as 2229 tick).

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-0028, branch
bot/torihiki-fix-20261104-0028 off origin/main e17e767 (PR #90 merge; PR #89
= the 0222/0817/142x wire-number-canon scoping evidence). The 2229 tick's
branch bot/torihiki-fix-20261103-2229 is still unmerged — its carry-forward
was read IN FULL and is what this tick continued.

## Queue check this tick (measured by code read)

maturity.md NEXT's fix items all verify LANDED at e17e767: validate i53/
notional caps (api.cljk:304/311 and every store door), accum sum gates via
fx/acc/fx/sadd (state.cljk, funding.cljk), fmul/exact products, rate caps,
falsify-14 merkle saturation, bench 3-part fix (bench.cljk:123 / curve.cljk:26
ring-owner wiring; probe.cljk:31 owner-consistent), repro3 (3fb5add), fuzz
in-suite, jvm/nbb/parity legs in-repo (script/*.sh), and the core-lib
settle-deficit delta clamp (torihiki-core pinned sha c59d8fd,
lib/kotoba.torihikiCore/src/torihiki/clearing.cljk:764 — capped move, no
throw). The active queue head is wire-number-canon (4 scoped-not-landed
handoffs; design settled and probe-validated on the 2229 tick).

SMALL ALTERNATE PACKAGE, unlanded, for whoever wants it: store-tx-shape's
:deposit clause (api.cljk:526) judges account/credit only — validate* judges
:amount (1..i53-max, :bad-amount, api.cljk:1131) and the engine door does
not. This is integral_double_test.cljk's "22:2x finding 2 residual" (lines
176/253: "the suite must not pin it as fixed"). Fix = mirror the one
int-in? :amount judgment into the :deposit clause (same keyword, same
order), then pin :deposit amount rows at both doors + update the two residual
comments. TDD BROKEN-first: the shape-door pin fails pre-fix (door answers
nil).

## Measured this tick (exact)

JVM ground-truth pin literals regenerated (clojure -M /tmp/wnc-gen-lits.clj,
EXIT=0, openjdk 27, BigDecimal(double)-ctor route — matches 0817/2229
contract byte-for-byte): 1e-300 expansion length 1051, 299 zeros after "0.",
tail "28125"; parse-back (= 0.1 (Double/parseDouble exp)) and 1e-300 both
true. Generator recipe (the (subs -5) in the 2229 doc's tail check is not
valid clojure subs — use (- (count s) 5)):

  u  = (str (.toBigIntegerExact (BigDecimal. v)))   ; whole doubles
  d  = (.toPlainString (BigDecimal. v))             ; fractional doubles

THE PIN LITERALS (paste into the test; regenerate rather than hand-copying):

(def u-1e17 "100000000000000000")
(def u--2.5e18 "-2500000000000000000")
(def u-1.5e300 "1500000000000000078757140382806630373056702871662238732373781173267703686983362293679557062620671796065556665749325817265413784853040645863467188277180060474272580801389863705606745350692182135089053429852456199917621678558451461320111979114170213741868888183230085264257173504208294580298189100810240")
(def d-5.5 "5.5")
(def d--2.5 "-2.5")
(def d-2.5 "2.5")
(def d-0.1 "0.1000000000000000055511151231257827021181583404541015625")
(def d-0.1+0.2 "0.3000000000000000444089209850062616169452667236328125")
(def d-1e-300 "0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000002505909183520875968569614680770370524992534231990046604318405148467630281218195010089496230627027825414891031146499880413081224609160619018271942662793458427551041478278701507022263926060379361392435977509403014386614147912551359088259101734169222292122040491862182202915561954185941852588326204092831631787205015401996986616948980410676557942431921652541808732242554300585073938340203330993157646467433638479065531661724812599598594906293782493759617177861888792970476530542335134710418229637566637950767497147854236589795152044892049176025289756709261767081824924720105632337755616538050643653812583050224659631159300563236507929025398878153811554013986009587978081167432804936359631140419153283449560376539011485874652862548828125")

(signing-payload expected tx-line shape, verified by hand construction only —
NOT yet run: segments [kind account market side level qty flags oid amount
price] joined by ":", so {:tx :deposit :account 1e17 :amount 5.5} renders
"deposit:100000000000000000:::::::5.5:" — 7 colons between account and
amount, 1 trailing. Confirm against the run before pinning.)

## Environment notes (this host, for the next tick)

- kbb = /Users/junkawasaki/github/kotoba-lang/kotoba/bin/kbb — the older
  docs' /Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang path does
  NOT exist here; monorepo sibling is /Users/junkawasaki/github/kotoba-lang/.
  org-babashka-nbb sibling present (KBB_HOME/../org-babashka-nbb/cli.js OK).
- node_modules/@noble/hashes absent in the worktree — nbb leg will need
  `npm ci` in-worktree or a NODE_PATH that carries @noble/hashes.
- gh auth dead again: `gh auth status` -> "The token in default is invalid"
  (2026-11-04 00:1x). ~/.git-credentials has github.com entries (credential
  helper = store) — push untested this tick; 142x recipe
  `git -c credential.helper=osxkeychain push` for the keychain. PR create
  remains BLOCKED until gh auth works.

## Carry-forward (unchanged from the 2229 doc — implement in that order)

1. test/torihiki/log_test.cljk pins (BROKEN-first per leg), using the literal
   defs above: ["u" digits] for 1e17 / -2.5e18 / 1.5e300; ["d" exp] for 5.5 /
   -2.5 / 2.5 / 0.1 / 0.1+0.2 / 1e-300; ["d" "Infinity"]/["d" "-Infinity"];
   bare 5 for 5.0, bare 0 for -0.0; wire-idempotence
   (= (tx->wire tx) (tx->wire (wire->tx (tx->wire tx)))) over 1e17 / -2.5e18
   / 5.5 / 0.1 / -0.0 / 1e-300 / 1.5e300 / ±Inf (WIRE-level law — never
   type-sensitive `=` across a submitted double and a decoded value);
   "d" values `=`-recovered; "u" recovered pinned via `==` (category-blind;
   (str decoded) is NOT plain on JS above 1e21 — do not pin str for 1.5e300
   on the JS legs); NaN refused (thrown? at tx->wire, both runtimes);
   signing-payload literal for {:tx :deposit :account 1e17 :amount 5.5};
   kind helper {:tx 5} no-throw; :a/y renders "a/y"; fn pin unchanged.
2. src/torihiki/log.cljk only: enc-val ladder — (and int-like? in-domain?)
   (long v) UNCHANGED; int-like? otherwise -> ["u" <exact digits>] (JVM
   BigDecimal(double)-ctor -> toBigIntegerExact -> str for doubles, (str v)
   for integer types; :cljs (js/BigInt v).toString — string conversion only,
   never BigInt `*` (kbb's BigInt * is correctness-broken, 2229 measured));
   (number? v) -> ["d" <exact expansion>] (JVM (.toPlainString (BigDecimal.
   (double v))); :cljs the validated digit-vector x5 route from
   evidence/wnc-kbb-probe.cljk on branch bot/torihiki-fix-20261103-2229
   (mul5d / digits-of / frac-expansion — reuse verbatim); +/-Inf ->
   "Infinity"/"-Infinity"; NaN -> the existing "no wire form" throw; JVM
   Ratio/BigDecimal stay refused — doubles only); dec-val "u" (JVM
   (bigint s), :cljs (js/Number (js/BigInt s))), "d" (Double/parseDouble /
   js/parseFloat); signing-payload: total kind helper (keyword ->
   (subs (str k) 1), string -> as-is, number -> canon digits, else (str v))
   and the 9 slots through the same number canon.
3. BROKEN once per leg where the red fires (record OBSERVED modes; known:
   Gap 1 throws only on the JVM leg — kbb encodes 1e17 bare today), then
   GREEN kbb -M:test, script/jvm-test.sh (renamed copy),
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh
   — never claim both runtimes from one. Baseline to beat: 600 / 5216 / 0F
   / 0E (re-confirm first — NOT run this tick).
4. Land: commit, push, PR + merge. Compat note for the landing record:
   namespaced kind rendering changes from (name k) to "a/y" — signature
   payloads of OLD committed blocks carrying a namespaced kind would not
   re-verify (such blocks are engine-rejected :unknown-tx but committable).

## Not run / not claimed

Baseline suite (600/5216 expected — NOT run this tick), all BROKEN runs, all
three GREEN legs, the same-count claim: NOT RUN. No src/test change. This
evidence file is the only write. Branch pushed as the handoff channel; the
next tick starts from the literal defs above + the 2229 carry-forward.
