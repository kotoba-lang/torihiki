# 2026-10-24 04:3x torihiki-fix tick — NOT-RUN (runtime budget exhausted; no code landed)

- Load gate PASSED: torihiki_state.sh + uptime direct 04:18 = 1-min 17.44 / 5-min 14.02 /
  15-min 13.38, all windows < 20. Not a host-load skip.
- origin/main = b6b4cb4 (PR #60). Worktree created clean at
  ~/github/wt/fleet/bot-torihiki-fix-20261024-0432 (branch of same name), zero edits —
  removed on exit; next tick re-creates fresh.
- Audit (read-only) of maturity.md NEXT against landed code: every named fix package is
  ALREADY LANDED and the maturity.md NEXT line is stale relative to main —
  validate i53 caps + notional cap (PR #52/#58, api.cljk:121-133 `:bad-amount` division-
  form cap), balance-domain deposit gate (clearing.cljk:737+ refuse-not-throw), sum gates
  via fx/acc sweep (PR #56), exact mul-rate + pmul (fixed.cljk:142-246), settle-deficit
  capped move (clearing.cljk:722-735), merkle aggregate saturation (PR #54), rate caps +
  book-opts sizing at validate (PR #55), fuzz suite 常設化 (PR #53), bench 3-part fix +
  HEAD n=1M 3x stable repro3 (PR #59), JVM test leg script/jvm-test.sh (PR #60).
- FINDING (unlanded residual, same measured class as falsify-9/11 secondaries — raw
  product of two in-domain values before a division: JVM bare overflow = halted chain at
  >2^63, JS silent rounding = cross-runtime divergence at >2^53; kbb is SCI-on-Node so
  both failure modes are observable in-repo):
  1. funding.cljk:68   premium      (fx/fdiv (* diff rate-scale) oracle-price)
  2. liquidation.cljk:152 adl leverage (fx/fdiv (* notional rate-scale) base) — sort key
  3. liquidation.cljk:155 adl score    (fx/fdiv (* profit leverage) rate-scale)
  4. clearing.cljk:286 vault-deposit minted (quot (* amount total) pool)
  5. clearing.cljk:313 vault-withdraw payout (quot (* shares pool) total)
  6. oracle.cljk:131  deviation    (fx/fdiv (* (abs* (- price reference)) rate-scale) reference)
  7. validators.cljk:338 slash reward (quot (* total 5) 100) — >2^53 divergence only
  plus transient in-domain sums (JS-rounding fork, no JVM throw): cl/unrealized
  (clearing.cljk:119 `(- notional entry-notional)`), adl base (liquidation.cljk:151
  `(+ collateral profit)`), state.cljk:1526 quote-fn `(- collateral entry-notional)`.
  Ruled SAFE: api.cljk:169-170 spot-amend-funds products (amend runs validate-order-shape
  incl. the level×qty cap first — api.cljk:258-267), premium's own diff (oracle prices
  validated 1..i53-max keeps it in-domain).
- Intended package (next tick): `torihiki.fixed/mul-div` — exact floor(a·b/c) computed
  without materializing the product (double-word long division, every intermediate <2^53,
  quotient saturates at the domain bound), total, no throw on the apply path — rewiring
  sites 1-7, plus fx/sadd at the three transient diffs; tests pinning VALUE (exact pins
  verified offline: premium pin -1000000000 with ib=ia=1/op=i53-max; adl leverage
  999999999 / score 9007199254731990 with size=2^52-1/mark=2/coll=1; mul-div exact
  (2^52-1)^2/(2^52-2)=2^52; slash reward 450000000000001 with total=9000000000000020 —
  engineered so the pre-fix JS quotient lands at 450000000000000, ties-to-even at ulp 8).
- Tests this tick: none run (0 run, 0 green). Suite baseline not re-measured this tick.
