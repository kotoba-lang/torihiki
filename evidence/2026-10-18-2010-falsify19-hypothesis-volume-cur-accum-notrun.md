# falsify-19 hypothesis (registered 2026-10-18 20:10 JST) — :volume :cur accum overflow — MEASUREMENT NOT RUN (run budget exhausted)

## Run context
- Load gate PASSED at registration: 2026-10-18 20:09, load averages 12.26 22.65 45.49 (1-min 12.26 < 20, frame-549/561 1-min gate precedent). This is NOT a load-gated not-run.
- HEAD 1ceb357. No code changes made (evidence only, per cron contract).
- The cross-runtime measurement (JVM + nbb drivers) did NOT execute this run: the
  iteration's execution budget was exhausted during source reconnaissance. The
  verdict for falsify-19 is therefore UNMEASURED. Do not cite this file as a
  confirmation or a refutation — it registers the hypothesis and the verified
  static findings so the next run can execute the measurement directly.

## Hypothesis (falsify-19)
`:volume acct :cur` (clearing.cljk:629, `(update rec :cur (fnil + 0) notional)`) is a
sum-unchecked accumulation of per-fill traded notional — the falsify-8/9/10/11/12/13
class at a previously unmeasured site — and unlike :open-interest it IS folded into
the state root (encode-clearing-account, state.cljk:~1644 encodes `epoch cur prev`).
Two in-domain fills by one account inside the same volume epoch (epoch = quot(height,
20000), clearing.cljk:586-599) can accumulate :cur onto exactly 2^53+1 (odd,
double-irreversible): JVM keeps the exact long, nbb (JS double) rounds to 2^53 →
cross-runtime state-root divergence at the volume leaf, throw 皆無 expected.

## Static findings verified this run (source reads, not runtime measurements)
1. Production call site: state.cljk:510 `(update :clearing cl/add-volume taker-owner h notional)`
   with `notional = (fx/abs* (fx/notional level qty))` (state.cljk:482) — the per-fill
   value is fx/notional-checked (in-domain), the SUM at clearing.cljk:629 is not.
2. Root folding confirmed: encode-clearing-account (state.cljk:~1635-1650) encodes
   account `epoch cur prev` from `[:volume a]` — a :cur crossing is consensus-visible.
3. Read side also escapes: volume-of (clearing.cljk:601-613) computes `(+ cur prev)`
   and passes `cur` through on the `(= epoch (dec e))` branch — after a crossing, the
   value feeding fee-rates-for tier selection (clearing.cljk:636-653) diverges across
   runtimes too (fee-tier choice divergence on top of root divergence).
4. Secondary, root NOT folded: `:open-interest` accumulation (clearing.cljk:400,
   `(update-in [:open-interest mkt] (fnil + 0) oi-delta)`) is also sum-unchecked, but
   encode-clearing-totals (state.cljk:1598-1602) encodes only :insurance-fund,
   :fees-collected, :funding-residue — OI is absent from the root. OI feeds only the
   api OI-cap acceptance check (api.cljk:123-125; cap is optional via when-let, so an
   uncapped market has no gate at all). A double-rounded OI there yields silent
   order-ACCEPTANCE divergence (one replica takes an order the other refuses) rather
   than root divergence — a different failure mode, worth its own probe later.
5. Honest crossing distance: per-fill notional is capped in-domain by fx/notional, so
   an honest 2^53+1 landing on :cur needs ≥ 2 fills of i53-max notional + 1 small fill
   (2×i53-max = 2^54−2 even/reversible; +3 lands 2^54+1) — or the seeded 2-event
   method below. Seeded method is the falsify-12/13 precedent and is cheap.

## Planned measurement (NEXT run — execute, do not re-derive)
- Seed method (falsify-12 per-event reseed precedent): set
  `[:volume 4] = {:epoch 0 :cur (- 9007199254740993 (* 2 N)) :prev 0}` with N = 1.8e9
  (level 1000 × qty 1_800_000, both in-domain; N·F < 2^63 for any sane fee-rate),
  then drive TWO real fills through the production cross path (st/apply-block,
  state.cljk:487-510) at heights h < 20000 (same epoch 0) so add-volume accumulates
  :cur to exactly 2^53+1.
- Measure on BOTH runtimes (JVM `clojure -M` driver + nbb 2-stage classpath driver,
  harness pattern evidence/falsify13-{jvm,nbb}-driver.cljk): :cur value, state-root,
  throw count. Expect: JVM cur = 9007199254740993 (2^53+1), nbb cur = …992 (rounded),
  root1/root2 mismatch, throws = 0. Also snapshot volume-of output to show the read
  side diverging.
- No try/catch in the harness file (falsify-6..13 rule); drivers own catching; nothing
  in src/ or test/ is touched.

## Status
UNMEASURED. Registered so the next falsify iteration executes the probe instead of
reconnoitering. Ledger entry pending until the measurement runs.