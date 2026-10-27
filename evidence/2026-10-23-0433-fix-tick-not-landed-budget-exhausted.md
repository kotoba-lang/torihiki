# 2026-10-23 04:33 torihiki-fix tick — NOT LANDED (runtime budget exhausted)

Load gate: 04:18 direct 12.16 / 10.52 / 10.22 — all windows < 20, gate passed.

Measured: fresh worktree `bot/torihiki-fix-20261023-0433` at origin/main (0a7a5fb),
`kbb -M:test` baseline **502 tests / 3504 assertions / 0 failures / 0 errors, EXIT=0**
(/tmp/tv-base.out, /tmp/tv-base.err).

Finding (code inspection, NOT yet reproduced by a failing test run — treat as
unmeasured): the maturity.md NEXT ② fix package, the 3-part bench fix and fuzz-suite
permanence are ALL landed at 0a7a5fb (PRs #52–#58: validate caps, balance-domain
gate, fx/acc accumulator sweep, exact mul-rate, REDUCING fx/pmul, rate caps,
settle-deficit clamp, bench cancel! 3-arg, fuzz in-suite). The remaining NEXT items
are measurement/reproducibility (① re-anchor, HEAD 3x n=1M bench).

Residual falsify-7-class halt door identified for the next fix tick: `fx/notional`'s
`check :notional` throw is reachable mid-apply via price x AGGREGATE-size products
(validate caps bound only per-order `level x qty`). Sites: clearing.cljk:119
(unrealized), :145/:153/:155/:167/:401 (apply-fill incl. liquidation slice deltas),
:445/:478/:519 (margin), funding.cljk:115 (payment), liquidation.cljk:66/:150/:202
(slice-size / adl-ranking / stage-1 fee), order_margin.cljk:126/:127/:128/:253/:254.
Reachability shape: an i53-max-scale position built from two validated level-1
orders, then mark/oracle >= 2 — end-of-block sweep computes mark x size > i53-max
and throws inside apply-block (contract: "never applied and never thrown from").
Planned fix: swap those sites to saturating `fx/pmul` (clamp = the house verdict for
products, see fx/pmul docstring); keep checked `fx/notional` at validated per-order
sites (clearing.cljk:558, state.cljk:610/:711/:788/:814/:1539/:1558/:1570,
book.cljk:914, order_margin.cljk:125/:132/:133). Broken-first tests designed (pins
hand-derived, must be re-derived mechanically before landing: unrealized clamp
i53-max-3e15, equity, maintenance-margin at pct 5, REDUCING/FLIP apply-fill state,
funding payment/residue, liq/slice-size 6e14, om/requirement = i53-max).

Not landed, no PR opened this tick. Worktree removed after this note; branch deleted.
