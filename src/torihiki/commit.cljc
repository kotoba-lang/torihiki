(ns torihiki.commit
  "The authenticated tree over the state, and the proofs it makes possible.

  ## What this buys

  A flat digest commits to everything and proves nothing about any part: to
  check one balance against it you need the whole state, which means replaying
  the chain, which is the thing a light client exists not to do. A tree over
  the same encoding lets a server hand back one account's bytes plus a sibling
  path, and the client recomputes the root itself.

  ## Why merkle-sum and not a tree written here

  `kotoba-lang/merkle-sum` is already a zero-dependency portable `.cljc`
  Merkle tree with inclusion proofs, extracted from `cryptoexchange.attest`
  so that \"any actor needing PoR/PoL reuses one audited implementation\". It
  takes an injected hasher, so it carries no crypto of its own, and it carries
  an odd node UP unchanged rather than duplicating it — duplication is the
  classic proof-forgery footgun, and it also double-counts sums.

  Writing a fourth Merkle tree in this workspace to avoid one dependency
  would be writing a fourth thing to get subtly wrong.

  ## The sums are real now

  A merkle-sum tree's root carries the total of its leaves, and for an
  exchange that total is the prize: the root's sum is every account's
  collateral added up, which is what the reserves have to cover.

  This used to be zero everywhere, and the reason was honest — `verify`
  rejects a negative leaf sum, and collateral COULD go negative, because a
  liquidation the insurance fund could not fully cover left the hole sitting
  on the account as a negative balance. A sum that is only valid while nobody
  is underwater is a claim that fails exactly when it matters.

  What changed is not here. `torihiki.clearing/settle-deficit` moves that hole
  into a separate non-negative `:deficit` field, so `:collateral` is a
  quantity an account HAS rather than a mixture of holdings and debts, and it
  can be added up. The leaf sum became collateral and the root's sum became a
  reserves attestation, with the one change to this namespace being that the
  zeros are gone.

  ## What the root's sum does and does not say

  It says: these accounts together hold this much collateral, and any one of
  them can prove its share against it without replaying the chain.

  It does not say the money exists. That is the other half of proof of
  reserves and it lives off-chain — an attestation that the escrow backing
  this chain holds at least `reserves`. Until `:bridge-authority` is set, this
  chain mints its own collateral, so the sum is exact and unbacked; `reserves`
  reports the number either way rather than pretending the distinction is
  this namespace's to make."
  (:require [merkle-sum.core :as ms]
            [kotoba.bytes :as b]
            [kotoba.bytes.sha256 :as sha]))

(defn- hash-hex
  "The hasher merkle-sum digests node preimages with. Node preimages are
  ASCII (`node|<hash>|<sum>|…`), so this is the string face of the same
  SHA-256 the leaves use."
  [s]
  (sha/sha256-hex (b/utf8-encode s)))

(defn leaf-hash
  "A leaf's digest: SHA-256 over its id and its bytes.

  The id is inside the preimage, not merely beside it. Without that, a leaf
  proved at one position could be replayed as a different leaf at another —
  the account's own id is in its encoding today, but that is a property of
  this encoding rather than of the tree, and the tree should not depend on it.

  It also separates leaf preimages from node preimages, which begin with the
  ASCII `node|`."
  [{:keys [id bytes]}]
  (sha/sha256-hex (into (b/utf8-encode (str "leaf|" id "|")) bytes)))

(defn tree
  "Build the tree over `leaves` (`[{:id .. :bytes ..}]`).

  `:id->str identity` because the ids are already the strings that sort into
  the canonical order — merkle-sum's default `str` would be a no-op on them,
  but saying so is what keeps a later id type change from silently reordering
  the tree.

  A leaf carries the sum `canonical-leaves` gave it — collateral on an account
  leaf, zero on the rest, because a book or a nonce is not a quantity of money
  and adding it into the total would make the total mean nothing."
  [leaves]
  (ms/build-tree hash-hex
                 (mapv (fn [l] (assoc l :hash (leaf-hash l) :sum (:sum l 0))) leaves)
                 {:id->str identity}))

(defn root
  "The tree's root hash for `leaves`."
  [leaves]
  (:hash (:root (tree leaves))))

(defn reserves
  "The total collateral committed by `leaves` — the root's sum.

  This is the liability side of proof of reserves, and it is the half a chain
  can produce by itself: every account's collateral, authenticated, with each
  account able to prove its own share against the same root.

  The asset side cannot come from here. Whoever holds the escrow attests that
  it contains at least this much, and a reader compares the two numbers. The
  point of the tree is that neither party has to be believed about the first
  one."
  [leaves]
  (:sum (:root (tree leaves))))

(defn proof
  "Inclusion proof for the leaf with `id`, or nil when there is none.

  Returns `{:id :bytes :sum :proof :root :reserves}` — everything a verifier
  needs except the root it independently trusts, which is included so a caller
  can see what this server believes without being asked to trust it.

  `:sum` is this leaf's collateral and `:reserves` the whole tree's. Both are
  claims by the server; `verify` is what makes them checkable."
  [leaves id]
  (let [t (tree leaves)]
    (when-let [p (ms/inclusion-proof t id)]
      (when-let [l (first (filter #(= id (:id %)) (:leaves t)))]
        {:id id :bytes (vec (:bytes l)) :sum (:sum l 0) :proof p
         :root (:hash (:root t)) :reserves (:sum (:root t))}))))

(defn verify
  "Check a proof against a root the caller already trusts.

  Takes the leaf's bytes rather than its hash, so the caller has to have the
  thing it is verifying — a verifier handed only a hash proves that SOME
  preimage is in the tree, which is not the question anyone is asking.

  `root` is either the trusted root hash or a `{:hash :sum}` map. Given only
  the hash, the tree's total is read from the proof's own `:reserves`, and
  that is safe rather than a shortcut: every node preimage contains its sum,
  so a total that did not match would recompute to a different root hash and
  fail against the one the caller trusts. The sums cannot be moved without
  moving the hash, which is the property merkle-sum exists to provide."
  [{:keys [id bytes sum proof reserves]} root]
  (let [root-hash (if (map? root) (:hash root) root)
        root-sum (if (map? root) (:sum root) (or reserves 0))]
    (boolean
     (and id bytes proof root-hash
          (ms/verify hash-hex (leaf-hash {:id id :bytes bytes}) (or sum 0) proof
                     {:hash root-hash :sum root-sum})))))

(defn account-leaf-id
  "The leaf id holding account `a`'s collateral and positions. Mirrors
  `torihiki.state/canonical-leaves`; a light client needs to be able to ASK
  for the right leaf without reading that function.

  Sixteen digits, the width of the largest i53 — see `torihiki.state/id-pad`
  for why a shorter pad silently reorders the tree."
  [a]
  (let [s (str a)]
    (str "04:01:" (subs "0000000000000000" 0 (max 0 (- 16 (count s)))) s)))
