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

(deftest a-proposal-alone-changes-nothing
  ;; `rotate` used to be public and to replace the key. It is private now and
  ;; called only by `mature-rotations`, because a controller that can replace a
  ;; key by calling one function is a controller nobody can answer. The
  ;; block-driven path is asserted further down.
  (let [e (-> (ex) (pr/claim alice pid) (pr/confirm alice pid)
              (assoc-in [:agents alice "agent-key"] {:expires nil}))]
    (is (nil? (pr/check-rotate e ctrl alice "new-key")))
    (let [e (pr/rotate-propose e alice "new-key" 100)]
      (is (= "alice-key" (get-in e [:account-keys alice]))
          "proposing replaced the key")
      (is (some? (get-in e [:agents alice]))
          "proposing dropped the agents before the holder could refuse")
      (is (= {:pubkey "new-key" :effective 100} (pr/pending-rotation e alice))
          "with no configured delay a proposal is effective immediately"))))

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
              (st/state-root (pr/rotate-propose bound alice "new-key" 7)))
        "queueing a replacement did not move the root")))

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

;; ── the controller proposes; the holder refuses ─────────────────────────────

(def ^:private m {:id 1 :tick 10 :lot 1 :n-levels 64})

(defn- bound-chain
  "A chain where alice holds a key and her principal is bound.

  The key is seeded rather than bound by a signature: `auth/accept` writes
  `:account-keys` only when `apply-block` is given the auth options, and these
  tests are about what happens AFTER an account has a key, not about how it
  got one. Without the seed every assertion below reads `nil` and passes or
  fails for a reason that has nothing to do with rotation."
  [delay]
  (-> (st/new-exchange {:market m :controller-authority ctrl
                        :rotation-delay-blocks delay})
      (assoc-in [:account-keys alice] "alice-key")
      (st/apply-block {:height 1 :ts 1000
                       :txs [{:tx :principal-claim :account alice :principal pid}]})
      (st/apply-block {:height 2 :ts 2000
                       :txs [{:tx :principal-confirm :account ctrl
                              :subject alice :principal pid}]})))

(deftest a-rotation-does-not-take-effect-in-the-block-it-is-proposed
  ;; The whole point. A controller that can replace an owner key between one
  ;; block and the next is a controller the holder cannot answer.
  (let [e (bound-chain 10)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})]
    (is (= "alice-key" (get-in e [:account-keys alice]))
        "the key changed in the block the rotation was proposed")
    (is (= {:pubkey "new-key" :effective 13} (pr/pending-rotation e alice)))))

(deftest a-rotation-matures-on-the-block-and-not-on-a-transaction
  ;; Driven by the block, like liquidation. A recovery that needed a keeper to
  ;; send something would end the veto window whenever the keeper felt like it.
  (let [e (bound-chain 2)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})
        at-4 (st/apply-block e {:height 4 :ts 4000 :txs []})
        at-5 (st/apply-block at-4 {:height 5 :ts 5000 :txs []})]
    (is (= "alice-key" (get-in at-4 [:account-keys alice])) "matured early")
    (is (= "new-key" (get-in at-5 [:account-keys alice])) "never matured")
    (is (nil? (pr/pending-rotation at-5 alice))
        "the proposal outlived the rotation it caused")))

(deftest the-holder-can-refuse
  (let [e (bound-chain 10)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})
        e (st/apply-block e {:height 4 :ts 4000
                             :txs [{:tx :principal-rotate-cancel :account alice}]})
        later (st/apply-block e {:height 99 :ts 99000 :txs []})]
    (is (nil? (pr/pending-rotation e alice)))
    (is (= "alice-key" (get-in later [:account-keys alice]))
        "a cancelled rotation matured anyway")))

(deftest a-cancel-in-the-maturing-block-still-wins
  ;; Transactions run before maturation, deliberately: refusing a recovery
  ;; costs a round trip, applying one the holder refused costs the account.
  (let [e (bound-chain 1)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})
        e (st/apply-block e {:height 4 :ts 4000
                             :txs [{:tx :principal-rotate-cancel :account alice}]})]
    (is (= "alice-key" (get-in e [:account-keys alice])
           ) "the rotation matured in the same block the holder refused it")))

(deftest nobody-else-can-refuse-for-the-holder
  (let [e (bound-chain 10)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})]
    (is (= :not-the-holder (pr/check-rotate-cancel e bob alice)))
    (is (= :not-the-holder (pr/check-rotate-cancel e ctrl alice))
        "the controller cancelled a rotation on the holder's behalf")))

(deftest the-controller-cannot-queue-a-second-proposal
  ;; Two live proposals would make the veto a race the holder loses: they
  ;; cancel the one they saw while another they did not see is maturing.
  (let [e (bound-chain 10)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})]
    (is (= :rotation-pending (pr/check-rotate e ctrl alice "other-key")))))

(deftest a-zero-delay-is-legal-and-immediate
  ;; A chain may choose no veto window. It must be a choice, which is why the
  ;; delay is genesis config and under the root rather than a default.
  (let [e (bound-chain 0)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})]
    (is (= "new-key" (get-in e [:account-keys alice])))))

(deftest the-delay-and-the-pending-rotation-are-in-the-state-root
  ;; Two replicas disagreeing about the window would mature the same rotation
  ;; at different heights, which is two different owners for one account.
  (let [a (st/new-exchange {:market m :controller-authority ctrl :rotation-delay-blocks 10})
        b (st/new-exchange {:market m :controller-authority ctrl :rotation-delay-blocks 20})]
    (is (not= (st/state-root a) (st/state-root b))
        "the veto window is not under the root"))
  (let [e (bound-chain 10)
        proposed (st/apply-block e {:height 3 :ts 3000
                                    :txs [{:tx :principal-rotate :account ctrl
                                           :subject alice :pubkey "new-key"}]})]
    (is (not= (st/state-root e) (st/state-root proposed))
        "a queued replacement is not under the root")))

(deftest a-rotation-that-matures-drops-the-agents
  (let [e (bound-chain 1)
        e (assoc-in e [:agents alice "agent-key"] {:expires nil})
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})
        e (st/apply-block e {:height 4 :ts 4000 :txs []})]
    (is (= "new-key" (get-in e [:account-keys alice])))
    (is (nil? (get-in e [:agents alice]))
        "a delegation the replaced key granted outlived it")))

(deftest the-holder-can-see-what-they-would-be-refusing
  ;; A veto nobody can see is not a veto. Without this the holder learns their
  ;; key was replaced by being refused `:wrong-key` on their own account,
  ;; which reads as a permissions problem rather than as a takeover.
  (let [e (bound-chain 10)
        before (api/account-state e alice)
        e (st/apply-block e {:height 3 :ts 3000
                             :txs [{:tx :principal-rotate :account ctrl
                                    :subject alice :pubkey "new-key"}]})
        after (api/account-state e alice)]
    (is (nil? (:pending-rotation before)))
    (is (= pid (:principal before)))
    (is (= 10 (:rotation-delay-blocks before)))
    (is (= {:pubkey "new-key" :effective 13} (:pending-rotation after))
        "the account could not see the replacement queued against it")))
