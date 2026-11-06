# 2026-11-06 12:2x — EVM word door landed (green on three legs)

Branch: `bot/torihiki-fix-20261106-1226` (completes in-flight
`bot/torihiki-fix-20261106-0818`, merged in for completion).

## What

A stack word is 256 bits and a host integer is not. Where one becomes the
other — jump target, memory offset/length, calldata offset, shift count,
byte index — the old code TRUNCATED (`.longValue` mod 2^64 on the JVM,
`js/Number` rounding on the node), so one JUMP landed at two pcs and one
offset addressed two keys: a state-root fork inside one call, nothing
thrown. The door refuses instead: outside 0..2^53-1 there is no host
integer that IS the word.

- `w->int` answers nil when no host integer is the word; every caller
  halts (`:word-out-of-domain`), never throws.
- Memory windows go through `mem-args`: bounded work (`max-mem` = 65536
  bytes) and every key inside the shared domain (`:memory-limit`
  otherwise).
- Calldata/returndata reads past the end are zeroes / empty (EVM rule),
  not `subs` throws out of `run`.
- SHL/SHR at 256+ and BYTE at 32+ are judged in WORD space (EVM rule),
  so a shift word no host integer holds is zero, not a truncated shift.
- CREATE2's initcode window goes through the same door (was a raw
  `mem-read` that surfaced as `:step-faulted`).
- The step net catches any residual fault and halts `:step-faulted` —
  apply-block is never thrown from.

## What this tick found (the 08:818 fix as committed was RED)

The in-flight commit's "fix implemented" did not measure: the door code
used `wgt` as a predicate, but `wgt` answers a WORD (`one`/`zero`) — and
a word 0 is truthy on both runtimes (BigInteger 0 / BigInt 0n). Every
`(if (wgt …))` was always-true, `w->int` answered nil for EVERY word, and
the doors refused the whole opcode table. Measured this tick on kbb at
the merged state: **675 tests / 5617 assertions / 27 failures / 0 errors**
(10 new-pin failures + 17 regressions in pre-existing EVM tests). Fixed
by `wgt?` (predicate over `wgt`+`wzero?`), used at all four door sites.

Second finding: `span-ok?` judged the window fit with `o + l - 1 ≤
i53-max` — the sum crosses 2^53 exactly at the boundary it judges, so a
JS double rounds it and a window whose true last key is i53-max + 1 is
accepted on the node while the JVM refuses (fork at the door that exists
to prevent forks). Rewritten as `l ≤ (i53-max + 1) - o`, exact on both
runtimes.

Third: CREATE2 built `mem-read mem (w->int off) (w->int len)` raw — a
refusal there surfaced as `:step-faulted`, not the door's reason. Routed
through `mem-args`.

## Measured

BROKEN-first for the 10 new deftests (word-door pins: REASON literals
`:word-out-of-domain` / `:memory-limit` / `:step-faulted`, VALUE pins for
zeroes-past-the-end and zero-shift):
- previous tick (branch 0818): kbb **10F/3E** (evidence/word-door-broken-kbb.*)
- this tick, merged state before the fix: kbb **27F/0E** (/tmp/wd1226-kbb.out,
  10 new + 17 pre-existing red — the "fix implemented" commit was red)

GREEN, all three legs, identical counters:
- `kbb -M:test`: **675 tests / 5617 assertions / 0 failures / 0 errors**
- `script/jvm-test.sh`: **675 / 5617 / 0F / 0E**
- `script/nbb-test.sh` (KOTOBA_CHECKOUTS=…): **675 / 5617 / 0F / 0E**,
  namespaces 38/38, TESTS-ON-NBB: pass

Baseline at origin/main (324a9ec, PR #107): 665/5590 on kbb (from the
same suite at the branch's BROKEN run minus the 10 new tests' reds —
see counters in evidence/word-door-broken-kbb.out). +10 tests / +27
assertions from this package.

No throw on any apply path introduced; every refusal is a halt carrying a
reason literal. `torihiki.api/reasons` untouched (the EVM halt reasons
live in the interpreter's result map, not the tx refusal taxonomy).
