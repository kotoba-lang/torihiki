# 2026-11-06 02:5x torihiki-fix tick — signing/wire canon package: LANDED
# (continuation of the 00:2x tick; see
# evidence/2026-11-06-002x-torihiki-fix-signing-canon-not-landed-broken.md
# for the finding, the 3 fix sites and the kbb BROKEN measurement).

Load gate: torihiki_state.sh absent on this host (chronic-fault precedent);
uptime direct 02:17 — 1.02 / 1.11 / 1.17, all windows < 20, gate passed.

Continued the kept handoff branch bot/torihiki-fix-20261106-0029 (fix in
src/torihiki/log.cljk + 4 deftests in test/torihiki/log_test.cljk, tests
BROKEN-measured on kbb by the 00:2x tick). maturity.md untouched
(measurement bots' file).

## Measured this tick (exact counts)

BROKEN, fix withheld (src/torihiki/log.cljk reverted to 867b3e3, tests
kept) — completing the three-leg BROKEN record the handoff left unrun:
- JVM (script/jvm-test.sh): **665 tests / 5595 assertions / 10 failures /
  0 errors**, JVM_EXIT=1 (/tmp/tori-0029-broken-jvm.{out,err}). All 4
  deftests red; both set pins red (the handoff's "expect it red on the JVM
  BROKEN leg" confirmed) and 2 of the 5 wire-round-trip cases red.
- nbb (script/nbb-test.sh, KOTOBA_CHECKOUTS=/Users/junkawasaki/github/
  kotoba-lang): **665 / 5595 / 8 failures / 0 errors**, NBB_EXIT=1
  (/tmp/tori-0029-broken-nbb.{out,err}) — same 8 reds as the 00:2x kbb
  leg (set pin 1 green-by-luck on SCI, red on the JVM).

GREEN, fix restored (git checkout HEAD -- src/torihiki/log.cljk), all
three legs — identical counters:
- kbb (`kbb -M:test`, PATH +/Users/junkawasaki/github/kotoba-lang/
  kotoba/bin — `kbb` is not on the cron shell's PATH; without it the nbb
  leg exits 127): **665 / 5595 / 0 failures / 0 errors**, KBB_EXIT=0
  (/tmp/tori-0029-green-kbb.out).
- JVM (script/jvm-test.sh): **665 / 5595 / 0F / 0E**, JVM_EXIT=0
  (/tmp/tori-0029-green-jvm.out).
- nbb (script/nbb-test.sh): **665 / 5595 / 0F / 0E**, NBB_EXIT=0
  (/tmp/tori-0029-green-nbb.out), "TESTS-ON-NBB: pass".

Baseline identity: 661/5582 (PR #106 landing record) + 4 deftests /
+13 assertions = 665/5595 on every leg.

## One pin re-derived (the handoff's second option, said so here)

First GREEN kbb run was 1 failure: the trigger coverage pin expected
"trigger:1:::::::::;" (ten colons) while the implementation renders
"trigger:1::::::::" (nine). Re-derived from the encoding rules: the line
is ten fields joined by NINE colons — the pre-existing green pins
("a/y:::::::::", "5:::::::::", "t:::::::::" — each nine colons, unchanged
bytes claim) and the probe's measured quote "trigger:1:2:::::::" (nine
colons with two fields spent) fix the count; filling :account spends one
colon on ":1", so eight follow. The implementation renders nine colons
for every named-only transaction exactly as before — the pin counted the
empty-tx colon string and added ":1" without spending a colon. Pin
corrected (test/torihiki/log_test.cljk, derivation in a comment beside
the pin); implementation untouched. After the correction GREEN x3 above.

Note on BROKEN coverage at assertion granularity: 10 of the 13 new
assertions were measured red on at least one leg (8 on kbb/nbb, 10 on the
JVM); 3 of the 5 wire-round-trip doseq cases were never red individually
— the deftest itself was red on every leg, and those 3 pass under the fix
because decode lands on values whose rendering is unchanged (named-only
slots).

## What landed

src/torihiki/log.cljk (from the 00:2x tick, unchanged this tick):
1. enc-val set branch sorts by (comp pr-str enc-val) — the map rule; raw
   pr-str is runtime-local (#{1e21 15.0} folded in two orders).
2. slot-str renders map?/vector?/set? through (pr-str (enc-val v)) — the
   wire canon's bytes, identical on both runtimes because the encoding is.
3. signing-payload: every entry outside the nine named slots rides after
   them as ;key=value, sorted by the printed encoding of the key — the
   payload commits to every entry (two different :trigger txs no longer
   share one payload).

script/probe-signing-canon.cljk: absent from the tree (verified this
tick; nothing to remove). The measured probe stays under evidence/.

## Left (carry-forward)

- Finding 4 of the 00:2x record stays OPEN for a later tick: a map whose
  keys collide under the canon (5 and 5.0) encodes both to bare 5 and
  decodes to one entry — the round-trip law cannot hold; refuse loudly
  per the NaN precedent.
