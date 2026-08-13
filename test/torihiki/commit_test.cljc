(ns torihiki.commit-test
  "The tree, and the claim that it is a refinement of what came before."
  (:require [clojure.test :refer [deftest is]]
            [torihiki.state :as st]
            [torihiki.commit :as cm]
            [torihiki.parity :as par]
            [clojure.string]))

(defn- ex [] (st/apply-block (par/fresh) par/scenario))

;; ── the compatibility anchor ────────────────────────────────────────────────

(deftest the-encoding-changes-only-on-purpose
  ;; This pin used to hold `22f75a7f…`, the digest of the parity scenario from
  ;; before the tree existed, and it was there to prove the tree had not
  ;; touched the bytes underneath it.
  ;;
  ;; The governance leaf DOES touch them, deliberately: `:bridge-authority`
  ;; and `:oracle-publishers` decide who may mint collateral and who may move
  ;; the mark, and neither was under the root, so two replicas configured
  ;; differently agreed on the root while disagreeing about who may mint.
  ;;
  ;; So the number moved, once, with a reason. The assertion is kept rather
  ;; than deleted because its job never was to hold one particular digest — it
  ;; is to make an accidental encoding change impossible to land quietly, and
  ;; that job is the same on the far side of a deliberate one.
  (is (= "3ab527143aac4540a2376320bd731ddb956aa16a1cf63e04111d811a8bf0537a"
         (st/flat-root (ex)))))

(deftest the-root-carries-the-total-collateral
  ;; Proof of reserves, liability half. The sums were all zero until bad debt
  ;; stopped living in `:collateral` as a negative number; this is the
  ;; assertion that they carry something now, and that the something is right.
  (let [e (ex)
        expected (reduce + 0 (map (fn [[_ a]] (or (:collateral a) 0))
                                  (get-in e [:clearing :accounts])))]
    (is (pos? expected) "the scenario holds collateral, or this proves nothing")
    (is (= expected (cm/reserves (st/canonical-leaves e))))))

(deftest only-collateral-counts-toward-the-total
  ;; A book, a nonce and a set of oracle submissions are not quantities of
  ;; money. If any of them carried a sum, the root's total would be a number
  ;; with no unit — and it would still verify, which is the dangerous part.
  (is (= #{0} (set (map #(:sum % 0)
                        (remove #(clojure.string/starts-with? (:id %) "04:01:")
                                (st/canonical-leaves (ex))))))))

(deftest a-leaf-sum-cannot-be-moved-without-moving-the-root
  ;; What makes it safe for `verify` to read the tree's total out of the proof
  ;; it is checking: every node preimage contains its sum, so a forged total
  ;; recomputes to a different root hash.
  (let [e (ex)
        root (st/state-root e)
        ls (st/canonical-leaves e)
        a (first (sort (keys (get-in e [:clearing :accounts]))))
        p (cm/proof ls (cm/account-leaf-id a))]
    (is (cm/verify p root))
    (is (not (cm/verify (update p :sum + 1) root))
        "a leaf claiming more collateral than it has still verified")
    (is (not (cm/verify (update p :reserves + 1) root))
        "a tree claiming more reserves than it holds still verified")))

(deftest governance-is-under-the-root
  ;; The property the leaf exists for, stated directly: change who may mint,
  ;; change nothing else, and the root must move. Before the leaf, these two
  ;; exchanges produced identical roots.
  (let [base (ex)
        other (assoc base :bridge-authority 7)]
    (is (not= (st/state-root base) (st/state-root other))))
  ;; And the same for who may publish a price.
  (let [base (ex)
        other (update base :oracle-publishers (fnil conj #{}) 99)]
    (is (not= (st/state-root base) (st/state-root other))))
  ;; `nil` is not account 0. Zero is a real account id, and a nil authority
  ;; means the opposite of a restriction, so the two must not collide.
  (let [none (assoc (ex) :bridge-authority nil)
        zero (assoc (ex) :bridge-authority 0)]
    (is (not= (st/state-root none) (st/state-root zero)))))

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

(deftest leaf-ids-sort-correctly-for-real-account-ids
  ;; The test above passed while the padding was too short, because the parity
  ;; scenario's accounts are 10 and 11 and any padding orders two-digit ids
  ;; correctly. Live `torihiki.address/derive` produces fourteen-digit ids;
  ;; the first one that appeared on the deployed chain was 34633027260816.
  ;;
  ;; So this asserts the property on ids that actually stress it: values on
  ;; both sides of a power of ten, where lexicographic and numeric order
  ;; disagree unless every id is padded to the same width.
  (let [accts [9 10 99999999999999 100000000000000 34633027260816 9007199254740992]
        e (reduce (fn [e a]
                    (assoc-in e [:clearing :accounts a] {:collateral a :positions {}}))
                  (st/apply-block (par/fresh) par/scenario)
                  accts)
        ids (mapv :id (st/canonical-leaves e))]
    (is (= ids (vec (sort ids)))
        "leaf ids are not in sorted order — the tree and the flat encoding disagree")
    ;; and the leaf order still matches the numeric account order
    (let [acct-ids (filterv #(clojure.string/starts-with? % "04:01:") ids)]
      (is (= acct-ids
             (mapv #(cm/account-leaf-id %)
                   (sort (keys (get-in e [:clearing :accounts])))))))
    ;; proofs still work for the big ones
    (doseq [a accts]
      (let [p (cm/proof (st/canonical-leaves e) (cm/account-leaf-id a))]
        (is (cm/verify p (st/state-root e)) (str "no verifying proof for account " a))))))
