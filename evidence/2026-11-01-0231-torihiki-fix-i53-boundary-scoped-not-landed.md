# 2026-11-01 02:3x torihiki-fix tick — NOT LANDED (run budget exhausted before
# the BROKEN run). Package scoped, baseline measured, one test-file edit made;
# no src change, no PR. maturity.md untouched (measurement bots' file).
# "not run" != "passed": nothing is claimed green here beyond the baseline.

Load gate: torihiki_state.sh 02:17 — 16.49 / 18.66 / 19.80, all windows < 20,
gate passed.

Worktree: ~/github/wt/fleet/bot-torihiki-fix-20261101-0231, branch
bot/torihiki-fix-20261101-0231 off origin/main 896133b (PR #85 merge,
consensus-totality). KEPT as handoff — next tick starts here.

## Package chosen (highest-leverage UNLANDED item)

PR #85's named residual, i.e. the remaining validate-side piece of maturity.md
NEXT ② (the falsify-7/8 "api/validate :bad-amount i53 検査" sharpening):
"an out-of-domain integer :subject/:proposal key is admitted by BOTH doors and
remains a garbage key reachable through validated traffic — that disagreement
is validate's own, to be fixed with the falsify-7/8 package."

Scope (surveyed at 896133b, all findings from direct code reads):
- Cap 5 integer slots at the i53 boundary (fx/i53-min..fx/i53-max) in BOTH
  doors (api/validate + api/store-tx-shape, word-for-word, same reason
  keywords as today): :upgrade-vote :proposal (:missing-field),
  :principal-confirm :subject and :principal-rotate :subject (:bad-account —
  a subject is an account, so account-ok?), :equivocation-evidence
  :evidence-height and :view (:missing-field). The [witness evidence-height
  view] triple is the :slashed state-root KEY (torihiki.validators/evidence-key)
  — a genuine falsify-8 landing class (a vouching vote-verify-fn + garbage
  height keys :slashed with 2^53+1 on the JVM and 2^53 on JS).
- Verified INERT / out of scope (do not re-litigate): :set-leverage :leverage
  is transitively capped (cl/set-leverage refuses lev > :max-leverage which is
  spec-capped 1..i53-max); :twap :slices is transitively capped (slices <= qty
  <= i53-max); :builder-fee capped by max-builder-fee-rate; :count* capped
  2..50; the names class stays integer? PINNED AS ADMITTED elsewhere —
  :withdraw-* :claim (attest_totality_test.cljk "an out-of-domain INTEGER
  claim names nothing but is not refused"), :cancel-trigger/:cancel-twap :id
  (agent_trigger_totality_test.cljk "out-of-domain id is still an integer").
- The three consensus_totality_test parity rows that pin the OLD residual
  (expected nil) must flip with the fix: "upgrade-vote: out-of-domain id is
  still an integer", "principal-confirm: out-of-domain subject is still an
  integer", "equivocation-evidence: out-of-domain height is still an integer"
  — plus new rows (:view ood, :principal-rotate :subject ood) and boundary
  guards (i53-min/i53-max names still pass). Pin flips are intentional and
  ride with the fix (the rows pin the residual this package removes).

## What ran (exact counts)

- Baseline GREEN kbb/SCI (engine + tests unmodified): 586 tests / 5015
  assertions / 0 failures / 0 errors, exit 0 (/tmp/tfx-baseline.{out,err}).
  Matches PR #85's landing record exactly.
- BROKEN run: NOT run (budget). GREEN: NOT run. JVM leg: NOT run. nbb leg:
  NOT run. Nothing new is claimed passing.

## State left in this worktree (handoff)

test/torihiki/consensus_totality_test.cljk: ns docstring updated (the
"mirror does not sharpen" paragraph replaced by the i53-boundary rule and the
names-class boundary, citing attest/agent-trigger pins). NOTE: one patch pass
escaped the docstring terminator (\" at end of the ns docstring) and a
follow-up patch fixed it back to a bare closing quote — VERIFY the ns form
reads before running (the file should load; if it does not, the fix is the
last line of the docstring).

## Next tick starts here (unchanged from the plan above)

1. Finish the test edits: flip the 3 parity rows (nil -> the field's existing
   word), add rows for :view ood / principal-rotate :subject ood, add
   i53-min/i53-max boundary guards, add VALUE deftests (one fixture PER CASE):
   seeded garbage-key claim + confirm binds nothing; seeded garbage-key
   binding + rotate keys nothing; seeded garbage-key proposal + vote mutates
   nothing; vouching-verifier evidence at ood height or view keys :slashed
   with nothing and mints nothing. Also fix the deftest comment above
   the-two-doors-answer-with-the-same-word ("validate is unchanged by this
   package... the door does not sharpen" is stale once the fix lands).
2. BROKEN `kbb -M:test` (expect the flipped rows + new VALUE pins red at pin
   level; baseline 586/5015 must stay green outside the edited file), record
   raw out/err.
3. Fix src/torihiki/api.cljk: the 5 slots in validate AND the same 5 in
   store-tx-shape (clauses at validate ~689/:697/:703/:727-728 and mirror
   ~1279/:1318/:1324/:1352-1353), same order, same keywords; update the
   stale "integer?, not in-domain" comments in both places.
4. GREEN `kbb -M:test`, then script/jvm-test.sh and
   KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang script/nbb-test.sh
   — never claim both runtimes from one; the JVM leg runs on the renamed copy.
   No tests-on-nbb.cljk change needed (no new namespace; floor stays 34).
5. Land: commit, push, gh pr create --base main, merge --delete-branch,
   git worktree remove, delete the local branch.

maturity.md untouched. Host load at tick end: not re-measured.