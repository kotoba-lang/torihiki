(ns torihiki.commit-test
  "The tree, and the claim that it is a refinement of what came before."
  (:require [clojure.test :refer [deftest is]]
            [torihiki.state :as st]
            [torihiki.commit :as cm]
            [torihiki.parity :as par]))

(defn- ex [] (st/apply-block (par/fresh) par/scenario))

;; ── the compatibility anchor ────────────────────────────────────────────────

(deftest the-bytes-under-the-tree-did-not-change
  ;; The published digest of the parity scenario, from before the tree existed.
  ;; It lived in the README, where nothing checked it. The tree changes what
  ;; `state-root` returns; if it ALSO changed the encoding underneath, then
  ;; every historical root would have been silently invalidated rather than
  ;; merely superseded. This is the assertion that says it did not.
  (is (= "22f75a7ff4777d1c5ae397f47aa2b62b08aba8f2ba131aa6ae506b156cd9c87e"
         (st/flat-root (ex)))))

(deftest the-leaves-concatenate-back-to-the-flat-encoding
  ;; `canonical-bytes` is DEFINED as this concatenation, so this is not a
  ;; tautology by accident — it is the property that definition exists to
  ;; make unbreakable, restated where a future refactor will trip over it.
  (let [e (ex)]
    (is (= (vec (st/canonical-bytes e))
           (vec (mapcat :bytes (st/canonical-leaves e)))))))

(deftest leaf-ids-are-in-sorted-order
  ;; merkle-sum sorts leaves by id. If the ids did not already sort into the
  ;; canonical order, the tree would commit to a different sequence than the
  ;; flat encoding — the two would disagree while both looking healthy. The
  ;; padding is what makes account 10 sort after account 9.
  (let [ids (mapv :id (st/canonical-leaves (ex)))]
    (is (= ids (vec (sort ids))))))

;; ── what the tree is for ────────────────────────────────────────────────────

(deftest one-balance-verifies-without-the-rest-of-the-state
  (let [e (ex)
        root (st/state-root e)
        ls (st/canonical-leaves e)]
    (doseq [a (sort (keys (get-in e [:clearing :accounts])))]
      (let [p (cm/proof ls (cm/account-leaf-id a))]
        (is (some? p) (str "no proof for account " a))
        (is (cm/verify p root) (str "proof for account " a " did not verify"))
        ;; the verifier holds the bytes, so it can read the balance it proved
        (is (seq (:bytes p)))))))

(deftest a-tampered-leaf-does-not-verify
  (let [e (ex)
        root (st/state-root e)
        ls (st/canonical-leaves e)
        a (first (sort (keys (get-in e [:clearing :accounts]))))
        p (cm/proof ls (cm/account-leaf-id a))]
    (is (cm/verify p root))
    (is (not (cm/verify (update p :bytes #(assoc % 8 (inc (nth % 8)))) root))
        "a changed balance still verified")
    (is (not (cm/verify (assoc p :id (cm/account-leaf-id 999999)) root))
        "the leaf verified under somebody else's id")
    (is (not (cm/verify p "0000000000000000000000000000000000000000000000000000000000000000"))
        "the proof verified against a root it does not belong to")))

(deftest a-proof-from-one-state-does-not-verify-against-another
  ;; The failure that matters in practice: a server replays an old proof at a
  ;; client that has moved on.
  (let [e1 (ex)
        e2 (st/apply-block e1 {:height 2 :ts 2000
                               :txs [{:tx :deposit :account 10 :amount 12345}]})
        a 10
        p (cm/proof (st/canonical-leaves e1) (cm/account-leaf-id a))]
    (is (cm/verify p (st/state-root e1)))
    (is (not (cm/verify p (st/state-root e2))))))

(deftest asking-for-an-account-that-is-not-there-gets-nothing
  ;; Not a fabricated absence proof, and not an exception either — nil, which
  ;; the caller has to handle. Claiming to prove non-membership would need a
  ;; sorted-tree construction this does not have.
  (is (nil? (cm/proof (st/canonical-leaves (ex)) (cm/account-leaf-id 424242)))))

(deftest the-root-changes-when-any-part-does
  (let [e (ex)
        r (st/state-root e)]
    (is (not= r (st/state-root (assoc e :height (inc (:height e))))))
    (is (not= r (st/state-root (assoc-in e [:clearing :accounts 10 :collateral] 1))))
    (is (not= r (st/state-root (assoc-in e [:clearing :insurance-fund] 7))))))

(deftest the-tree-root-is-not-the-flat-root
  ;; Stated as a test because the two are both 64 hex characters and a
  ;; comparison that silently succeeded would mean the tree was never built.
  (let [e (ex)]
    (is (not= (st/flat-root e) (st/state-root e)))
    (is (re-matches #"[0-9a-f]{64}" (st/state-root e)))))
