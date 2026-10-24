# 2026-10-25 08:24 torihiki-fix tick — LANDED: fx/fmul, the exact floor(a x b / c)

Load gate PASS (9.95 / 10.12 / 10.14). Resumes the package scoped in
evidence/2026-10-23-1219 and re-confirmed in evidence/2026-10-25-0620 (the
in-flight worktree holding that diff was gone from disk; reimplemented here,
pins re-verified exact with Python integer math before any test was written).

## Package
fx/fmul: exact floor(a*b/c) without forming the product, saturating to the
i53 domain, floor semantics on every sign, total (never throws on the apply
path; zero divisor is a code-contract ex-info, unreachable at every site).
Schoolbook on the bits of b: running value q*c + r, 0 <= r < c, carries
decided by comparison (r >= c - r), q saturated — no intermediate leaves the
domain on either runtime.

Sites routed through fmul (the product-then-divide class):
- clearing REDUCING closed-basis (was fdiv of a pmul-CLAMPED product: basis
  4503599 instead of 360e12 on falsify-11's numbers)
- clearing vault-deposit minted (was quot of the raw product)
- clearing vault-withdraw payout (same)
- liquidation adl-ranking leverage + score (was bare * + fdiv)
- validators slash reward (was quot of the raw product)
- oracle deviation (clamps to i53-max now; used to return out-of-domain 7.1e23)
- funding premium (same class; in-domain results byte-identical)

## Measured (exact counts)
- RED-0 (fmul absent): `kbb -M:test` aborts at load — "Unable to resolve
  symbol: fx/fmul"; every new test ran broken.
- RED (fmul present, sites unfixed): `kbb -M:test` 520 tests / 3573
  assertions / 7 failures / 0 errors (/tmp/red-fmul-kbb.out). Old values
  measured: vault minted 4913264919471392 (pin ...391); reducing basis
  719995496400001-ish retired / collateral 720995496400001 (pins 360e12 /
  360e12); adl score 1515362229667057 (pin ...056); deviation 7.142857142857144e+23
  (pin 9007199254740991); slash reward 360287970189640 (pin ...641);
  insurance-fund 6845471433603180 (pin ...179). Honest note: the
  vault-WITHDRAW payout assertion passed at RED on kbb (double rounding did
  not cross there); it ran broken at RED-0 and is a regression guard on kbb.
- GREEN: `kbb -M:test` 520 tests / 3573 assertions / 0 failures / 0 errors
  (exit 0).
- GREEN: `bash script/jvm-test.sh` (renamed copy leg) 520 tests / 3573
  assertions / 0 failures / 0 errors (exit 0).
- NOT RUN (budget): `bash script/nbb-test.sh`, parity legs. Two of the three
  runtimes measured green from one source tree; nbb not claimed.
