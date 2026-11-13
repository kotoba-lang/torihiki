# 2026-10-22 20:49 JST — torihiki-fix tick: validate residual i53 caps (PR #58, MERGED)

- Branch bot/torihiki-fix-20261022-2037 off origin/main b9c1ab7; merged as 0a7a5fb (PR #58, 2026-10-22T11:49:52Z).
- Fix: validate-layer residual i53 caps (falsify-6/7/8 family) for fields checked by integer?/pos? alone and then STORED into state: :anchor account, :create-sub-account :sub, :scale :level, :twap :every, :attest-reserves :amount/:height*, :upgrade-propose :version/:activation, :vault-withdraw :shares. Engine-side no-op safety nets for :anchor/:create-sub-account/:twap/:attest-reserves; fire-twaps :next-at sum via fx/sadd.
- Measured (kbb -M:test, ONE runtime — JVM renamed-copy leg and nbb leg NOT RUN this tick):
  - BROKEN first: 502 tests / 3504 assertions / 13 failures (the 13 new assertions in 2 new deftests, test/torihiki/overflow_gates_test.cljk).
  - GREEN after: 502 tests / 3504 assertions / 0 failures, 0 errors.
- Host load at tick start: 17.63 / 24.00 / 30.13 (not all windows > 20 — gate condition per SOUL not met, tick proceeded).
- Left for next ticks (measured shape, not yet fixed):
  1. Vault share products clearing.cljk:286/:313 — bare (* a b) with in-domain quotient, out-of-domain product (falsify-11 secondary class); needs exact floor(a*b/c) primitive.
  2. :scale rung (+ level (* dir i step)) at Long/MAX — overflow-unsafe where + is checked; kbb rounds silently (measured: (+ Long/MAX 1) = 9223372036854776000), so no red-able engine-guard test on this runtime.
  3. NEXT reproducibility item unchanged: HEAD bench n=1M 3x stable (cancelled>0) under all-windows<20 — not run this tick (5/15-min windows at 24/30).
