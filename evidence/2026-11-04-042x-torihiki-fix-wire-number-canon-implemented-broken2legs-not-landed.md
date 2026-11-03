# 2026-11-04 torihiki-fix tick — wire-number-canon IMPLEMENTED, BROKEN
# measured on 2 legs, NOT LANDED (run budget exhausted before GREEN).
# "not run" != "passed": nothing is claimed green this tick except the two
# BROKEN runs quoted below (a red suite is a measured result). maturity.md
# untouched (measurement bots' file).

Load gate: uptime direct 04:18 — 2.18 / 1.73 / 1.58, all windows < 20, gate
passed. torihiki_state.sh ABSENT in this profile (chronic-fault precedent,
direct uptime). Terminal default cwd broken — explicit workdir on every call.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-0028, branch
bot/torihiki-fix-20261104-0028 (the 00:28 handoff) — CONTINUED. This tick
followed the 02:2x carry-forward in order: (1) test-file defect fixed, (2)
BROKEN re-measured per leg, (3) log.cljk implemented. GREEN + landing are
the next tick's step.

## What this tick changed (unverified against GREEN — next tick must)

- test/torihiki/log_test.cljk: def-name defect fixed — `u--2.5e18`,
  `u-1.5e300`, `d-5.5`, `d--2.5`, `d-2.5`, `d-0.1`, `d-0.1+0.2` renamed to
  dot-free names (`u-neg-2p5e18`, `u-1p5e300`, `d-5p5`, `d-neg-2p5`,
  `d-2p5`, `d-0p1`, `d-0p1-plus-0p2`): the JVM compiler reads a symbol with
  a dot as interop — the 02:2x tick's JVM BROKEN died at compile with
  "ClassNotFoundException u--2.5e18" (log_test.cljc:239), not at a pin.
  Assertions unchanged.
- src/torihiki/log.cljk: the number canon, per the scoped + corrected
  design (0222 scoped, 0817 expansion-bug amendment, 142x JVM `=`-sensitivity
  correction, 2229 probe-validated digit-x5 route, 0028 literals):
  - enc-val: whole number outside i53 -> ["u" <exact digits>] (Gap 1 —
    used to throw on the JVM, `integer?` a type test, and encode bare on
    kbb, a value test); fractional double and signed infinity -> ["d"
    <exact decimal expansion>]/["d" "Infinity"] (Gap 2 — both threw on
    both runtimes, a handled-and-rejected block could not be committed);
    NaN stays the loud refusal (no round trip can hold). In-domain
    integral -> bare long UNCHANGED. JVM Ratio/BigDecimal stay refused.
  - Exact digits: JVM BigDecimal(double)-ctor -> toBigIntegerExact /
    toPlainString; :cljs js/BigInt string conversion for "u" and the
    evidence/wnc-kbb-probe.cljk digit-vector x5-with-carry route (mul5d /
    digits-of / frac-expansion, reused verbatim) for "d" — BigInt
    arithmetic never on the number path (2229 measured SCI BigInt *5 loop
    corruption at the double-rounding scale). js/BigInt input is
    digits-only: measured on kbb this tick — (integer? (js/BigInt 5)) and
    (number? (js/BigInt 5)) are both FALSE and (long (js/BigInt 5))
    THROWS, so it is branched before every `long`/number path.
  - dec-val: "u" -> JVM (bigint s) / :cljs (js/Number (js/BigInt s)) (the
    designed category split — `=` across categories is type-sensitive,
    pins use `==`); "d" -> parseDouble / parseFloat.
  - signing-payload (Gap 3): every slot renders through the canon (exact
    digits — `str` gave "1.0E17" on the JVM vs "100000000000000000" on
    JS, two runtimes signing different bytes over one block); kind renders
    through a TOTAL helper — keyword -> (subs (str k) 1) keeping the slash
    (:a/y -> "a/y", was "y" via `name`), string -> as-is, else slot
    rendering ((name (:tx t)) threw on {:tx 5}).
  - Compat note for the landing record (from 0028): namespaced kind
    rendering changes from "y" to "a/y" — signature payloads of OLD
    committed blocks carrying a namespaced kind would not re-verify (such
    blocks are engine-rejected :unknown-tx but committable).

## Measured this tick (exact)

- BROKEN kbb/SCI (`kbb -M:test`, tests renamed + engine unmodified):
  **608 tests / 5250 assertions / 4 failures / 20 errors**, exit 1
  (/tmp/wnc2-broken-kbb.{out,err}) — byte-same counters and red modes as
  the 02:2x tick's measurement, so the rename changed nothing: tag-u 2 FAIL
  (1e17 / -2.5e18 encode bare on kbb) + 1 ERROR (1.5e300 throws) + :claim
  FAIL; tag-d 8 ERROR (throw, every fractional + both infinities);
  idempotence ERROR at 5.5; recovery 8+1 ERROR; signing literal PASS on kbb
  (str plain below 1e21); kind-total ERROR ((name 5)) + FAIL (:a/y "y");
  guard pins (bare 5 / bare 0 / NaN refusal) PASS pre-fix as designed.
- BROKEN JVM (script/jvm-test.sh renamed copy, engine unmodified):
  **608 tests / 5248 assertions / 2 failures / 25 errors**, exit 1
  (/tmp/wnc2-broken-jvm.{out,err}) — OBSERVED red modes: Gap 1 is
  JVM-leg-real (1e17 / -2.5e18 / 1.5e300 ALL throw "no wire form" here ->
  4 ERROR incl. the :claim pin), tag-d 8 ERROR, idempotence ERROR at 1e17
  (first value — 0 assertions out of that doseq, which is exactly the
  5250-5248 counter gap vs kbb), recovery 11 ERROR, signing literal FAIL
  ("1.0E17" divergence, Gap 3), kind-total ERROR + FAIL. Exactly 2F/25E.
- Both BROKEN runs executed against the PRE-FIX engine: kbb loaded sources
  before the edits landed and the JVM leg's renamed copy predates them —
  corroborated by the red-mode split matching the predicted pre-fix modes
  on each runtime (bare-vs-throw asymmetry per leg).
- Baseline GREEN (engine + suite unmodified) 600 / 5216 / 0F / 0E was
  measured and cited by the 02:2x tick (/tmp/wnc-baseline.out); not
  re-run this tick.

## Not run / not claimed

GREEN on all three legs (expect 608 / 5250 / 0F / 0E on every leg), the
same-count cross-runtime claim, the nbb leg at all, and landing
(commit/PR/merge of the fix): NOT RUN (budget). The implementation above
has NOT been executed green anywhere — treat it as unverified until a tick
runs `kbb -M:test`, script/jvm-test.sh and
KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh
to green and records the counters. gh auth is still dead on this host
("The token in default is invalid") — PR create remains BLOCKED unless a
tick restores gh (the git-credential keychain token works for push;
`git -c credential.helper=osxkeychain push`).

## Next tick order (remainder of the 00:28 carry-forward)

1. GREEN kbb -M:test (expect 608 / 5250 / 0F / 0E), script/jvm-test.sh,
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
   script/nbb-test.sh — never claim both runtimes from one. If any pin
   fails, debug the impl (the digit-x5 route and the literals are
   validated; suspect ladder order or reader-conditional shape first).
2. Land: commit, push (git -c credential.helper=osxkeychain), PR + merge
   (gh auth restore or API with the keychain token), worktree remove.
   Carry the compat note above into the landing record.
