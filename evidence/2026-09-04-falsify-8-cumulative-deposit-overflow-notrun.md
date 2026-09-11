# falsify-8 (NOT-RUN: host load gate) — cumulative deposit overflow makes planned fix (a) insufficient

- date: 2026-09-04 02:32–02:40 JST
- iteration: falsify-8 (hypothesis registered, measurement deferred)
- gate: host load 27.45 / 21.26 / 18.58 (1/5/15-min, `uptime` 02:32) — cron rule "load > 20 → not-run evidence only". Both 1-min and 5-min above 20 (likely residual from remeasure-0231 suite runs finished 02:31). No runtime measurement performed this iteration.

## Hypothesis (falsify-8)

**Planned fix (a) — per-tx `:bad-amount` i53 cap on `:deposit`/`:order` (NEXT, maturity.md) — is insufficient: two in-domain deposits, each ≤ i53-max and each passing that cap, sum the account collateral past the i53 domain, because `cl/deposit` domain-checks only the incoming amount and not the resulting balance.**

Extension of falsify-7's finding ("halt は域外入力を必要としない") to: *halt は域外な per-tx 入力すら必要としない* — cumulative state can escape the domain even when every individual tx is in-domain.

## Code grounding (read-only, 2026-09-04)

- `clearing.cljc:714–726` `cl/deposit`: `amount` passes `fx/check :deposit` (:721 — only validates the amount itself), then credits via `(update-in [:accounts acct :collateral] (fnil + 0) (- amount repaid))` (:726) — **no fx/check on the sum**. The deficit-repay branch (:725) is likewise unchecked.
- `state.cljc:166–171` `apply-tx :deposit` → `cl/deposit` — nothing downstream in the deposit path re-checks collateral.
- `api.cljc:199–237` `:deposit` validation: `:bad-amount` is `(and (integer? amount) (pos? amount))` (:202) — no i53 cap (falsify-6); the planned fix (a) adds a per-tx cap here but has no state-level gate.

Predicted measurement (next iteration, both runtimes, seeded/byte-identical expectations as falsify-6/7):

1. deposit `amount = i53-max` → collateral = i53-max (in domain, passes planned (a)).
2. deposit `amount = i53-max` again → collateral = 2×i53-max, silently in state (no throw at :726 — no check there).
3. Expected halt path: any subsequent `fx/check` that consumes the escaped collateral (margin/equity, funding, or the next deposit's arithmetic) throws `torihiki.fixed: value escaped the i53 domain` at the same position on JVM and nbb — same contract break as state.cljc:1118. **Which fx/check site fires first is the open question the measurement must answer** (candidates: fill-time equity/margin, `fx/notional` consumers, `deficit` at clearing.cljc:711).
4. Minimal fix implication: (a) alone leaves a 2-tx in-domain halt; fix must add a **balance-domain gate** — reject (`:bad-amount` or a dedicated code) any deposit where `collateral + amount > i53-max` (state-dependent validate, or fx/check the sum in `cl/deposit` and treat throw as no-op like `cl/withdraw`). Same class of gate likely needed for `:deficit` accumulation (clearing.cljc:711) and fee pooling — out of scope for falsify-8, noted for later.

## Verdict

NOT-RUN (hypothesis only). falsify-7's conclusion stands unchanged; NEXT gains the balance-gate requirement **only after** falsify-8 is measured — do not amend the fix plan on an unmeasured hypothesis.

## Next iteration

With load < 20: build falsify8 driver on the falsify7 pattern (`evidence/falsify7-jvm-driver.cljk` / `falsify7-driver.cljs`, nbb via `script/nbb-classpath.cljk`), run steps 1–3 on JVM + nbb, write `falsify8-{jvm,nbb}.out`, then register verdict in maturity.md and amend NEXT.
