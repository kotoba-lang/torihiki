(ns torihiki.log
  "Phase 2: the verifiable log. A single sequencer, and a way for anyone to
  check that it did not lie.

  ## Sequencer, not consensus

  This namespace produces blocks. One writer decides their order, and nothing
  here votes, gossips, or tolerates a Byzantine peer. Calling that
  arrangement `consensus` would be a lie of the kind ADR-2608010930 Decision 5
  exists to forbid: a single writer plus compare-and-swap is a SEQUENCER. It
  is a real and useful thing — it gives total order, and the log it produces
  is independently checkable — but a validator set is what replaces trust in
  the operator, and there isn't one yet.

  ## What the log actually guarantees

  Two different properties, and conflating them is how a chain ends up
  trusting its own operator:

  1. `chain.core/verify-chain` proves the log was NOT TAMPERED WITH after the
     fact. Every commit re-derives to its own CID from its own bytes, and
     `seq` increases by exactly one. Rewriting a past block changes its CID
     and orphans every descendant.

  2. `replay` proves the recorded state roots are CORRECT. This is the
     stronger claim and the one that matters: a sequencer can publish a
     perfectly well-formed, untampered chain in which every state root is
     invented. Only re-executing the transactions catches that. Property (1)
     is about the log's integrity; property (2) is about the sequencer's
     honesty, and (1) does not imply (2).

  ## Injected, not imported

  Storage arrives as `put!`/`get-fn`, exactly the seam `chain.core` and
  `prolly-tree.core` already use, so a caller shares one block store with no
  adapter. Signing arrives as `sign-fn`/`verify-fn` for the same reason and
  one more: `kotoba-lang/ed25519` is JVM-only, and importing it would make
  this namespace — whose whole purpose is that anyone can re-check the log —
  unable to run in the browser that wants to check it."
  (:require [chain.core :as chain]
            [torihiki.state :as st]))

;; ── wire encoding ───────────────────────────────────────────────────────────
;;
;; The commit body is dag-cbor, so it holds strings, integers, vectors and
;; maps — not keywords. Rather than a generic keyword-walking codec, each
;; transaction field is named explicitly. That is more typing and it is worth
;; it: a generic codec silently round-trips fields nobody meant to put on the
;; wire, and a block format that accepts anything is one that can never be
;; validated.

(def ^:private tx-kinds
  #{:deposit :withdraw :order :cancel :oracle :funding-sample :funding-settle
    :liquidate})

(def ^:private tx-fields
  [:account :market :side :level :qty :flags :oid :amount :price])

(defn tx->wire
  [tx]
  (let [kind (:tx tx)]
    (when-not (contains? tx-kinds kind)
      (throw (ex-info "torihiki.log: unknown transaction kind" {:tx kind})))
    (reduce (fn [m k]
              (if-some [v (get tx k)]
                (do (when-not (integer? v)
                      (throw (ex-info "torihiki.log: non-integer transaction field"
                                      {:field k :value v})))
                    (assoc m (name k) v))
                m))
            {"tx" (name kind)}
            tx-fields)))

(defn wire->tx
  [w]
  (reduce (fn [m k]
            (if-some [v (get w (name k))] (assoc m k v) m))
          {:tx (keyword (get w "tx"))}
          tx-fields))

(defn block->wire
  [{:keys [height ts txs]} state-root sig]
  (cond-> {"height" height
           "ts" ts
           "state-root" state-root
           "txs" (mapv tx->wire txs)}
    sig (assoc "sig" sig)))

(defn wire->block
  [w]
  {:height (get w "height")
   :ts (get w "ts")
   :txs (mapv wire->tx (get w "txs"))})

;; ── what gets signed ────────────────────────────────────────────────────────

(defn signing-payload
  "The canonical bytes-as-string a sequencer signs.

  Deliberately covers the parent CID as well as this block's own contents. A
  signature over the block alone would let anyone splice a validly-signed
  block under a different parent and produce a chain the operator never
  produced — the signature has to commit to the position, not just the
  payload."
  [height ts state-root prev-cid txs]
  (str "torihiki/block\n"
       "height=" height "\n"
       "ts=" ts "\n"
       "state-root=" state-root "\n"
       "prev=" (or prev-cid "genesis") "\n"
       "ntx=" (count txs) "\n"
       (apply str (for [t txs]
                    (str (name (:tx t)) ":"
                         (:account t) ":" (:market t) ":" (:side t) ":"
                         (:level t) ":" (:qty t) ":" (:flags t) ":"
                         (:oid t) ":" (:amount t) ":" (:price t) "\n")))))

;; ── producing the log ───────────────────────────────────────────────────────

(defn commit-block!
  "Apply `block` to `ex`, then append it to the chain rooted at `prev-cid`.

  Returns `{:exchange :cid :state-root}`. `sign-fn` is optional and receives
  the signing payload; whatever it returns rides in the commit under `sig`.

  The state root is computed AFTER application and stored alongside the
  transactions that produced it, which is exactly what makes `replay` able to
  contradict it."
  ([ex put! get-fn prev-cid block] (commit-block! ex put! get-fn prev-cid block nil))
  ([ex put! get-fn prev-cid block sign-fn]
   (let [ex' (st/apply-block ex block)
         root (st/state-root ex')
         sig (when sign-fn
               (sign-fn (signing-payload (:height block) (:ts block)
                                         root prev-cid (:txs block))))
         cid (chain/commit! put! get-fn (block->wire block root sig) prev-cid)]
     {:exchange ex' :cid cid :state-root root})))

(defn commit-blocks!
  "Fold a sequence of blocks into the log. Returns `{:exchange :cid :roots}`."
  ([ex put! get-fn prev-cid blocks] (commit-blocks! ex put! get-fn prev-cid blocks nil))
  ([ex put! get-fn prev-cid blocks sign-fn]
   (reduce (fn [{:keys [exchange cid roots]} b]
             (let [r (commit-block! exchange put! get-fn cid b sign-fn)]
               {:exchange (:exchange r) :cid (:cid r) :roots (conj roots (:state-root r))}))
           {:exchange ex :cid prev-cid :roots []}
           blocks)))

;; ── checking the log ────────────────────────────────────────────────────────

(defn replay
  "Re-execute every block in the chain ending at `cid` against a fresh
  exchange from `new-exchange-fn`, and compare each recomputed state root
  with the one the sequencer recorded.

  Returns `{:ok true :blocks n :exchange ex}` when every root agrees, or
  `{:ok false :height h :recorded r :recomputed r'}` at the FIRST block whose
  root does not — first, not all, because after one divergence every later
  root is computed from a state the verifier and the sequencer no longer
  share, so the remaining mismatches are noise.

  This is what makes the operator checkable. `chain.core/verify-chain` should
  be run too, but it answers a different question — see this namespace's
  docstring."
  [get-fn cid new-exchange-fn]
  (let [entries (chain/chain get-fn cid)]
    (loop [ex (new-exchange-fn)
           [e & more] entries
           n 0]
      (if (nil? e)
        {:ok true :blocks n :exchange ex}
        (let [w (:state e)
              recorded (get w "state-root")
              block (wire->block w)
              ex' (st/apply-block ex block)
              recomputed (st/state-root ex')]
          (if (= recorded recomputed)
            (recur ex' more (inc n))
            {:ok false
             :height (:height block)
             :recorded recorded
             :recomputed recomputed}))))))

(defn verify-signatures
  "Check every block's signature with `verify-fn`, which receives
  `[payload sig]` and returns truthy when the signature is good.

  Returns `{:ok true :blocks n}` or `{:ok false :height h :reason ...}`. A
  block with no signature is a failure rather than a skip: an unsigned block
  in a signed log is precisely what an attacker who cannot sign would
  produce, so treating it as 'nothing to check' would make the whole
  verification optional at the attacker's discretion."
  [get-fn cid verify-fn]
  (let [entries (chain/chain get-fn cid)]
    (loop [[e & more] entries n 0]
      (if (nil? e)
        {:ok true :blocks n}
        (let [w (:state e)
              sig (get w "sig")
              block (wire->block w)]
          (cond
            (nil? sig)
            {:ok false :height (:height block) :reason :unsigned}

            (not (verify-fn (signing-payload (:height block) (:ts block)
                                             (get w "state-root")
                                             (:prev e) (:txs block))
                            sig))
            {:ok false :height (:height block) :reason :bad-signature}

            :else (recur more (inc n))))))))

(defn tip-root
  "The state root the sequencer claims at the tip, without replaying."
  [get-fn cid]
  (some-> (chain/head get-fn cid) :state (get "state-root")))
