# Evidence: oracle runs (AWAI-3556)

All oracle runs below were executed on this machine against the pinned
dependency, not by reading alone.

## Repository and dependency pins

- Repo HEAD: `263104338a9c262c86b6842ddbb3b0dca26d1650`
  (branch `muse/awai-162-rust-matching-engine`, dated 2026-11-16 +0900).
- Oracle dependency pin (`deps.edn` / `nbb.edn`): `kotoba.git` at
  `b94158fd48ec541b70d3c455912a06f407537ddc`
  (`lib/kotoba.torihikiCore` — `torihiki.book`, `torihiki.api`;
  `torihiki.fixed`, `torihiki.auth` live in that library).
- Date of runs: 2026-11-19T02:17Z and later the same day (UTC).

## Toolchain

- `kbb` (kotoba SCI runner) with `--backend sci`, classpath built by
  `script/nbb-classpath.cljk` from the `nbb.edn` pins
  (`KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang`).
- `node` v26.0.0 (SCI host), Homebrew OpenJDK 27.0.1 (kbb boot JVM).
- The sandbox blocks writes to `~/.gitlibs` and `~/.clojure`, so the runs
  used `GITLIBS=/tmp/mygitlibs` (a copy of the cached kotoba objects),
  `CLJ_CONFIG=/tmp/cljconfig`, and `JAVA_HOME` pointed at the Homebrew JDK.
  No repository file was touched by this; the extraction the recipe itself
  performs (`.nbb-deps/`) is git-ignored.

## Run 1: full oracle suite (cross-runtime claim)

```
script/nbb-leg.sh equivalent:
kbb --backend sci --classpath "$CP" script/tests-on-nbb.cljk
```

Result: **707 tests, 5708 assertions, 0 failures, 0 errors, 38/38
namespaces** — the runtime that deploys ran the suite green on the day the
fixtures were taken.

## Run 2: parity fixture generator

```
kbb --backend sci --classpath "$CP" .agent-output/gen-parity.cljk \
  > .agent-output/parity-oracle.edn
```

`.agent-output/gen-parity.cljk` replays 13 tapes through the oracle's own
`torihiki.book/place!`, `cancel!`, and `reduce-to!` and prints each tape
with the oracle's return values (`-1` = nothing rested), fill maps, resting
count and touch. 12 of the 13 ship as
`engine-rs/tests/parity_fixtures.jsonl` (EDN converted to one JSON object
per line, `:ref` op-index links resolved to concrete oids); the 13th,
`amend-reduce-keeps-place`, is outside stage-1 scope and stays in the EDN
plus the note below.

`amend-reduce-keeps-place` oracle output (kept verbatim from
`.agent-output/parity-oracle.edn`):

- ask 5 @100 (acct 11) rests as oid 0; ask 5 @100 (acct 22) rests as oid
  1048576 (slot 1, generation 0 — lowest free slot, ids mirror exactly).
- `reduce-to!` oid 0 to 2 removes 3 and keeps its place in the queue.
- bid 8 @100 (acct 33) fills 2 from maker 11 then 5 from maker 22,
  i.e. the reduced order still fills first: time priority survived the
  amend. Rests 1 (bid remainder 1 @100), best-bid 100, best-ask -1.

## Run 3: validation-door probes

```
kbb --backend sci --classpath "$CP" .agent-output/gen-doors.cljk
```

Ran `torihiki.api/order-tx-shape` and `cancel-tx-shape` (the apply-path
gates `torihiki.state/apply-tx` asks before touching a book) over hostile
inputs. Raw output: `evidence/doors.json`. Every verdict matched the rule
this crate mirrors (review note: the first `doors.json` had a generator
bug — `(str kw)` keeps the colon, so every `:match` read `false` while the
verdicts agreed; the generator now compares with `clojure.core/name`, the
file was re-run against the same oracle, and all 18 `:match` read `true`
with identical verdicts):

| input | oracle verdict |
|---|---|
| well-formed order | `nil` (accepted) |
| market 9 (unlisted) | `:unknown-market` |
| account 1.5 | `:bad-account` |
| side 2 | `:bad-side` |
| qty 0 / -3 / 2^53 | `:bad-quantity` |
| level 64 on a 64-ladder / level -1 | `:bad-price-level` |
| level 32, qty 2^48+1 | `:bad-amount` |
| flags 8 | `:bad-flags` |
| level 0 | `nil` (accepted — level 0 rests) |
| well-formed cancel (oid 0) | `nil` (accepted) |
| cancel market 9 | `:unknown-market` |
| cancel account 1.5 | `:bad-account` |
| cancel oid -1 / 2.5 / 2^53 | `:missing-field` |

## Rules mirrored by reading only (not executed)

Everything above was executed. Mirrored by reading the source only:

- `torihiki.state/apply-tx :order` reduce-only clamping + forced IOC, and
  the stated Hyperliquid difference (reduce-only never rests) — the
  clearinghouse layer is out of scope, so only the book's half (ignore the
  flag) is implemented; see README "Out of scope".
- `torihiki.state/canonical-leaves` Merkle-sum tree layout — stage 1
  commits to matching-engine state only, with its own documented encoding.
- Nonce / key-binding / agent-key authentication (`torihiki.auth/check`,
  `accept`) — out of scope; the signature payload carries the account id
  but no nonce is tracked.
