# 2026-11-03 16:2x torihiki-fix tick — NOT LANDED (run budget exhausted
# during reconnaissance + design read-back). No src change, no PR. "not run"
# != "passed": no suite leg was completed this tick.

Load gate: 16:18 uptime direct on jacob.local — 1.66 / 1.66 / 1.57, all
windows < 20, gate passed. NOTE (environment): ~/.hermes/scripts/
torihiki_state.sh is ABSENT on this host (the chronic-fault precedent in the
maturity frames applies: direct uptime). Also, the session terminal's default
cwd (~/github/wt/fleet/bot-torihiki-fix) does not exist — every terminal call
needs an explicit workdir, and `git worktree add` with the timestamped branch
name nests the worktree at ~/github/wt/fleet/bot/<stamp> (the branch name
contains a slash).

Worktree ~/github/wt/fleet/bot/torihiki-fix-20261103-1621, branch
bot/torihiki-fix-20261103-1621 at bf36fee (origin/main, PR #88 merge).
The 02:2x/14:2x handoff branch bot/torihiki-fix-20261103-0222 (c670c47) is
untouched and still carries the scoped design + design correction; this tick
works from those two evidence files and does not duplicate them.

## Measured this tick (exact counts)

- gh CLI auth is DEAD (gh auth status: "The token in default is invalid";
  gh api -> Requires authentication) — same as the 14:2x finding. SSH auth
  works (ssh -T git@github.com -> Hi com-junkawasaki). git push is expected
  via `git -c credential.helper=osxkeychain push` per the 14:2x recipe.
  PR create/merge via gh remains BLOCKED until a human runs `gh auth login`;
  the branch push is the handoff channel.
- kbb is at /Users/junkawasaki/github/kotoba-lang/kotoba/bin/kbb (not on
  default PATH). kbb v2 is a SCI-on-Node host and accepts `-e expr`.
  clojure CLI + openjdk 27 present (openjdk is keg-only: prepend
  /opt/homebrew/opt/openjdk/bin). Siblings org-babashka-nbb + kotoba present
  at /Users/junkawasaki/github/kotoba-lang/. The 0222 worktree already has
  node_modules; the new 1621 worktree needs `npm install` (script/nbb-leg.sh
  auto-installs).
- Repo state read at bf36fee: src/torihiki/ = 11 files (api/book/evm/funding/
  log/mark/order_margin/parity/slab/snapshot/state) — fixed/clearing/
  liquidation/commit/auth/… now live in the kotoba monorepo dep
  (io.github.kotoba-lang/torihiki-core, lib/kotoba.torihikiCore) and are
  OUT OF SCOPE per SOUL (only this repo's src/test). The in-scope residue of
  maturity.md NEXT-② (the accumulator sum gates at clearing/liquidation
  sites, fx/mul-rate pre-limit, REDUCING product bound, settle-deficit
  clamp) therefore lives OUT of this repo; the in-scope validate doors in
  api.cljk are already gated (line 304 :bad-quantity i53-bounded, line 311
  notional cap qty <= i53-max/level -> :bad-amount, deposit amount bounded
  at the door ~line 539). This is consistent with the fleet's recent ticks
  (PRs #73–#88) picking per-tick packages rather than the stale NEXT text.
- Baseline suite kbb -M:test: STARTED but NOT completed before budget —
  NOT RUN, no claim. (The 14:2x tick measured 600 tests / 5216 assertions /
  0 failures / 0 errors on kbb/SCI at the same tree; that is the baseline
  to re-confirm.)
- One runtime-semantics probe was attempted (`kbb -e '(println (integer?
  1e17) … #?(:clj :clj-branch :cljs :cljs-branch))'`) and produced no usable
  stdout before budget — NOT measured. The reader-conditional feature kbb
  selects (:clj vs :cljs) and kbb's `integer?` semantics for 1e17 remain
  UNMEASURED and must be probed first next tick (they decide whether Gap 1
  fires on the kbb leg).

## Carry-forward (unchanged from the 14:2x correction; do not re-litigate)

Implement wire-number-canon in src/torihiki/log.cljk only, per the scoped
design in evidence/2026-11-03-0222-…-scoped-not-landed.md as CORRECTED by
evidence/2026-11-03-142x-…-not-landed-baseline600.md:

- enc-val ladder: int-like + in-domain -> bare long (unchanged); int-like
  otherwise -> ["u" <exact digits>]; finite fractional number -> ["d" <
  exact plain expansion>] (JVM (.toPlainString (BigDecimal. (double v))),
  nbb BigInt binary-decomposition m*2^e -> m*5^(-e) digits; special-case
  +/-Inf -> "Infinity"/"-Infinity" strings — BigDecimal of Inf throws);
  NaN -> loud symmetric refusal (falls through to the existing "no wire
  form" throw on both runtimes — pin it); everything else unchanged.
- dec-val tags "u" (JVM (bigint s), JS (js/Number (js/BigInt s))) and "d"
  (Double/parseDouble / js/parseFloat).
- signing-payload: total kind helper (keyword -> name, string as-is, else
  number canon / str) and the slots through the same number canon (digit
  strings, never `str`'s scientific form). Compat check done by code-read
  this tick: no OLD committed block can carry a fractional double (enc-val
  threw on all runtimes) or a JVM out-of-domain whole double (threw on the
  JVM), so the only old payloads the new rendering must match byte-for-byte
  are nbb-committed whole out-of-domain doubles (String(1e17) = the digit
  string) — no signature-compat break.
- Pin at the WIRE level, never type-sensitive `=` across a submitted double
  and a decoded BigInt: wire literals ["u" "100000000000000000"] for
  {:claim 1e17}, ["d" "5.5"] for 5.5; wire-idempotence
  (= (tx->wire tx) (tx->wire (wire->tx (tx->wire tx)))) over 1e17 / -2.5e18
  / 5.5 / 0.1 / -0.0 / 1e-300 / 1.5e300; value pins for the "d" set
  (same-type doubles) and for "u" via digit strings ((str decoded) =
  "100000000000000000" works on both); in-domain 5.0 stays bare 5; -0.0
  pins through canon-tx to 0 and wire {"claim" 0}; NaN refused; signing-
  payload literal for {:account 1e17}; kind {:tx 5} renders total.
- Each new pin run BROKEN first on all three legs (kbb -M:test,
  script/jvm-test.sh, script/nbb-test.sh — the last with
  KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang), recording the
  ACTUAL observed red mode per leg (do not predict: the 02:2x "Gap 1 red on
  kbb+JVM" is a prediction and kbb is SCI-on-Node, so kbb may follow JS
  `integer?` semantics instead).

## Not run / not claimed

Baseline suite, BROKEN runs, all three legs, the same-count claim, the JVM
leg, the nbb leg: NOT RUN this tick. No PR. Branch kept for handoff.
