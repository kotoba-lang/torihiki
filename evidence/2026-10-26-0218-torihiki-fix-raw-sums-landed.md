# 2026-10-26 02:18 torihiki-fix tick — LANDED: the raw `+` folds that feed a root or a decision saturate

- Host load gate: PASS (torihiki_state.sh 02:17 — 7.42 / 10.94 / 15.23, all windows < 20).
- Worktree `~/github/wt/fleet/bot/torihiki-fix-20261026-0218`, branch
  `bot/torihiki-fix-20261026-0218` @ origin/main **07d025b** (PRs #56–#66 all
  merged since maturity.md's NEXT was written — NEXT ② is landed, NEXT ① is a
  measurement-bot item). Note: the branch name contains a slash, so the
  worktree sits at `fleet/bot/torihiki-fix-20261026-0218` (= `fleet/<branch-name>`).
- Package = the residual raw-`+` class from the 2026-10-25-0026 "left for
  later" list: the `:size` fold, the root-preimage sums, the derived sums, and
  the trigger store door.

## Measured (exact counts)

**RED — same final test file, unfixed src: `kbb -M:test` → 526 tests / 3590
assertions / 11 failures / 0 errors** (exit 1, /tmp/red2-26-0218.out). All 11
broken assertions are in the new section:
- 3 — `position-size-saturates-at-the-ceiling`: ceiling pin returned raw
  9007199254741000 (= 2^53+8), the in-domain? check on it, and the floor pin
  returned raw −9007199254741000.
- 2 — `stake-of-saturates-at-the-ceiling`: raw 9007199254740992 (2^53) and
  raw −9007199254740992.
- 2 — `bonded-saturates-at-every-sum`: both pins raw 9007199254740996.
- 1 — `rolling-volume-read-sum-saturates`: raw 9007199254740992.
- 2 — `root-preimage-sums-saturate`: the falsify-14 fork measured live — two
  states differing only past the ceiling hashed to **56183d4b… vs c68ec2ac…**
  (bonds) and **2d947dd3… vs 77308ba9…** (unbonding).
- 1 — `the-trigger-store-door-is-gated-too`: the engine STORED a
  :trigger-price of 9007199254740992 (state-identical no-op expected).

A first RED run (526/3590, 9F + 3E) errored on my own root-pin fixture
(`st/state-root` on a minimal ex threw `TypeError: Cannot read properties of
null`); the fixture was rebuilt on `st/new-exchange` and the canonical RED
above was re-run on the same test file BEFORE the fix landed.

**GREEN** (all three legs, identical counts):
- `kbb -M:test` 526 tests / 3590 assertions / 0F / 0E (exit 0)
- `bash script/jvm-test.sh` 526 / 3590 / 0F / 0E (exit 0)
- `bash script/nbb-test.sh` 526 / 3590 / 0F / 0E (exit 0) — needs
  `KOTOBA_CHECKOUTS=/Users/junkawasaki/github/kotoba-lang`; the default
  sibling-checkout lookup misses the pins' checkouts from any fleet worktree
  (exits 2 `unresolvable: pprint @ 5c9d4aea…`).

**Parity**: `script/jvm-parity.sh` vs `script/nbb-parity.sh` outputs
byte-identical (`diff` empty): FLAT ROOT `b4322bedd406112e6…`, STATE ROOT
`d1ebb9d30cd51516…`, `PROOF a 10 verifies true`, both legs exit 0.

Honest note: the below-ceiling regression pins and the "root still notices a
changed bond" guard pass pre-fix too; the broken-first evidence is the 11
assertions listed above.

## Left (not scoped here)

- validators.cljk slash-path raw sums (:286 `take!` amount, :329 delegator
  stake) and the light.cljk/oracle.cljk weight sums — same class, different
  scope.
- `trg/valid?` now gates the price only; its shape fields (:qty/:side) remain
  shape-only at the engine gate (validate covers them).

## Housekeeping

- Stale worktrees from ticks 20261022–20261025 remain registered (incl. the
  fmul in-flight worktree bot-torihiki-fix-20261023-2023 — its package landed
  as PR #66, so the worktree is superseded).
- Local `git branch -D` is refused in cron mode ("flagged as dangerous"), so
  stale local branches (and this tick's branch after merge) cannot be deleted
  from a cron tick; they need a manual or approved cleanup pass.
