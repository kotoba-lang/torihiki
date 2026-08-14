(ns torihiki.snapshot
  "The exchange as plain data, and back again.

  ## Why this has to exist

  `torihiki.log/replay` reconstructs the state by re-executing every block,
  which is the right thing for VERIFYING a chain and the wrong thing for
  STARTING one. Replay costs O(chain), and a process that must pay that before
  it can answer anything eventually cannot start at all.

  That is not a hypothetical. On 2026-08-04 the deployed validator spent long
  enough replaying its log to exceed a Cloudflare Durable Object's CPU budget;
  the platform reset the object, the reset discarded the in-memory state, and
  the next invocation replayed from zero and exceeded it again — a crash loop
  that could not end, because recovery WAS the work that killed it. Paging the
  replay turned a permanent outage into a slow start (ADR-2608040400). This
  removes the O(chain) instead of bounding it.

  ## What makes it safe

  A snapshot is only usable if restoring it gives a state that every other
  replica agrees with — not approximately, byte-for-byte under
  `torihiki.state/canonical-bytes`. Two things make that true and both were
  changes, not observations:

  1. **`torihiki.book` allocates the lowest free slot**, so the free set is a
     function of which slots are occupied rather than of the order they were
     freed in. A snapshot carrying the resting orders carries it implicitly.
     With the old free LIST, a restored replica would hand out different order
     ids from one that never restarted — and since a client cancels by id, and
     a refused cancel lands in `:rejected`, which IS in the state root, the two
     would diverge and the chain would halt.

  2. **Generations are carried for freed slots too**, up to the book's
     high-water mark. A generation is what stops a stale cancel from hitting
     whoever inherited a reused slot; resetting them would hand out an id that
     a cancel already in flight names.

  ## `:rejected` IS carried, and I got that wrong first

  It looks like block scratch — `apply-block` clears it at the top of every
  block — so the first version of this dropped it, on the reasoning that it
  belongs to the block that produced it rather than to the state.

  That reasoning is wrong and the round-trip test said so in bytes:
  `encode-rejections` is part of `canonical-bytes`, so the state root AFTER
  block N includes block N's rejections. A snapshot taken at height N that
  dropped them would restore to a state whose root is a different number from
  the one every peer agreed on — and it would look fine, because the book, the
  balances and the nonces would all be right.

  Whether something is state is decided by whether the root commits to it, not
  by how transient it feels.

  ## What is deliberately absent

  The preallocated slabs. The point of a snapshot is that it costs what the
  book HOLDS, not what it could hold — the same property `state-root` already
  has for the same reason.

  And the host's views — see `view-keys`. Same principle one level out: a
  snapshot costs what the STATE holds, not what somebody folded in beside it.

  ## The format is EDN, not JSON

  Account ids are integers and JSON has no integer keys; `:oracle-publishers`
  is a set and JSON has no sets. Both would survive a round trip through JSON
  as something subtly different — string keys, an array — and the difference
  would surface as a state root that does not match, at restore time, on a
  node that is already in trouble. EDN carries them as themselves."
  (:require [clojure.string]
            [torihiki.book :as bk]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])))

(def ^:const format-version
  "Bumped when the shape changes. A restore that silently accepted an older
  shape would produce a state that is wrong rather than a load that fails,
  and wrong state is what the state root exists to make impossible to hide.

  **Not bumped when `capture` stopped carrying `view-keys`**, and the reason
  is the rule above rather than an exception to it. An older snapshot has
  those keys in it; restoring one produces the same state root it always did,
  because the root never committed to them. Nothing is silently accepted that
  is wrong — the extra keys are the host's own views handed back to the host.

  Refusing them would have been the expensive mistake: every checkpoint on
  disk would fail to load at once, and a fleet that cannot load a checkpoint
  replays the log, which is the outage this namespace exists to prevent."
  1)

(def view-keys
  "Keys a HOST folds into the exchange map that are not the exchange.

  `torihiki.state` never writes these; `torihiki-node.validator`'s apply-fn
  does, beside the state, because the map it folds is the only place it has to
  put them. A print tape, a candle index per market, and the reasons blocks
  refused things — all three are views over fills the root already commits to,
  and `torihiki.state/canonical-leaves` reads none of them.

  ## Why dropping them is the whole point

  A capture becomes ONE Durable Object storage write, and that write is what
  has to stay small. It already carries the whole exchange; carrying an
  unbounded history beside it means that past some size the write no longer
  fits in the platform's storage timeout — and the platform's answer to that
  is not an error the caller catches. Captured from the deployed chain:

    Durable Object storage operation exceeded timeout which caused object to
    be reset.

  The reset dropped the venue to three of four replicas. A quorum of three has
  no margin left at three, so the next replica to fall behind stopped the
  chain. It happened three times and looked like a consensus stall each time.

  ## What it costs

  History, not state. A host that wants its views back across a restart writes
  them in a separate, bounded write; a host that does not gets a shorter tape
  and a shorter chart, which the routes serving them report as their own
  horizon rather than implying. The failure mode is visible, and it is not a
  state root that disagrees with the rest of the venue."
  #{:tape :candles :refused})

(defn capture
  "Exchange → plain data. Pure.

  `view-keys` are dropped: what comes out is what the state root commits to."
  [ex]
  (-> (apply dissoc ex view-keys)
      (assoc :snapshot/version format-version)
      ;; `:rejected` normalised to a vector: it is in the state root, and a
      ;; `nil` and an empty vector encode to the same bytes but are not `=`,
      ;; which would make a structural comparison of two snapshots lie.
      (update :rejected #(vec (or % [])))
      (update :books (fn [bs] (into {} (map (fn [[m b]] [m (bk/snapshot b)])) bs)))))

(defn restore
  "Plain data → exchange. Inverse of `capture`."
  [snap]
  (let [v (:snapshot/version snap)]
    (when-not (= v format-version)
      (throw (ex-info "torihiki.snapshot: unknown format version"
                      {:found v :expected format-version})))
    (-> snap
        (dissoc :snapshot/version)
        (update :rejected #(vec (or % [])))
        (update :books (fn [bs] (into {} (map (fn [[m s]] [m (bk/restore s)])) bs))))))

(defn write-string
  "Snapshot → EDN string. `pr-str` with `*print-length*` and `*print-level*`
  neutralised, because a REPL that set either would silently truncate the
  state into something that still parses."
  [snap]
  (binding [*print-length* nil
            *print-level* nil]
    (pr-str snap)))

(defn read-string*
  "EDN string → snapshot. Named with a star because `clojure.core/read-string`
  evaluates and this must not: a snapshot arrives from storage, and storage is
  not a place code should be able to come from."
  [s]
  (edn/read-string s))
