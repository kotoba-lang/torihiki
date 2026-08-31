(ns torihiki.principal-test
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.principal :as pr]
            [torihiki.address :as addr]
            [torihiki.state :as st]
            [torihiki.api :as api]))

(def ctrl 900)
(def alice (addr/derive "alice-key"))
(def bob (addr/derive "bob-key"))
(def pid "did:web:auth.kotoba.cloud:principal:alice")
(def pid2 "urn:kotoba:principal:bob")

(defn- ex [] {:controller-authority ctrl :account-keys {alice "alice-key"}})

;; ── the shape the controller actually issues ────────────────────────────────

(deftest the-principal-shape-matches-what-the-controller-issues
  ;; Restated from `app-kotoba-cloud/session/identity-value` rather than
  ;; imported -- the chain cannot depend on a Worker. Pinned here by example so
  ;; the duplication is visible if either side moves.
  (is (pr/well-formed-principal? "did:web:auth.kotoba.cloud:principal:x"))
  (is (pr/well-formed-principal? "urn:kotoba:principal:x"))
  (testing "a principal the controller would never issue"
    (is (not (pr/well-formed-principal? "alice")))
    (is (not (pr/well-formed-principal? " did:x")))
    (is (not (pr/well-formed-principal? "did:x\nInjected: header")))
    (is (not (pr/well-formed-principal? (apply str "did:" (repeat 300 "x")))))
    (is (not (pr/well-formed-principal? nil)))))

;; ── an unconfigured chain refuses, it does not admit ────────────────────────

(deftest with-no-controller-nothing-is-permitted
  ;; The bridge reads nil as "the faucet is open". Reading nil the same way
  ;; here would let ANY account rotate ANY other account's owner key on a chain
  ;; whose operator simply had not configured an identity plane yet.
  (let [e (dissoc (ex) :controller-authority)]
    (is (= :no-controller (pr/check-claim e alice pid)))
    (is (= :no-controller (pr/check-confirm e ctrl alice pid)))
    (is (= :no-controller (pr/check-rotate e ctrl alice "new-key")))))

;; ── neither party can bind alone ────────────────────────────────────────────

(deftest the-controller-cannot-bind-an-account-that-never-claimed
  (is (= :no-claim (pr/check-confirm (ex) ctrl alice pid))))

(deftest the-owner-cannot-bind-without-the-controller
  (let [e (pr/claim (ex) alice pid)]
    (is (nil? (pr/principal-of e alice))
        "a claim alone bound the account")
    (is (= :not-the-controller (pr/check-confirm e bob alice pid))
        "somebody other than the controller confirmed a binding")))

(deftest the-controller-cannot-confirm-a-principal-the-owner-did-not-name
  ;; Otherwise the owner's signature authorises a binding they never named.
  (let [e (pr/claim (ex) alice pid)]
    (is (= :claim-mismatch (pr/check-confirm e ctrl alice pid2)))))

(deftest the-handshake-completes
  (let [e (-> (ex) (pr/claim alice pid))]
    (is (nil? (pr/check-confirm e ctrl alice pid)))
    (let [e (pr/confirm e alice pid)]
      (is (= pid (pr/principal-of e alice)))
      (is (= alice (pr/account-of e pid)))
      (is (nil? (get-in e [:principal-claims alice]))
          "the claim outlived the binding it produced"))))

;; ── one principal, one account ──────────────────────────────────────────────

(deftest a-principal-cannot-hold-two-accounts
  (let [e (-> (ex) (pr/claim alice pid) (pr/confirm alice pid) (pr/claim bob pid))]
    (is (= :principal-taken (pr/check-confirm e ctrl bob pid)))))

(deftest an-account-cannot-serve-two-principals
  (let [e (-> (ex) (pr/claim alice pid) (pr/confirm alice pid))]
    (is (= :account-taken (pr/check-claim e alice pid2)))))

(deftest squatting-a-principal-does-not-block-its-holder
  ;; The griefing case the two-step exists for. Bob claims Alice's principal;
  ;; the controller never confirms it, and Alice's own claim still binds.
  (let [e (-> (ex) (pr/claim bob pid))]
    (is (nil? (pr/check-claim e alice pid))
        "a pending claim by somebody else blocked the real holder")
    (let [e (-> e (pr/claim alice pid) (pr/confirm alice pid))]
      (is (= alice (pr/account-of e pid))))))

;; ── rotation, and what bounds it ────────────────────────────────────────────

(deftest an-account-that-never-opted-in-can-never-be-rotated
  ;; The whole bound on this authority. Bob has a key and has never claimed a
  ;; principal, so the controller cannot touch him.
  (let [e (assoc-in (ex) [:account-keys bob] "bob-key")]
    (is (= :not-bound (pr/check-rotate e ctrl bob "attacker-key")))))

(deftest a-pending-claim-is-not-enough-to-rotate
  ;; A claim is one signature from a key that may be the one being replaced.
  (let [e (pr/claim (ex) alice pid)]
    (is (= :not-bound (pr/check-rotate e ctrl alice "new-key")))))

(deftest only-the-controller-rotates
  (let [e (-> (ex) (pr/claim alice pid) (pr/confirm alice pid))]
    (is (= :not-the-controller (pr/check-rotate e bob alice "new-key")))))

(deftest rotation-replaces-the-key-and-drops-the-agents
  (let [e (-> (ex) (pr/claim alice pid) (pr/confirm alice pid)
              (assoc-in [:agents alice "agent-key"] {:expires nil}))]
    (is (nil? (pr/check-rotate e ctrl alice "new-key")))
    (let [e (pr/rotate e alice "new-key")]
      (is (= "new-key" (get-in e [:account-keys alice])))
      (is (nil? (get-in e [:agents alice]))
          "a delegation the replaced key granted outlived it"))))

(deftest rotating-to-the-key-already-there-is-refused
  (let [e (-> (ex) (pr/claim alice pid) (pr/confirm alice pid))]
    (is (= :already-that-key (pr/check-rotate e ctrl alice "alice-key")))))

;; ── roles are not identities ────────────────────────────────────────────────

(deftest a-role-id-cannot-claim-a-principal
  ;; Ids below the floor are the backstop vault and the publishers, configured
  ;; at genesis and derived from no key.
  (is (= :reserved-account (pr/check-claim (ex) 5 pid)))
  (is (= :reserved-account (pr/check-claim (ex) (dec addr/floor) pid))))

;; ── it is under the root ────────────────────────────────────────────────────

(deftest the-binding-and-the-controller-are-in-the-state-root
  ;; A binding outside the root is a key replacement two replicas could
  ;; disagree about while reporting the same state.
  (let [m {:id 1 :tick 10 :lot 1 :n-levels 64}
        base (st/new-exchange {:market m :controller-authority ctrl})
        bound (-> base (pr/claim alice pid) (pr/confirm alice pid))]
    (is (not= (st/state-root base) (st/state-root bound))
        "binding a principal did not move the root")
    (is (not= (st/state-root (st/new-exchange {:market m}))
              (st/state-root base))
        "configuring the controller did not move the root")
    (is (not= (st/state-root bound)
              (st/state-root (pr/rotate bound alice "new-key")))
        "rotating the owner key did not move the root")))

(deftest a-pending-claim-is-in-the-state-root
  ;; Half the handshake. A sequencer able to add a claim without moving the
  ;; root could manufacture the owner's consent.
  (let [m {:id 1 :tick 10 :lot 1 :n-levels 64}
        base (st/new-exchange {:market m :controller-authority ctrl})]
    (is (not= (st/state-root base)
              (st/state-root (pr/claim base alice pid)))
        "a pending claim did not move the root")))

;; ── through the real surface, not just the pure functions ───────────────────

(defn- chain [cfg txs]
  (reduce (fn [e [i tx]]
            (st/apply-block e {:height (inc i) :ts (* 1000 (inc i)) :txs [tx]}))
          (st/new-exchange cfg)
          (map-indexed vector txs)))

(deftest the-handshake-works-through-apply-block
  ;; The pure functions above are the decision; this is the transaction path a
  ;; client actually uses. Without it the namespace could be entirely correct
  ;; and reachable from nothing -- which is the failure this workspace names
  ;; for an adapter that was right and connected to nothing.
  (let [m {:id 1 :tick 10 :lot 1 :n-levels 64}
        e (chain {:market m :controller-authority ctrl}
                 [{:tx :principal-claim :account alice :principal pid}
                  {:tx :principal-confirm :account ctrl :subject alice :principal pid}])]
    (is (= pid (pr/principal-of e alice)))
    (is (= alice (pr/account-of e pid)))
    (is (empty? (:rejected e)) "a well-formed handshake was rejected")))

(deftest apply-block-refuses-a-confirm-from-anyone-else
  (let [m {:id 1 :tick 10 :lot 1 :n-levels 64}
        e (chain {:market m :controller-authority ctrl}
                 [{:tx :principal-claim :account alice :principal pid}
                  {:tx :principal-confirm :account bob :subject alice :principal pid}])]
    (is (nil? (pr/principal-of e alice))
        "an account that is not the controller completed the binding")))

(deftest a-malformed-principal-is-a-rejection-not-a-halt
  ;; `apply-block` is total: a transaction it cannot apply is recorded and
  ;; skipped. A throw here would let anyone stop the chain with a typo.
  (let [m {:id 1 :tick 10 :lot 1 :n-levels 64}
        e (chain {:market m :controller-authority ctrl}
                 [{:tx :principal-claim :account alice :principal "alice"}])]
    (is (nil? (pr/principal-of e alice)))
    (is (seq (:rejected e)) "a malformed principal was accepted")))

(deftest the-api-judges-the-shape-and-names-the-reason
  ;; Pinned by literal, not by "it was refused". A refusal for some other
  ;; reason would count as a pass otherwise.
  (let [m {:id 1 :tick 10 :lot 1 :n-levels 64}
        e (st/new-exchange {:market m :controller-authority ctrl})]
    (is (nil? (api/validate e {:tx :principal-claim :account alice :principal pid})))
    (is (= :malformed-principal
           (api/validate e {:tx :principal-claim :account alice :principal "alice"})))
    (is (= :bad-account
           (api/validate e {:tx :principal-confirm :account ctrl :principal pid})))
    (is (= :missing-field
           (api/validate e {:tx :principal-rotate :account ctrl :subject alice})))
    (is (nil? (api/validate e {:tx :principal-rotate :account ctrl
                               :subject alice :pubkey "k"})))))
