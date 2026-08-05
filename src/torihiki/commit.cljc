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

  ## The sums are all zero, deliberately

  A merkle-sum tree's root carries the total of its leaves, which for an
  exchange is the obvious prize: proof of reserves, the root's sum being the
  sum of every account's collateral. **This does not claim that**, because
  `verify` rejects a negative leaf sum and torihiki's collateral CAN go
  negative — `torihiki.liquidation` computes a shortfall from exactly that
  case and has the insurance fund absorb it. A sum that is only valid while
  no account is underwater is a claim that fails when it matters most.

  Making the sums real needs the non-negativity to be an invariant first, at
  which point the leaf sum becomes collateral and the root's sum becomes a
  reserves attestation with no other change here. Until then zero is the
  honest value, and it is not free: it is a whole field of the node preimage
  carrying no information."
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
  the tree."
  [leaves]
  (ms/build-tree hash-hex
                 (mapv (fn [l] (assoc l :hash (leaf-hash l) :sum 0)) leaves)
                 {:id->str identity}))

(defn root
  "The tree's root hash for `leaves`."
  [leaves]
  (:hash (:root (tree leaves))))

(defn proof
  "Inclusion proof for the leaf with `id`, or nil when there is none.

  Returns `{:id :bytes :proof :root}` — everything a verifier needs except
  the root it independently trusts, which is included so a caller can see
  what this server believes without being asked to trust it."
  [leaves id]
  (let [t (tree leaves)]
    (when-let [p (ms/inclusion-proof t id)]
      (when-let [l (first (filter #(= id (:id %)) (:leaves t)))]
        {:id id :bytes (vec (:bytes l)) :proof p :root (:hash (:root t))}))))

(defn verify
  "Check a proof against a root the caller already trusts.

  Takes the leaf's bytes rather than its hash, so the caller has to have the
  thing it is verifying — a verifier handed only a hash proves that SOME
  preimage is in the tree, which is not the question anyone is asking."
  [{:keys [id bytes proof]} root-hash]
  (boolean
   (and id bytes proof root-hash
        (ms/verify hash-hex (leaf-hash {:id id :bytes bytes}) 0 proof
                   {:hash root-hash :sum 0}))))

(defn account-leaf-id
  "The leaf id holding account `a`'s collateral and positions. Mirrors
  `torihiki.state/canonical-leaves`; a light client needs to be able to ASK
  for the right leaf without reading that function."
  [a]
  (let [s (str a)]
    (str "04:01:" (subs "000000000000" 0 (max 0 (- 12 (count s)))) s)))
