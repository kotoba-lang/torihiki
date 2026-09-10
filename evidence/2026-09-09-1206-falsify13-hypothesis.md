# falsify-13 hypothesis (registered pre-run, 2026-09-09 12:06 JST)

- target: `:insurance-fund` accumulation — liquidation.cljc:195 `(update :insurance-fund (fnil + 0) fee)` — the last unmeasured accumulation site (falsify-12 verdict listed it as (b)).
- class: falsify-8/9/10/11/12 (delta-checked / sum-unchecked). `fee` is i53-checked inside `fx/mul-rate` (fixed.cljc:103), the accumulator sum is not.
- reachability: `:insurance-fund` is encoded into `state-root` via `encode-clearing-totals` (state.cljc:1601) → a sum crossing onto a double-irreversible value should silently diverge JVM/nbb roots, throw-free.
- prediction: per-event fee = fdiv(N·F, 1e9) with F = 10 bp = 1e6 → fee = N/10⁴; honest per-event ceiling ~9.2e8 (N capped by N·F < 2^63 at ~9.2e12), so honest crossing needs ~10⁷ events (probe B measures). Seeded crossing (probe C, f9/f10/f12 labelled-seed method): seed fund = 2^53+1 − fee with fee odd → one real stage-1 liquidation lands the sum exactly on 2^53+1 (odd, double-irreversible) → JVM keeps the exact long, nbb rounds → root divergence, zero throws.
- fallback: if probe A's fee is even, adjust N until odd.
- runtimes: JVM via load-file; nbb via pre-require + load-string driver (nbb-classpath bootstrap with `--classpath ../text/src`, frame-394 finding).
- src/ and test/ untouched; no code changes; evidence only.
