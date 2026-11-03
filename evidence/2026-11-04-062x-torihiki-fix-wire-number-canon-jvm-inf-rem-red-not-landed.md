# 2026-11-04 06:2x torihiki-fix tick — NOT LANDED (run budget exhausted after
# 2 GREEN-leg measurements + root-cause of the JVM red). "not run" != "passed"
# — the only greens claimed below are the ones counted this tick. maturity.md
# untouched (measurement bots' file).

Load gate: 06:18 uptime direct — 1.98 / 1.46 / 1.33, all windows < 20, gate
passed (torihiki_state.sh absent on this host, profile scripts dir empty —
direct uptime per the chronic-fault precedent). Host jacob.local.

Worktree: continued the KEPT handoff ~/github/wt/fleet/bot-torihiki-fix-
20261104-0028 (branch bot/torihiki-fix-20261104-0028, wire-number-canon tests
+ implementation from the 00:28/04:2x ticks). Merged origin/main (2201d03,
venue JUN-807) into the branch — clean merge, 34006b9 — so every counter
below is measured on the state that would land.

## Measured this tick (exact)

- kbb GREEN (`kbb -M:test`, merged branch, tests + engine as committed by
  the 00:28 tick): **631 tests / 5372 assertions / 0 failures / 0 errors**,
  KBB_EXIT=0 (/tmp/wnc3-green-kbb.{out,err}). 608/5250 was the pre-merge
  branch; +23/122 is the venue suite from origin/main. The wire-number-canon
  pins are GREEN on kbb/SCI.
- JVM GREEN attempt (script/jvm-test.sh renamed copy + dep shims): **631
  tests / 5371 assertions / 0 failures / 5 ERRORS**, JVM_EXIT=1
  (/tmp/wnc3-green-jvm.{out,err}). NOT green. All 5 errors are ONE root
  cause, measured from stack traces: `api/int-like?` (api.cljk:88) judges
  whole-ness with `rem`, and the JVM's `Numbers.remainder` is
  BigDecimal-backed — `(rem ##Inf 1)` THROWS
  `NumberFormatException: Infinite or NaN`, while on JS it lands on NaN and
  the predicate answers false. The ##Inf/##-Inf pins (fractional-numbers-tag-d
  x2, the-wire-round-trip-is-idempotent doseq, decoded-numbers-recover x2)
  reach `int-like?` through `enc-val` and die before the "d" branch. This is
  the falsify-6/7 asymmetry class one layer down: it is not only the codec —
  `validate`'s own doors call `int-like?`, so a signed tx carrying an
  infinity halts the JVM sequencer inside apply-block while the JS leg
  answers `:bad-amount`. The kbb leg hides it (its `rem` never throws).
- Assertion-counter gap 5372 vs 5371 = the idempotence doseq aborted at its
  first ##Inf on the JVM (0 of 2 remaining assertions ran).

## Written this tick (UNVERIFIED — no run of any kind since)

1. test/torihiki/log_test.cljk: new deftest
   `an-infinite-amount-is-rejected-into-the-root-not-thrown` — end-to-end
   liveness pin: a block carrying :amount ##Inf and ##-Inf COMMITS, each tx
   lands `:rejected` `:bad-amount` into the root, replay agrees. Predicted
   red pre-fix on the JVM leg (NFE from int-like? inside apply-block) and on
   kbb pre-0028-fix (no wire form); NOT RUN broken, NOT RUN green.
2. src/torihiki/api.cljk `int-like?`: portable finiteness guard
   `(== (- v v) 0)` before `rem` (false exactly for NaN/±Inf, true for every
   finite number — no interop, same words both runtimes). This is the fix
   for the measured JVM red above. UNVERIFIED.

Expected next-tick green counters: **632 tests / 5375 assertions / 0F / 0E
on every leg** (631/5372 + the new deftest's 3 assertions). The 0028 tick's
BROKEN baselines were 608/5250 kbb (4F/20E) and 608/5248 JVM (2F/25E) at
the pre-merge state — the 8 canon deftests were red-first there; the 2 guard
pins (in-domain-stay-bare, nan-refused) were green before and after by
design (regression guards). nbb leg: NOT RUN this tick and NOT RUN broken
ever (the 0028 tick's records cover kbb + JVM only).

## Not run / not claimed

The new deftest (broken or green), GREEN re-runs of all three legs after the
int-like? fix, the nbb leg (KOTOBA_CHECKOUTS=/Users/junkawasaki/github/
kotoba-lang script/nbb-test.sh) at all, the same-count cross-runtime claim,
and LANDING (PR/merge): NOT RUN. gh auth is still dead on this host ("The
token in default is invalid"); push works via
`git -c credential.helper=osxkeychain push`. PR create/merge next tick via
gh if restored, else the REST API over the keychain credential without
printing the token.

## Next tick order (remainder)

1. BROKEN the new deftest on the JVM leg (expect the NFE red — record the
   observed mode), then GREEN `kbb -M:test`, script/jvm-test.sh,
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang
   script/nbb-test.sh — expect 632/5375/0F/0E on every leg, never claim
   both runtimes from one.
2. Land: commit, push (osxkeychain), PR + merge, worktree remove. Carry the
   0028 compat note into the landing record: namespaced kind rendering in
   signing-payload changes "y" -> "a/y" (old committed blocks with a
   namespaced kind would not re-verify — such blocks are engine-rejected
   :unknown-tx but committable).
3. Discovery carry-forward (do not re-derive): `rem` on the JVM is
   BigDecimal-backed and throws on non-finites — any future whole-ness or
   mod-1 arithmetic must guard finiteness first (the `int-like?` fix is the
   pattern).
