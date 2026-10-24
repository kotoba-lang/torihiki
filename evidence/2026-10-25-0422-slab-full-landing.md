# 2026-10-25 04:22 — slab-exhaustion fix package landing (torihiki-fix tick)

Closes the order-slab halt door (falsify-6/7 class: a throw inside
`apply-block` is a halted chain; this one needed no out-of-domain integer —
`:cap` resting orders and one more quote).

## What landed

- `src/torihiki/book.cljk` — `take-slot!` is total: returns -1 when the slab
  is full instead of throwing `torihiki.book: order slab exhausted`.
  `place!` rests nothing in that case (a full book still takes what crosses;
  the remainder is cancelled like an IOC's). New `bk/full?` and
  `bk/marketable?` answer the validation path.
- `src/torihiki/api.cljk` — `:book-full` reason; `validate` refuses a quote
  that can only rest against a full book. A crossing order is never refused
  for capacity (it frees a slot per maker it fills). Shape reasons outrank it.
- `src/torihiki/state.cljk` — `:book-full 22` appended to `reason-codes`
  (append-only; caught RED by `every-refusal-reason-has-its-own-code`).
- `test/torihiki/slab_full_test.cljk` — 6 deftests pinning REASON literals
  (`:book-full`, `:bad-quantity`) and VALUES (what rested, what filled,
  resting counts). Registered in `script/tests-on-nbb.cljk` (floor 28→29).

Provenance: the diff body is the 2026-10-24-2219b tick's WIP
(6d1e7bc, "UNVERIFIED GREEN — budget cut the landing"), re-landed here with
the broken-first runs that tick never got.

## Measured (all on this branch)

- RED (defect probe, unfixed src): `kbb evidence/slab-exhaustion-probe.cljk`
  → `HALT torihiki.book: order slab exhausted`
  (evidence/2026-10-25-0422-slab-halt-probe-red.out).
- RED (suite, tests present, src unfixed): `kbb -M:test` exit 1,
  `Unable to resolve symbol: bk/full?`
  (2026-10-25-0422-slab-full-red-kbb.{out,err}).
- RED2 (src fixed minus reason-code): `kbb -M:test` exit 1 — 514 tests /
  3549 assertions / **1 failure**: `reasons with no code: [:book-full]`
  (2026-10-25-0422-slab-full-red2-kbb.out).
- GREEN probe: verdict `-1`, resting 32→32, no throw
  (2026-10-25-0422-slab-halt-probe-green.out).
- GREEN ×3 legs, identical counts **514 tests / 3549 assertions / 0F / 0E**
  (baseline on main: 508 / 3528 → +6 tests / +21 assertions):
  - `kbb -M:test` exit 0 (2026-10-25-0422-slab-full-green-kbb.out)
  - `bash script/jvm-test.sh` exit 0 (renamed .cljk→.cljc copy + dep shims)
    (2026-10-25-0422-slab-full-green-jvm.out)
  - `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang bash
    script/nbb-test.sh` exit 0, namespaces 29/29
    (2026-10-25-0422-slab-full-green-nbb.out) — the KOTOBA_CHECKOUTS override
    is required from a `~/github/wt/fleet` worktree (PR #64 note).
