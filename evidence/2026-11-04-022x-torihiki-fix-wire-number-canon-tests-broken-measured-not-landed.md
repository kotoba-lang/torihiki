# 2026-11-04 02:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# BROKEN-first test write + kbb BROKEN measurement). "not run" != "passed":
# nothing is claimed green this tick except the two runs quoted below.
# maturity.md untouched (measurement bots' file).

Load gate: uptime direct 02:17 — 1.40 / 1.47 / 1.48, all windows < 20, gate
passed. torihiki_state.sh ABSENT in this profile (chronic-fault precedent,
direct uptime). Terminal default cwd broken — explicit workdir on every call.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261104-0028, branch
bot/torihiki-fix-20261104-0028 (the 00:28 handoff) — CONTINUED, kept for the
next tick. The 00:28 carry-forward (test pins -> impl -> BROKEN per leg ->
GREEN 3 legs -> land) was followed in order; this tick completes step 1 and
part of step 3.

## Measured this tick (exact)

- Baseline re-confirmed GREEN kbb/SCI (engine + suite unmodified): **600
  tests / 5216 assertions / 0 failures / 0 errors**, exit 0
  (/tmp/wnc-baseline.out). Matches PR #88's green.
- JVM ground truth for the u-1.5e300 pin literal re-verified independently
  (clojure -M, openjdk 27, BigDecimal(double)-ctor -> toBigIntegerExact):
  the extracted evidence literal (301 chars) MATCHES exactly. Note: the 2229
  doc's "309-digit" remark is a slip — the literal is 301 chars and correct.
- **Tests written** (test/torihiki/log_test.cljk, appended section "the
  number canon", 8 deftests / 34 assertions; literals spliced byte-for-byte
  from the 00:28 evidence defs — u-1e17 18, u--2.5e18 20, u-1.5e300 301,
  d-5.5 3, d--2.5 4, d-2.5 3, d-0.1 57, d-0.1+0.2 54, d-1e-300 1051 chars):
  out-of-domain-whole-numbers-tag-u (["u" digits] for 1e17 / -2.5e18 /
  1.5e300 + the :claim slot), fractional-numbers-tag-d (["d" exp] for 5.5 /
  -2.5 / 2.5 / 0.1 / 0.1+0.2 / 1e-300 + ["d" "Infinity"] / ["d" "-Infinity"]),
  in-domain-integral-numbers-stay-bare (bare 5 for 5 and 5.0, bare 0 for
  -0.0 both on the wire and on decode), nan-has-no-wire-form (thrown?),
  the-wire-round-trip-is-idempotent (WIRE-level law over 1e17 / -2.5e18 /
  5.5 / 0.1 / -0.0 / 1e-300 / 1.5e300 / ±Inf), decoded-numbers-recover-the-
  submitted-value ("d" via `=`, "u" via `==` — never type-sensitive `=`
  across a submitted double and a decoded BigInt), signing-payload-renders-
  numbers-through-the-number-canon (the full payload literal for
  {:tx :deposit :account 1e17 :amount 5.5} — 7 colons between account and
  amount, shape confirmed by the run), the-signing-kind-rendering-is-total
  ({:tx 5} renders "5", :a/y renders "a/y"). The existing fn pin
  (wire-rejects-what-it-cannot-validate) is unchanged.

- **BROKEN run, kbb/SCI leg** (`kbb -M:test`): **608 tests / 5250
  assertions / 4 failures / 20 errors**, exit 1 (/tmp/wnc-broken-kbb.{out,err}).
  OBSERVED red modes (not predicted — measured):
  - tag-u: 2 FAIL = 1e17 / -2.5e18 encode BARE today (the predicted kbb
    mis-pin); 1 ERROR = 1.5e300 throws "no wire form" on kbb (measured
    surprise: kbb's integer? rejects 1.5e300 although (integer? 1e17) is
    true there); :claim pin FAIL (bare).
  - tag-d: 8 ERROR = "torihiki.log: value has no wire form" throw for every
    fractional and both infinities (Gap 2, both-runtimes throw today).
  - idempotence: ERROR at 5.5 (same throw). recovery: 8 ERROR (d values) +
    1 ERROR (1.5e300); the == pins for 1e17 / -2.5e18 PASS (bare round trip
    is value-correct on kbb).
  - signing literal: **PASS on kbb** (str is plain on JS below 1e21 —
    Gap 3's red is JVM-leg-only, as the 02:2x design predicted). This pass
    also confirms the tx-line shape literal.
  - kind-total: ERROR ((name 5) -> "Doesn't support name: 5") + FAIL
    (:a/y renders "y").
  - guard pins (bare 5 / bare 0 / NaN refusal) PASS pre-fix, as designed.

- BROKEN run, JVM leg (script/jvm-test.sh): STARTED (/tmp/wnc-broken-jvm.out,
  background proc_c16f850a6584) but budget expired before the result was
  retrieved — **NOT RUN to completion as far as this tick knows; nothing
  claimed**. Next tick: read /tmp/wnc-broken-jvm.{out,err} if still present,
  else re-run.

## Not run / not claimed

src/torihiki/log.cljk implementation (enc-val "u"/"d" ladder, dec-val tags,
signing-payload number canon + total kind helper — design unchanged, see the
00:28 carry-forward step 2), GREEN on all three legs, the same-count claim,
the nbb leg at all, and landing (commit/PR/merge) of the fix itself: NOT
RUN. gh auth still dead (verified again this tick: "The token in default is
invalid") — PR create remains BLOCKED; the branch push is the handoff
channel. This evidence file + the test section are the only writes to the
repo tree this tick.

## Next tick order (unchanged from 00:28, step 2 onward)

1. Implement src/torihiki/log.cljk per the 00:28/2229 carry-forward (the
   digit-x5 route verbatim from evidence/wnc-kbb-probe.cljk on branch
   bot/torihiki-fix-20261103-2229; BigInt `*` never on the :cljs number
   path; JVM Ratio/BigDecimal stay refused; NaN keeps the loud throw).
2. GREEN kbb -M:test (expect 608 / 5250 / 0F / 0E), script/jvm-test.sh,
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh
   — never claim both runtimes from one.
3. Land: commit, push (git -c credential.helper=osxkeychain), PR + merge.
   Compat note for the landing record: namespaced kind rendering changes
   from "y" to "a/y" — signature payloads of OLD committed blocks carrying a
   namespaced kind would not re-verify (such blocks are engine-rejected
   :unknown-tx but committable).
