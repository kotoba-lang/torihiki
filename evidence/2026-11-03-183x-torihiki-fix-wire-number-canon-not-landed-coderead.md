# 2026-11-03 18:3x torihiki-fix tick — NOT LANDED (run budget exhausted after
# environment + handoff reconciliation + full code-read of the implementation
# surface). No src/test change, no PR. "not run" != "passed": no suite leg, no
# BROKEN run, no probe was completed this tick.

Load gate: torihiki_state.sh 18:21 — 1.44 / 2.10 / 2.09 (uptime direct 18:18
— 1.65 / 2.46 / 2.20), all windows < 20, gate passed.

Worktree ~/github/wt/fleet/bot-torihiki-fix-20261103-1837, branch
bot/torihiki-fix-20261103-1837 off origin/main **3ab13e5** — NOTE the base
moved this tick: PR #89 (squash of the 02:2x/08:1x/14:2x scoping evidence)
is MERGED, so the wire-number-canon scoped design + expansion-probe finding
now live on main. The 0222 handoff branch (3d17a68) is fully absorbed; the
1621 branch (f86baf5, env-verification evidence) is still unmerged but its
content is superseded by what is below. maturity.md untouched.

## Measured this tick (exact)

- gh auth still DEAD at 18:37 (`gh auth status`: "The token in default is
  invalid") — PR create/merge via gh BLOCKED as per the 16:2x finding; the
  branch push is the handoff channel (`git -c credential.helper=osxkeychain
  push`).
- kbb present at /Users/junkawasaki/github/kotoba-lang/kotoba/bin/kbb; clojure
  (openjdk 27 keg-only) and node on PATH. Nothing else run.

## Code-read this tick: the exact implementation surface (anchors at 3ab13e5)

src/torihiki/log.cljk (316 lines):
- enc-val ladder at 88–112: `(and (api/int-like? v) (fx/in-domain? v)) (long v)`
  (97), then `(integer? v) v` bare (98 — Gap 1's JVM fall-through lives here:
  out-of-domain whole doubles pass 97's in-domain? gate and land on the TYPE
  test at 98), then string/keyword["k"]/boolean["b"]/nil["n"]/map["m"]/
  vector["v"]/set["set"], else throw "torihiki.log: value has no wire form"
  (112 — Gap 2's fractional-double halt lands here on all legs).
- dec-val at 114–128: tag case "k"/"b"/"n"/"m"/"v"/"set", bare `:else w`.
  Add "u" and "d" here.
- tx->wire at 130–159: kind rendering already total for keyword/string
  (plain keyword -> name string; namespaced -> enc-val; string -> ["s" ...];
  else enc-val) — a numeric kind ALREADY round-trips through tx->wire; only
  signing-payload's `(name (:tx t))` throws (213).
- signing-payload at 196–216: runs `(api/canon-tx t)` then
  `(name (:tx t))` + `str` of the 9 slots (account/market/side/level/qty/
  flags/oid/amount/price). Gap 3 exactly: `(str 1e17)` = "1.0E17" JVM vs
  "100000000000000000" on the JS legs; `(name 5)` throws.

test/torihiki/log_test.cljk (202 lines): existing pins reviewed line by
line — wire-round-trip (34), wire-covers-the-engine-vocabulary (38),
wire-round-trips-what-the-engine-rejects (65), wire-rejects fn pin (82),
malformed-into-root (90), chain/replay/tamper (106–155), signatures (167–202).
**NO existing pin commits an out-of-domain number or a fractional double
through the wire** — every pinned value is in-domain integral — so the
planned enc-val ladder change (int-like out-of-domain -> ["u" digits],
fractional finite -> ["d" expansion]) disturbs none of them. The fn pin at
82 (loud refusal for non-EDN values) and the `["s" ...]` string-kind
distinction stay as they are.

## Open risk NOT checked this tick (budget) — next tick MUST check first

- test/torihiki/fuzz_seeded_test.cljk and script/kotoba-parity.cljk digests:
  if any seed/fixture carries an out-of-domain number or fractional double
  through tx->wire, the encode ladder change shifts the pinned byte digests.
  Grep the seeds for large/fractional literals BEFORE changing enc-val; if
  present, the digest baselines must be re-pinned as part of this package
  (and that is a counted, deliberate change — not a silent drift).
- The corrected design's "u" decode (JS `(js/Number (js/BigInt s))`) loses
  exactness for a js/BigInt SUBMISSION above 2^53 (re-encodes to the nearest
  double's digits, breaking the wire law for that input class on the JS
  legs). The scoped pin space is doubles only (1e17, -2.5e18, 5.5, 0.1,
  -0.0, 1e-300, 1.5e300) and is unaffected — every double's exact digit
  string round-trips through Number exactly. Decide at implementation time:
  keep js/Number per the corrected design and document the BigInt-submission
  caveat, or decode "u" to js/BigInt (exact for all inputs; safe because
  decoded "u" values are always out-of-domain and never computed with).

## Carry-forward: implementation order (unchanged in substance)

1. probe-nums5 on kbb: isolate the drafted BigInt-loop corruption, settle
   the :cljs "d" expansion route (BigInt .pow one-shot or pure digit-string
   x5-with-carry), re-verify byte identity against the 0817 evidence's JVM
   literals (0.1's 55-digit expansion is the contract).
2. Fuzz/parity digest grep (the risk above).
3. log_test.cljk pins per 0222 design + 142x wire-level-law correction +
   0817 literals: wire literals ["u" "100000000000000000"] / ["d" "5.5"] /
   ["d" "0.1000...5625"], wire-level round-trip law (NOT type-sensitive `=`
   across numeric categories), -0.0 IS bare 0, in-domain 5.0 stays bare 5,
   signing-payload literal for :account 1e17, kind helper {:tx 5} no-throw +
   :a/y renders "a/y", NaN stays a loud symmetric refusal, fn pin unchanged.
4. BROKEN once per leg where the red fires (record observed modes per leg —
   predicted reds: Gap-1 pin throws on the JVM leg and mis-pins on the JS
   legs; Gap-2 pins throw everywhere; Gap-3 payload pin red where `str`
   diverges — do not cite predictions as measurements).
5. GREEN kbb -M:test, then script/jvm-test.sh (renamed copy) and
   KOTOBA_CHECKOUTS=... script/nbb-test.sh — never claim both runtimes from
   one. Baseline to beat: 600 tests / 5216 assertions (PR #88/#89 green).
6. Land: commit, push, PR, merge --delete-branch, worktree remove.
