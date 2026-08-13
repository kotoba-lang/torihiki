(ns torihiki.state-test
  "The tests that matter for a chain: two replays of the same block must reach
  the same state root, and any difference in the block must change it."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.fixed :as fx]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.liquidation :as liq]
            [torihiki.api :as api]
            [torihiki.auth :as auth]
            [torihiki.commit :as cm]
            [torihiki.state :as st]))

(def mkt (assoc (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                :taker-fee-rate (fx/bps 3)
                :maker-fee-rate 0))

(defn- fresh []
  (st/new-exchange {:market mkt :book-opts {:n-levels 4096 :cap 65536 :ev-cap 65536}}))

(defn- funded [ex accts amount]
  (reduce (fn [e a] (st/apply-tx e {:tx :deposit :account a :amount amount})) ex accts))

(defn- tape
  "A deterministic block: deposits, two-sided quoting, and takers that cross."
  [n]
  (vec
   (for [i (range n)]
     (let [side (mod i 2)]
       (if (zero? (mod i 7))
         {:tx :order :account (+ 10 (mod i 5)) :market 1
          :side side :level (if (zero? side) 2050 1950) :qty (inc (mod i 4))}
         {:tx :order :account (+ 10 (mod i 5)) :market 1
          :side side :level (if (zero? side) (- 2000 1 (mod i 20))
                                             (+ 2000 1 (mod i 20)))
          :qty (inc (mod i 6))})))))

(deftest replay-is-deterministic
  (testing "the same block applied twice from genesis yields the same root"
    (let [block {:height 1 :ts 1000 :txs (tape 3000)}
          run #(-> (fresh) (funded (range 10 15) 100000000) (st/apply-block block))
          a (run) c (run)]
      (is (= (st/state-root a) (st/state-root c)))
      (is (pos? (bk/event-count (get-in a [:books 1])))
          "the tape must actually trade, or this proves nothing"))))

(deftest the-root-notices-a-changed-block
  (let [base (tape 500)
        run (fn [txs] (-> (fresh) (funded (range 10 15) 100000000)
                          (st/apply-block {:height 1 :ts 1000 :txs txs})))
        r0 (st/state-root (run base))]
    (testing "one extra lot on one order changes the root"
      (is (not= r0 (st/state-root (run (update-in base [7 :qty] inc))))))
    (testing "reordering two crossing orders changes the root"
      (let [swapped (assoc base 0 (nth base 1) 1 (nth base 0))]
        (is (not= r0 (st/state-root (run swapped)))
            "order is the only thing consensus decides; it had better matter")))
    (testing "a different block timestamp changes the root"
      (is (not= r0 (st/state-root
                    (-> (fresh) (funded (range 10 15) 100000000)
                        (st/apply-block {:height 1 :ts 1001 :txs base}))))))))

(deftest fills-credit-both-sides
  (let [ex (-> (fresh)
               (funded [10 11] 10000000)
               (st/apply-block
                {:height 1 :ts 1
                 :txs [{:tx :order :account 10 :market 1 :side bk/ask :level 1000 :qty 5}
                       {:tx :order :account 11 :market 1 :side bk/bid :level 1000 :qty 5}]}))
        maker (cl/position (:clearing ex) 10 1)
        taker (cl/position (:clearing ex) 11 1)]
    (is (= -5 (:size maker)) "the resting seller is short")
    (is (= 5 (:size taker)) "the aggressor is long")
    (is (= 0 (+ (:size maker) (:size taker))) "positions net to zero")
    (is (= 1000 (get-in ex [:marks 1])) "the fill sets the mark")))

(deftest positions-always-net-to-zero
  (testing "a perp market cannot create net exposure out of nothing"
    (let [ex (-> (fresh) (funded (range 10 15) 100000000)
                 (st/apply-block {:height 1 :ts 1 :txs (tape 2000)}))
          total (reduce + 0 (for [[_ a] (get-in ex [:clearing :accounts])
                                  [_ p] (:positions a)]
                              (:size p)))]
      (is (= 0 total)))))

(deftest liquidation-waterfall-reaches-the-book-first
  (testing "when the book can absorb the position, no vault or fund is touched"
    (let [markets {1 mkt}
          ;; thin account, long position, mark dropped enough to breach
          s (-> (cl/new-state)
                (assoc :insurance-fund 1000 :backstop-vault st/vault-account
                       :liquidation-clock {})
                (cl/deposit 100 1000)
                (cl/apply-fill 100 1 10 500 0))
          marks {1 404}
          take-fn (fn [delta _mark] [delta 404])]   ; the book absorbs it at the mark
      (is (cl/liquidatable? s 100 marks markets))
      (let [r (liq/liquidate s 100 1 404 0 markets liq/default-params take-fn)]
        (is (= :book (:stage r)))
        (is (= 0 (:size (cl/position (:state r) 100 1))) "the position is closed")
        (is (> (:insurance-fund (:state r)) 1000) "the liquidation fee funds the insurance pool")))))

(deftest large-positions-liquidate-in-slices
  (testing "a position above the notional threshold closes 20% at a time"
    (let [params liq/default-params
          ;; 1000 lots at mark 500 = 500,000 notional, well over the 100,000 threshold
          slice (liq/slice-size 1000 500 params)]
      (is (= 200 slice) "20% of 1000")
      (is (= 10 (liq/slice-size 10 500 params)) "a small position closes in one shot")
      (is (= 1 (liq/slice-size 1000000 1 (assoc params :partial-fraction 0)))
          "a slice never rounds to zero, or the position could never be closed"))))

(deftest cooldown-uses-logical-time
  (let [params liq/default-params
        s {:liquidation-clock {[100 1] 1000}}]
    (is (liq/cooling-down? s 100 1 1010 params) "10 logical seconds into a 30s cooldown")
    (is (not (liq/cooling-down? s 100 1 1030 params)))
    (is (not (liq/cooling-down? s 999 1 1010 params)) "a different account is unaffected")))

(deftest adl-ranking-is-a-total-order
  (testing "equal scores are broken by account id, so two nodes rank identically"
    (let [s {:accounts {5 {:collateral 1000 :positions {1 {:size 10 :entry-notional 4000}}}
                        3 {:collateral 1000 :positions {1 {:size 10 :entry-notional 4000}}}
                        9 {:collateral 1000 :positions {1 {:size 10 :entry-notional 4000}}}}}
          ranked (liq/adl-ranking s 1 500 -1)]
      (is (= 3 (count ranked)) "all three are profitable longs facing a short")
      (is (= [3 5 9] (mapv :account ranked))
          "identical scores must still produce one canonical order")
      (is (apply = (map :score ranked))))))

(deftest adl-only-ranks-the-opposite-side
  (let [s {:accounts {1 {:collateral 1000 :positions {7 {:size 10 :entry-notional 4000}}}
                      2 {:collateral 1000 :positions {7 {:size -10 :entry-notional -6000}}}}}]
    (testing "closing a short can only be absorbed by longs"
      (is (= [1] (mapv :account (liq/adl-ranking s 7 500 -1)))))
    (testing "and closing a long only by shorts"
      (is (= [2] (mapv :account (liq/adl-ranking s 7 500 1)))))))

;; ── the state root as a commitment ──────────────────────────────────────────

(deftest state-root-is-a-sha256-hex-digest
  (let [r (st/state-root (fresh))]
    (is (string? r))
    (is (= 64 (count r)))
    (is (re-matches #"[0-9a-f]{64}" r))))

(deftest canonical-bytes-are-bytes
  (let [bs (st/canonical-bytes (-> (fresh) (funded [10 11] 5000)))]
    (is (pos? (count bs)))
    (is (every? #(and (integer? %) (<= 0 % 255)) bs)
        "anything outside 0..255 would not survive being hashed as a byte")))

(deftest tagging-defeats-the-concatenation-ambiguity
  (testing "two states that a naive encoder would flatten identically do not collide"
    ;; account 12 holding 3, versus account 1 holding 23. Concatenated without
    ;; delimiters both read as \"1\" \"2\" \"3\"; this is the classic reason a
    ;; canonical encoding has to be length-prefixed and tagged.
    (let [a (-> (fresh) (st/apply-tx {:tx :deposit :account 12 :amount 3}))
          b (-> (fresh) (st/apply-tx {:tx :deposit :account 1 :amount 23}))]
      (is (not= (st/canonical-bytes a) (st/canonical-bytes b)))
      (is (not= (st/state-root a) (st/state-root b))))))

(deftest the-root-commits-to-resting-orders-not-just-balances
  (testing "two books with the same balances but different resting depth differ"
    (let [mk (fn [qty]
               (-> (fresh)
                   (funded [10] 1000000)
                   (st/apply-block {:height 1 :ts 1
                                    :txs [{:tx :order :account 10 :market 1
                                           :side bk/ask :level 1500 :qty qty}]})))]
      (is (not= (st/state-root (mk 5)) (st/state-root (mk 6)))))))

(deftest the-root-commits-to-time-priority
  (testing "the same depth at the same level, queued in a different order, differs"
    (let [mk (fn [[a b]]
               (-> (fresh)
                   (funded [10 11] 1000000)
                   (st/apply-block {:height 1 :ts 1
                                    :txs [{:tx :order :account a :market 1
                                           :side bk/ask :level 1500 :qty 4}
                                          {:tx :order :account b :market 1
                                           :side bk/ask :level 1500 :qty 4}]})))]
      (is (not= (st/state-root (mk [10 11])) (st/state-root (mk [11 10])))
          "queue position is state — whoever is at the head fills first"))))

;; ── the liquidation waterfall, past stage 1 ─────────────────────────────────
;;
;; Stages 2, 3 and 4 had no tests, and two of them were wrong: the vault
;; takeover applied the closing delta to the wrong side (doubling the position
;; it was meant to close) and the shortfall was computed with an inverted sign
;; (so the insurance fund GREW when it paid out). Both are the kind of bug that
;; only surfaces as missing money.

(def liq-markets {1 mkt})

(defn- insolvent-long
  "An account long 100 at 500 with only 1000 of collateral, marked at `mark`.
  Below ~490 it is not merely liquidatable but insolvent."
  [mark fund]
  {:state (-> (cl/new-state)
              (assoc :insurance-fund fund :backstop-vault st/vault-account
                     :liquidation-clock {})
              (cl/deposit 100 1000)
              (cl/apply-fill 100 1 100 500 0))
   :marks {1 mark}})

(deftest stage-two-closes-the-position-it-is-given
  (testing "the vault takeover must move the account TOWARD flat"
    (let [{:keys [state marks]} (insolvent-long 480 1000000)
          refuse (fn [_delta _mark] [0 0])
          r (liq/liquidate state 100 1 480 0 liq-markets liq/default-params refuse)]
      (is (contains? #{:vault :insurance :adl} (:stage r)))
      (is (= 0 (:size (cl/position (:state r) 100 1)))
          "the liquidated account is flat, not doubled")
      (is (= 100 (:size (cl/position (:state r) st/vault-account 1)))
          "and the vault inherited the long"))))

(deftest stage-three-drains-the-insurance-fund-rather-than-filling-it
  (testing "paying out must make the fund smaller"
    (let [{:keys [state]} (insolvent-long 480 1000000)
          refuse (fn [_ _] [0 0])
          before (:insurance-fund state)
          r (liq/liquidate state 100 1 480 0 liq-markets liq/default-params refuse)]
      (is (= :insurance (:stage r)))
      (is (pos? (:covered r)) "something was actually owed")
      (is (< (:insurance-fund (:state r)) before)
          "the fund paid; it must not have grown")
      (is (= (- before (:covered r)) (:insurance-fund (:state r))))
      (is (= 0 (get-in (:state r) [:accounts 100 :collateral]))
          "the account is made whole to zero, not left negative"))))

(deftest stage-four-actually-closes-counterparties
  (testing "ADL is the last line of defence and it has to execute, not rank"
    (let [;; no insurance fund at all, so the waterfall must reach ADL
          base (-> (cl/new-state)
                   (assoc :insurance-fund 0 :backstop-vault st/vault-account
                          :liquidation-clock {})
                   (cl/deposit 100 1000)
                   (cl/apply-fill 100 1 100 500 0)      ; the insolvent long
                   (cl/deposit 7 1000000)
                   (cl/apply-fill 7 1 -80 500 0)        ; a profitable short
                   (cl/deposit 8 1000000)
                   (cl/apply-fill 8 1 -50 500 0))       ; another
          mark 400
          refuse (fn [_ _] [0 0])
          r (liq/liquidate base 100 1 mark 0 liq-markets liq/default-params refuse)]
      (is (= :adl (:stage r)))
      (is (seq (:adl-closed r)) "counterparties were actually closed")
      (is (= 100 (reduce + 0 (map :qty (:adl-closed r))))
          "the whole inherited position was absorbed")
      (is (= 0 (:adl-unfilled r)))
      (is (= 0 (:size (cl/position (:state r) st/vault-account 1)))
          "so the vault is left flat")
      (testing "and the counterparties really moved"
        (let [s7 (:size (cl/position (:state r) 7 1))
              s8 (:size (cl/position (:state r) 8 1))]
          (is (> s7 -80) "account 7 was deleveraged")
          (is (= -30 (+ s7 s8))
              "130 lots of short existed, 100 were closed, 30 remain")
          (is (= 100 (- (fx/abs* -130) (fx/abs* (+ s7 s8))))
              "exactly 100 lots of short exposure was closed"))))))

(deftest adl-leaves-what-it-cannot-absorb-with-the-vault
  (testing "an unfilled remainder is reported, not silently dropped"
    (let [base (-> (cl/new-state)
                   (assoc :insurance-fund 0 :backstop-vault st/vault-account
                          :liquidation-clock {})
                   (cl/deposit 100 1000)
                   (cl/apply-fill 100 1 100 500 0)
                   (cl/deposit 7 1000000)
                   (cl/apply-fill 7 1 -30 500 0))     ; only 30 to absorb 100
          r (liq/liquidate base 100 1 400 0 liq-markets liq/default-params
                           (fn [_ _] [0 0]))]
      (is (= :adl (:stage r)))
      (is (= 30 (reduce + 0 (map :qty (:adl-closed r)))))
      (is (= 70 (:adl-unfilled r)) "the rest stays with the vault, and says so")
      (is (= 70 (:size (cl/position (:state r) st/vault-account 1)))))))

;; ── the non-negativity invariant ────────────────────────────────────────────
;;
;; `torihiki.commit` kept every merkle-sum leaf at zero because collateral
;; could go negative, and merkle-sum refuses a negative leaf. These are the
;; assertions that the reason is gone.

(deftest an-uncovered-shortfall-becomes-debt-not-a-negative-balance
  ;; An empty insurance fund: stage 3 covers nothing, so the hole survives the
  ;; waterfall. It used to survive as a negative `:collateral`.
  (let [{:keys [state]} (insolvent-long 480 0)
        refuse (fn [_ _] [0 0])
        r (liq/liquidate state 100 1 480 0 liq-markets liq/default-params refuse)
        acct (get-in (:state r) [:accounts 100])]
    (is (= 0 (:collateral acct)) "collateral must never be negative")
    (is (pos? (:deficit acct)) "and the hole must still be on the books")))

(deftest the-debt-is-the-hole-and-not-a-rounding-of-it
  ;; `collateral - deficit` is the single number the old encoding held, so the
  ;; split must be exact — nothing invented, nothing forgiven. Asserted on a
  ;; synthetic negative balance rather than on the waterfall's output, because
  ;; here the number it started from is known.
  (let [s (assoc-in (cl/new-state) [:accounts 100 :collateral] -250)
        s' (cl/settle-deficit s 100)
        {:keys [collateral deficit]} (get-in s' [:accounts 100])]
    (is (= 0 collateral))
    (is (= 250 deficit))
    (is (= -250 (- collateral deficit))
        "the split must reconstruct the number it replaced")))

(deftest no-account-is-underwater-after-the-waterfall
  ;; Asserted over every account the waterfall can touch, not just the one it
  ;; was aimed at: the vault inherits positions and auto-deleveraged
  ;; counterparties are filled at the bankruptcy price, so either can be the
  ;; one that ends up negative.
  (let [{:keys [state]} (insolvent-long 480 0)
        refuse (fn [_ _] [0 0])
        r (liq/liquidate state 100 1 480 0 liq-markets liq/default-params refuse)]
    (doseq [[a acct] (:accounts (:state r))]
      (is (not (neg? (or (:collateral acct) 0)))
          (str "account " a " holds negative collateral"))
      (is (not (neg? (or (:deficit acct) 0)))
          (str "account " a " owes a negative debt")))))

(deftest a-deposit-pays-the-debt-before-it-credits-the-balance
  (let [s (assoc-in (cl/new-state) [:accounts 100 :deficit] 300)]
    (let [s' (cl/deposit s 100 100)]
      (is (= 200 (get-in s' [:accounts 100 :deficit])))
      (is (= 0 (get-in s' [:accounts 100 :collateral] 0))
          "nothing may be credited while the debt stands"))
    (let [s' (cl/deposit s 100 500)]
      (is (= 0 (get-in s' [:accounts 100 :deficit])))
      (is (= 200 (get-in s' [:accounts 100 :collateral]))
          "and the remainder lands as collateral"))))

(deftest settling-a-solvent-account-changes-nothing
  ;; Idempotence, and the no-op case — `liquidate` settles the same account
  ;; twice when a counterparty is also the vault.
  (let [s (cl/deposit (cl/new-state) 100 1000)]
    (is (= s (cl/settle-deficit s 100)))
    (is (= (cl/settle-deficit s 100)
           (cl/settle-deficit (cl/settle-deficit s 100) 100)))))

;; ── the withdrawal exit ─────────────────────────────────────────────────────
;;
;; A withdrawal used to decrement the balance and end there. What replaces it
;; is a claim: an obligation the root commits to, which whoever holds the
;; escrow can be handed an inclusion proof of and pay against.

(defn- with-collateral [amount]
  (-> (fresh) (funded [10] amount)))

(deftest a-withdrawal-raises-a-claim
  (let [e (-> (with-collateral 1000)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))]
    (is (= 600 (get-in e [:clearing :accounts 10 :collateral])))
    (is (= 1 (count (:withdrawals e))))
    (is (= {:account 10 :amount 400 :height 0} (get-in e [:withdrawals 1])))
    (is (= 1 (:withdraw-seq e)) "claims are numbered, and the number is state")))

(deftest a-withdrawal-does-not-change-what-the-exchange-owes
  ;; The property the claim leaf exists for. Before, the total fell the moment
  ;; the balance did — the chain said it owed less while nobody had been paid.
  (let [before (with-collateral 1000)
        after (st/apply-tx before {:tx :withdraw :account 10 :amount 400})]
    (is (= (cm/reserves (st/canonical-leaves before))
           (cm/reserves (st/canonical-leaves after)))
        "value moved from an account leaf to a claim leaf; the total is the same")))

(deftest settling-is-the-only-thing-that-lowers-the-total
  (let [e (-> (with-collateral 1000)
              (assoc :bridge-authority 77)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))
        settled (st/apply-tx e {:tx :withdraw-settle :account 77 :claim 1})]
    (is (empty? (:withdrawals settled)))
    (is (= (- (cm/reserves (st/canonical-leaves e)) 400)
           (cm/reserves (st/canonical-leaves settled)))
        "the money actually left, so the obligation did too")))

(deftest only-the-bridge-may-say-the-money-left
  (let [e (-> (with-collateral 1000)
              (assoc :bridge-authority 77)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))]
    (is (= e (st/apply-tx e {:tx :withdraw-settle :account 10 :claim 1}))
        "the claimant settled their own claim")
    (is (= e (st/apply-tx e {:tx :withdraw-settle :account 78 :claim 1}))
        "a stranger settled somebody else's claim")))

(deftest with-no-bridge-nothing-can-be-settled
  ;; An unbacked chain has no exit, and this is what that looks like when it
  ;; is stated rather than hidden.
  (let [e (-> (with-collateral 1000)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))]
    (is (nil? (:bridge-authority e)))
    (is (= e (st/apply-tx e {:tx :withdraw-settle :account 10 :claim 1})))
    (is (= 1 (count (:withdrawals e))) "the claim stands")))

(deftest a-claim-can-be-handed-back-to-its-owner
  (let [e (-> (with-collateral 1000)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))
        cancelled (st/apply-tx e {:tx :withdraw-cancel :account 10 :claim 1})]
    (is (empty? (:withdrawals cancelled)))
    (is (= 1000 (get-in cancelled [:clearing :accounts 10 :collateral])))
    (is (= (cm/reserves (st/canonical-leaves e))
           (cm/reserves (st/canonical-leaves cancelled)))
        "a cancellation moves value back, it does not create or destroy it"))
  (let [e (-> (with-collateral 1000)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))]
    (is (= e (st/apply-tx e {:tx :withdraw-cancel :account 11 :claim 1}))
        "somebody else's claim was cancelled into their own balance")))

(deftest a-refused-withdrawal-raises-nothing
  (let [e (-> (with-collateral 100)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))]
    (is (empty? (:withdrawals e)))
    (is (= 0 (:withdraw-seq e)) "a refused withdrawal must not consume a number")
    (is (= 100 (get-in e [:clearing :accounts 10 :collateral])))))

(deftest a-claim-proves-itself-against-the-root
  ;; What the escrow operator actually does before paying.
  (let [e (-> (with-collateral 1000)
              (st/apply-tx {:tx :withdraw :account 10 :amount 400}))
        ls (st/canonical-leaves e)
        p (cm/proof ls "06:01:0000000000000001")]
    (is (some? p) "no proof for the claim")
    (is (cm/verify p (st/state-root e)))
    (is (= 400 (:sum p)) "the proof carries the amount owed")))


;; ── who may cancel an order ─────────────────────────────────────────────────

(deftest only-the-owner-may-cancel-an-order
  ;; `bk/cancel!` took `[book oid]` and cancelled whatever the id named, and
  ;; `apply-tx :cancel` passed the id out of the transaction and nothing else.
  ;; The signature proved who SENT the cancel; nothing compared that to who
  ;; placed the order. Any authenticated account could empty anybody's book.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [20 99] 1000000)
               (st/apply-tx {:tx :order :account 20 :market 1
                             :side bk/bid :level 400 :qty 10}))
        book (get-in ex [:books 1])
        oid (:oid (first (bk/level-orders book bk/bid 400)))]
    (is (some? oid) "account 20 must have a resting order, or this proves nothing")
    (is (= 1 (bk/resting-count book)))
    (let [after (st/apply-tx ex {:tx :cancel :account 99 :market 1 :oid oid})]
      (is (= 1 (bk/resting-count (get-in after [:books 1])))
          "a stranger cancelled somebody else's resting order"))
    (let [after (st/apply-tx ex {:tx :cancel :account 20 :market 1 :oid oid})]
      (is (= 0 (bk/resting-count (get-in after [:books 1])))
          "and the owner must still be able to cancel their own"))))

;; ── order management ────────────────────────────────────────────────────────

(defn- resting-of [ex acct]
  (let [book (get-in ex [:books 1])]
    (vec (for [side [bk/bid bk/ask]
               lvl (loop [l (bk/best book side) acc []]
                     (if (neg? l) acc (recur (bk/next-occupied book side l) (conj acc l))))
               o (bk/level-orders book side lvl)
               :when (= acct (:owner o))]
           (assoc o :side side :level lvl)))))

(deftest cancel-all-takes-one-maker-out-and-leaves-the-others
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [30 31] 100000000))
        ex (reduce (fn [e l] (st/apply-tx e {:tx :order :account 30 :market 1
                                             :side bk/bid :level l :qty 3}))
                   ex [380 390 400])
        ex (st/apply-tx ex {:tx :order :account 31 :market 1
                            :side bk/bid :level 395 :qty 5})]
    (is (= 3 (count (resting-of ex 30))))
    (is (= 1 (count (resting-of ex 31))))
    (let [after (st/apply-tx ex {:tx :cancel-all :account 30 :market 1})]
      (is (empty? (resting-of after 30)) "the maker must be out of the market")
      (is (= 1 (count (resting-of after 31)))
          "and nobody else's orders may be touched"))))

(deftest amending-down-keeps-the-place-in-the-queue
  ;; The property that makes amend worth having: trimming size must not send a
  ;; maker to the back of the line behind an order that has not moved.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [40 41] 100000000)
               (st/apply-tx {:tx :order :account 40 :market 1
                             :side bk/bid :level 400 :qty 10})
               (st/apply-tx {:tx :order :account 41 :market 1
                             :side bk/bid :level 400 :qty 10}))
        book (get-in ex [:books 1])
        [first-oid second-oid] (mapv :oid (bk/level-orders book bk/bid 400))
        oid (:oid (first (resting-of ex 40)))]
    (is (= first-oid oid) "account 40 must be at the head, or this proves nothing")
    (let [after (st/apply-tx ex {:tx :amend :account 40 :market 1
                                 :oid oid :level 400 :qty 4})
          q (bk/level-orders (get-in after [:books 1]) bk/bid 400)]
      (is (= [oid second-oid] (mapv :oid q))
          "the amended order must still be ahead of the one that did not move")
      (is (= 4 (:qty (first q))))
      (is (= 14 (bk/level-qty (get-in after [:books 1]) bk/bid 400))
          "the level total must follow the order down"))))

(deftest amending-up-goes-to-the-back
  ;; Size that was never queued must not inherit a place in line, or anybody
  ;; could hold the front with one lot and claim it with a thousand.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [50 51] 100000000)
               (st/apply-tx {:tx :order :account 50 :market 1
                             :side bk/bid :level 400 :qty 2})
               (st/apply-tx {:tx :order :account 51 :market 1
                             :side bk/bid :level 400 :qty 2}))
        oid (:oid (first (resting-of ex 50)))
        after (st/apply-tx ex {:tx :amend :account 50 :market 1
                               :oid oid :level 400 :qty 9})
        q (bk/level-orders (get-in after [:books 1]) bk/bid 400)]
    (is (= 2 (count q)))
    (is (= 51 (:owner (first q))) "the order that did not move must now be first")
    (is (= [2 9] (mapv :qty q)))))

(deftest only-the-owner-may-amend
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [60 61] 100000000)
               (st/apply-tx {:tx :order :account 60 :market 1
                             :side bk/bid :level 400 :qty 10}))
        oid (:oid (first (resting-of ex 60)))
        after (st/apply-tx ex {:tx :amend :account 61 :market 1
                               :oid oid :level 400 :qty 1})]
    (is (= 10 (:qty (first (resting-of after 60))))
        "a stranger resized somebody else's order")))

(deftest reduce-to-refuses-what-is-not-a-reduction
  (let [b (bk/new-book {:n-levels 256 :cap 1024 :ev-cap 256})
        oid (bk/place! b bk/bid 50 10 0 7)]
    (is (= 0 (bk/reduce-to! b oid 8 5)) "somebody else's order was resized")
    (is (= 0 (bk/reduce-to! b oid 7 10)) "an unchanged size counted as a reduction")
    (is (= 0 (bk/reduce-to! b oid 7 20)) "an increase was applied in place")
    (is (= 0 (bk/reduce-to! b oid 7 0)) "zero must be a cancel, not a reduction")
    (is (= 6 (bk/reduce-to! b oid 7 4)))
    (is (= 4 (bk/level-qty b bk/bid 50)))
    (is (= 1 (bk/resting-count b)) "a reduced order must still be resting")))

;; ── more than one market ────────────────────────────────────────────────────
;;
;; `:books` was always a map keyed by market id and every transaction has
;; always named its market, but nothing ever built a second one — so the
;; multi-market claim was shape without evidence.

(def mkt2 (assoc (cl/market {:id 2 :max-leverage 20 :tick 1 :lot 1})
                 :taker-fee-rate (fx/bps 5)
                 :maker-fee-rate 0))

(defn- two-markets []
  (st/new-exchange {:markets [mkt mkt2]
                    :book-opts {:n-levels 4096 :cap 65536 :ev-cap 65536}}))

(deftest two-markets-keep-separate-books
  (let [ex (-> (two-markets)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (st/apply-tx {:tx :oracle :market 2 :price 900})
               (funded [70] 100000000)
               (st/apply-tx {:tx :order :account 70 :market 1
                             :side bk/bid :level 400 :qty 3})
               (st/apply-tx {:tx :order :account 70 :market 2
                             :side bk/bid :level 800 :qty 5}))]
    (is (= 1 (bk/resting-count (get-in ex [:books 1]))))
    (is (= 1 (bk/resting-count (get-in ex [:books 2]))))
    (is (= 3 (bk/level-qty (get-in ex [:books 1]) bk/bid 400)))
    (is (= 5 (bk/level-qty (get-in ex [:books 2]) bk/bid 800)))
    (is (= 0 (bk/level-qty (get-in ex [:books 2]) bk/bid 400))
        "market 2 must not hold market 1's order")
    ;; and each market prices itself
    (is (not= (get-in ex [:marks 1]) (get-in ex [:marks 2])))))

(deftest cancel-all-is-per-market
  ;; It takes a market, so pulling out of one must not pull out of the other —
  ;; a maker hedging across markets would otherwise lose the hedge.
  (let [ex (-> (two-markets)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (st/apply-tx {:tx :oracle :market 2 :price 900})
               (funded [71] 100000000)
               (st/apply-tx {:tx :order :account 71 :market 1
                             :side bk/bid :level 400 :qty 3})
               (st/apply-tx {:tx :order :account 71 :market 2
                             :side bk/bid :level 800 :qty 5})
               (st/apply-tx {:tx :cancel-all :account 71 :market 1}))]
    (is (= 0 (bk/resting-count (get-in ex [:books 1]))))
    (is (= 1 (bk/resting-count (get-in ex [:books 2])))
        "cancelling on market 1 emptied market 2")))

(deftest the-root-commits-to-every-market
  (let [a (-> (two-markets) (st/apply-tx {:tx :oracle :market 1 :price 500}))
        b (-> (two-markets) (st/apply-tx {:tx :oracle :market 2 :price 500}))]
    (is (not= (st/state-root a) (st/state-root b))
        "the same price on a different market produced the same root")))

(deftest a-block-sweeps-every-market
  ;; The end-of-block liquidation sweep walks `(:books ex)`, so a second market
  ;; has to be swept too — an account underwater on market 2 must not survive
  ;; because market 1 was the one being watched.
  (let [ex (-> (two-markets)
               (st/apply-tx {:tx :oracle :market 2 :price 500})
               (funded [72] 1000)
               (funded [73] 100000000)
               (st/apply-tx {:tx :order :account 73 :market 2
                             :side bk/ask :level 500 :qty 100})
               (st/apply-tx {:tx :order :account 72 :market 2
                             :side bk/bid :level 500 :qty 100}))]
    (is (pos? (:size (cl/position (:clearing ex) 72 2))))
    (let [after (st/apply-block ex {:height 1 :ts 1
                                    :txs [{:tx :oracle :market 2 :price 300}]})]
      (is (not (cl/liquidatable? (:clearing after) 72 (:marks after) (:markets after)))
          "market 2 was never swept"))))

(deftest duplicate-market-ids-are-refused
  (is (thrown? #?(:clj Exception :cljs :default)
               (st/new-exchange {:markets [mkt mkt] :book-opts {}}))))

;; ── listing a market on a running chain ─────────────────────────────────────
;;
;; Markets were genesis-only, and a chain restores from a checkpoint — so
;; adding one to the source added it to a chain nobody was running.

(def ^:private spec2 {:max-leverage 20 :tick 10 :lot 1
                      :taker-fee-rate 500000 :maker-fee-rate 100000
                      :initial-margin-rate 50000000
                      :maintenance-margin-rate 25000000})

(defn- with-bridge [b]
  (assoc (fresh) :bridge-authority b))

(deftest the-bridge-can-list-a-market-and-it-works
  (let [ex (-> (with-bridge 77)
               (st/apply-tx {:tx :list-market :account 77 :market 5 :spec spec2
                             :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}}))]
    (is (contains? (:markets ex) 5))
    (is (some? (get-in ex [:books 5])))
    ;; and it is a real market: it prices, it rests orders, it roots
    (let [ex (-> ex
                 (st/apply-tx {:tx :oracle :market 5 :price 900})
                 (funded [80] 100000000)
                 (st/apply-tx {:tx :order :account 80 :market 5
                               :side bk/bid :level 100 :qty 4}))]
      (is (= 900 (get-in ex [:oracle 5])))
      (is (= 4 (bk/level-qty (get-in ex [:books 5]) bk/bid 100))))))

(deftest only-the-bridge-may-list
  (let [ex (with-bridge 77)]
    (is (= ex (st/apply-tx ex {:tx :list-market :account 78 :market 5 :spec spec2}))
        "a stranger listed a market")
    (is (= (dissoc ex :bridge-authority)
           (dissoc (st/apply-tx (dissoc ex :bridge-authority)
                                {:tx :list-market :account 78 :market 5 :spec spec2})
                   :bridge-authority))
        "a chain with no authority let somebody list")))

(deftest listing-cannot-replace-a-live-market
  ;; The market being replaced is one that has orders resting in it and
  ;; positions margined against it; a new book at the same id would strand
  ;; both while every id already signed keeps pointing there.
  (let [ex (-> (with-bridge 77)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [81] 100000000)
               (st/apply-tx {:tx :order :account 81 :market 1
                             :side bk/bid :level 400 :qty 6}))
        after (st/apply-tx ex {:tx :list-market :account 77 :market 1 :spec spec2})]
    (is (= 6 (bk/level-qty (get-in after [:books 1]) bk/bid 400))
        "an existing market was replaced and its book went with it"))
  (is (= :unknown-market
         (api/validate (-> (with-bridge 77))
                       {:tx :list-market :account 77 :market 1 :spec spec2}))))

(deftest a-listed-market-is-in-the-root
  (let [a (with-bridge 77)
        b (st/apply-tx a {:tx :list-market :account 77 :market 5 :spec spec2
                          :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})]
    (is (not= (st/state-root a) (st/state-root b))
        "listing a market did not change the root")))

;; ── the market's own parameters ─────────────────────────────────────────────
;;
;; They were not in the root: the margin rates decide who is liquidatable and
;; the fee rates decide what a fill costs, so two replicas configured
;; differently would liquidate different accounts and charge different fees —
;; and the root said nothing about the difference until somebody was near
;; liquidation.

(deftest the-root-commits-to-the-margin-rates
  (let [a (fresh)
        b (assoc-in a [:markets 1 :maintenance-margin-rate] 999)]
    (is (not= (st/state-root a) (st/state-root b))
        "a different maintenance margin produced the same root")))

(deftest the-root-commits-to-the-fees
  (let [a (fresh)
        b (assoc-in a [:markets 1 :taker-fee-rate] 999999)]
    (is (not= (st/state-root a) (st/state-root b))
        "a different taker fee produced the same root")))

(deftest the-root-commits-to-the-symbol-and-the-tick
  (let [a (fresh)]
    (is (not= (st/state-root a) (st/state-root (assoc-in a [:markets 1 :symbol] "OTHER")))
        "renaming a market did not change the root")
    (is (not= (st/state-root a) (st/state-root (assoc-in a [:markets 1 :tick] 7)))
        "a different tick produced the same root")))

(deftest the-root-commits-to-the-margin-tiers
  (let [tiered (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1
                           :margin-tiers [{:max-notional 1000 :max-leverage 20}]})
        a (st/new-exchange {:market (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1})
                            :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})
        b (st/new-exchange {:market tiered
                            :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})]
    (is (not= (st/state-root a) (st/state-root b))
        "a market with tiers rooted the same as one without")))

(deftest a-market-has-a-name
  (is (= "BTC-PERP" (:symbol (cl/market {:id 1 :symbol "BTC-PERP"
                                         :max-leverage 40 :tick 1 :lot 1}))))
  (is (= "MKT-7" (:symbol (cl/market {:id 7 :max-leverage 40 :tick 1 :lot 1})))
      "a market without a name still needs one to be called"))

(deftest the-bridge-can-amend-a-market-but-not-what-a-price-means
  (let [ex (-> (assoc (fresh) :bridge-authority 77)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [90] 100000000)
               (st/apply-tx {:tx :order :account 90 :market 1
                             :side bk/bid :level 400 :qty 3}))
        after (st/apply-tx ex {:tx :amend-market :account 77 :market 1
                               :spec {:symbol "BTC-PERP" :taker-fee-rate 999
                                      :tick 1 :lot 99}})]
    (is (= "BTC-PERP" (get-in after [:markets 1 :symbol])))
    (is (= 999 (get-in after [:markets 1 :taker-fee-rate])))
    (is (= (get-in ex [:markets 1 :tick]) (get-in after [:markets 1 :tick]))
        "the tick changed, so every resting order silently repriced")
    (is (= (get-in ex [:markets 1 :lot]) (get-in after [:markets 1 :lot])))
    (is (= 3 (bk/level-qty (get-in after [:books 1]) bk/bid 400))
        "the book must survive an amend")
    (is (not= (st/state-root ex) (st/state-root after))
        "amending a market did not change the root")))

(deftest only-the-bridge-may-amend-a-market
  (let [ex (assoc (fresh) :bridge-authority 77)]
    (is (= ex (st/apply-tx ex {:tx :amend-market :account 78 :market 1
                               :spec {:symbol "STOLEN"}})))
    (is (= :unknown-market
           (api/validate ex {:tx :amend-market :account 77 :market 9
                             :spec {:symbol "NOPE"}}))
        "a market that does not exist was amendable")))

;; ── fee tiers ───────────────────────────────────────────────────────────────
;;
;; Everybody paid the same rate however much they traded. The tier a fill is
;; charged at comes from the account's own rolling volume, so the two sides of
;; one fill can pay different rates — which is the point.

(def ^:private tiered-mkt
  (assoc (cl/market {:id 1 :symbol "T" :max-leverage 40 :tick 1 :lot 1})
         :taker-fee-rate (fx/bps 5)
         :maker-fee-rate (fx/bps 2)
         :fee-tiers [{:min-volume 0 :taker-fee-rate (fx/bps 5) :maker-fee-rate (fx/bps 2)}
                     {:min-volume 1000000 :taker-fee-rate (fx/bps 1) :maker-fee-rate 0}]))

(deftest a-flat-market-still-charges-its-flat-rate
  (let [ex (st/new-exchange {:market mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})]
    (is (= [(:taker-fee-rate mkt) (:maker-fee-rate mkt)]
           (cl/fee-rates-for (:clearing ex) 1 (get-in ex [:markets 1]) 0))
        "a market with no tiers must charge what it always did")))

(deftest volume-moves-an-account-into-a-cheaper-tier
  (let [ex (st/new-exchange {:market tiered-mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})
        spec (get-in ex [:markets 1])
        c (:clearing ex)]
    (is (= [(fx/bps 5) (fx/bps 2)] (cl/fee-rates-for c 1 spec 0))
        "a new account starts at the top rate")
    (let [c (cl/add-volume c 1 0 2000000)]
      (is (= [(fx/bps 1) 0] (cl/fee-rates-for c 1 spec 0))
          "volume did not move the account into the cheaper tier"))))

(deftest the-window-rolls-and-the-discount-can-be-lost
  ;; The property a cumulative total cannot have: an account that stops
  ;; trading loses the rate its old volume earned.
  (let [ex (st/new-exchange {:market tiered-mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})
        spec (get-in ex [:markets 1])
        c (cl/add-volume (:clearing ex) 1 0 2000000)
        one-epoch cl/volume-epoch-blocks]
    (is (= [(fx/bps 1) 0] (cl/fee-rates-for c 1 spec 0)))
    (is (= [(fx/bps 1) 0] (cl/fee-rates-for c 1 spec one-epoch))
        "the previous epoch must still count")
    (is (= [(fx/bps 5) (fx/bps 2)] (cl/fee-rates-for c 1 spec (* 3 one-epoch)))
        "volume from three epochs ago still bought a discount")))

(deftest the-tier-and-not-the-market-decides-what-a-fill-costs
  ;; Two takers, identical trades, different volume. The first version of this
  ;; compared a TAKER against a MAKER and passed with the tier lookup removed —
  ;; their rates differ in a flat market too, so it proved nothing. Comparing
  ;; the same role at two volumes is what only tiers can produce.
  (letfn [(fee-for [volume]
            (let [ex (-> (st/new-exchange {:market tiered-mkt
                                           :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}})
                         (st/apply-tx {:tx :oracle :market 1 :price 500})
                         (funded [95 96] 1000000000))
                  ex (cond-> ex
                       (pos? volume) (update :clearing cl/add-volume 95 0 volume))
                  before (get-in ex [:clearing :accounts 95 :collateral])
                  ex (-> ex
                         (st/apply-tx {:tx :order :account 96 :market 1
                                       :side bk/ask :level 500 :qty 20000})
                         (st/apply-tx {:tx :order :account 95 :market 1
                                       :side bk/bid :level 500 :qty 20000}))]
              (- before (get-in ex [:clearing :accounts 95 :collateral]))))]
    (let [poor (fee-for 0)
          rich (fee-for 5000000)]
      (is (pos? poor) "the taker paid nothing, so the sizes are too small to measure")
      (is (< rich poor)
          "volume bought no discount — the tier was not read"))))

(deftest the-root-commits-to-volume-and-to-the-fee-schedule
  (let [a (st/new-exchange {:market tiered-mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})
        b (update a :clearing cl/add-volume 1 0 5000000)]
    (is (not= (st/state-root a) (st/state-root b))
        "an account's volume is outside the root, so a replica could invent a discount"))
  (let [a (st/new-exchange {:market tiered-mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})
        b (st/new-exchange {:market (assoc tiered-mkt :fee-tiers
                                           [{:min-volume 1 :taker-fee-rate 0 :maker-fee-rate 0}])
                            :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})]
    (is (not= (st/state-root a) (st/state-root b))
        "the fee schedule is outside the root")))

;; ── TWAP ────────────────────────────────────────────────────────────────────
;;
;; An order worked over time instead of all at once. The schedule is in BLOCKS
;; because the engine has no clock.

(defn- twap-fixture
  "A book with liquidity on both sides and an account to work an order into it."
  []
  (-> (fresh)
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (funded [10 11] 1000000000)
      ;; a wall of asks for a buy TWAP to eat
      (st/apply-tx {:tx :order :account 11 :market 1
                    :side bk/ask :level 500 :qty 1000})))

(deftest a-twap-does-not-fill-in-the-block-it-was-submitted
  ;; Otherwise it is a market order wearing a schedule.
  (let [ex (-> (twap-fixture)
               (st/apply-tx {:tx :twap :account 10 :market 1
                             :side bk/bid :qty 100 :slices 4 :every 1}))]
    (is (= 1 (count (:twaps ex))))
    (is (= 0 (:size (cl/position (:clearing ex) 10 1)))
        "the TWAP filled immediately")))

(deftest a-twap-fills-over-blocks-and-finishes-exactly
  (let [ex (-> (twap-fixture)
               (st/apply-tx {:tx :twap :account 10 :market 1
                             :side bk/bid :qty 100 :slices 4 :every 1}))
        step (fn [e h] (st/apply-block e {:height h :ts h :txs []}))
        after-1 (step ex 1)
        after-2 (step after-1 2)
        after-4 (-> after-2 (step 3) (step 4))]
    (is (pos? (:size (cl/position (:clearing after-1) 10 1)))
        "the first slice never fired")
    (is (< (:size (cl/position (:clearing after-1) 10 1))
           (:size (cl/position (:clearing after-2) 10 1)))
        "the second slice added nothing")
    (is (= 100 (:size (cl/position (:clearing after-4) 10 1)))
        "the schedule did not fill the whole quantity")
    (is (empty? (:twaps after-4)) "a finished TWAP must not stay on the books")))

(deftest the-remainder-lands-on-the-last-slice
  ;; 10 over 4 slices is 2,2,2,4 — not 2,2,2,2 with two lots quietly lost.
  (let [ex (-> (twap-fixture)
               (st/apply-tx {:tx :twap :account 10 :market 1
                             :side bk/bid :qty 10 :slices 4 :every 1}))
        run (reduce (fn [e h] (st/apply-block e {:height h :ts h :txs []}))
                    ex (range 1 6))]
    (is (= 10 (:size (cl/position (:clearing run) 10 1)))
        "the rounding remainder was dropped")))

(deftest every-controls-the-spacing
  (let [ex (-> (twap-fixture)
               (st/apply-tx {:tx :twap :account 10 :market 1
                             :side bk/bid :qty 100 :slices 4 :every 3}))
        after-1 (st/apply-block ex {:height 1 :ts 1 :txs []})
        after-2 (st/apply-block after-1 {:height 2 :ts 2 :txs []})]
    (is (pos? (:size (cl/position (:clearing after-1) 10 1))))
    (is (= (:size (cl/position (:clearing after-1) 10 1))
           (:size (cl/position (:clearing after-2) 10 1)))
        "a slice fired before its interval had passed")))

(deftest only-the-owner-may-cancel-a-twap
  (let [ex (-> (twap-fixture)
               (st/apply-tx {:tx :twap :account 10 :market 1
                             :side bk/bid :qty 100 :slices 4 :every 1}))]
    (is (= 1 (count (:twaps (st/apply-tx ex {:tx :cancel-twap :account 11 :id 1})))))
    (is (empty? (:twaps (st/apply-tx ex {:tx :cancel-twap :account 10 :id 1}))))))

(deftest a-working-twap-is-in-the-root
  (let [a (twap-fixture)
        b (st/apply-tx a {:tx :twap :account 10 :market 1
                          :side bk/bid :qty 100 :slices 4 :every 1})]
    (is (not= (st/state-root a) (st/state-root b))
        "a working schedule sat outside the root")))

(deftest a-schedule-of-one-is-refused
  (let [ex (twap-fixture)]
    (is (= :missing-field (api/validate ex {:tx :twap :account 10 :market 1
                                            :side bk/bid :qty 10 :slices 1 :every 1})))
    (is (= :bad-quantity (api/validate ex {:tx :twap :account 10 :market 1
                                           :side bk/bid :qty 3 :slices 4 :every 1}))
        "a schedule whose slices round to nothing was accepted")))

;; ── scale orders ────────────────────────────────────────────────────────────

(deftest a-scale-places-a-ladder-in-one-transaction
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [20] 1000000000)
               (st/apply-tx {:tx :scale :account 20 :market 1
                             :side bk/bid :level 400 :qty 2 :count* 4 :step 5}))
        book (get-in ex [:books 1])]
    (is (= 4 (bk/resting-count book)))
    (is (= [2 2 2 2] (mapv #(bk/level-qty book bk/bid %) [400 395 390 385]))
        "the rungs are not where the ladder said")))

(deftest a-ladder-walks-away-from-the-book
  ;; A bid ladder that walked UP would cross and take. The direction is not
  ;; the caller's to choose.
  (let [asks (-> (fresh)
                 (st/apply-tx {:tx :oracle :market 1 :price 500})
                 (funded [21] 1000000000)
                 (st/apply-tx {:tx :scale :account 21 :market 1
                               :side bk/ask :level 600 :qty 1 :count* 3 :step 10}))
        book (get-in asks [:books 1])]
    (is (= [1 1 1] (mapv #(bk/level-qty book bk/ask %) [600 610 620])))
    (is (= 0 (bk/level-qty book bk/ask 590))
        "an ask ladder walked toward the book")))

(deftest a-ladder-that-runs-off-the-book-keeps-what-fits
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [22] 1000000000)
               ;; step down from 5 by 3: 5, 2, then -1 and -4 are off the end
               (st/apply-tx {:tx :scale :account 22 :market 1
                             :side bk/bid :level 5 :qty 1 :count* 4 :step 3}))]
    (is (= 2 (bk/resting-count (get-in ex [:books 1])))
        "the rungs that fit were lost with the ones that did not")))

(deftest a-ladder-of-one-and-a-step-of-zero-are-refused
  (let [ex (fresh)]
    (is (= :missing-field (api/validate ex {:tx :scale :account 1 :market 1
                                            :side bk/bid :level 100 :qty 1
                                            :count* 1 :step 5})))
    (is (= :missing-field (api/validate ex {:tx :scale :account 1 :market 1
                                            :side bk/bid :level 100 :qty 1
                                            :count* 4 :step 0}))
        "a step of zero stacks the whole ladder on one level")))

(deftest the-signature-covers-the-shape-of-the-ladder
  (let [tx {:tx :scale :market 1 :side 0 :level 100 :qty 1 :count* 4 :step 5}]
    (is (not= (auth/signing-payload "c" 1 1 tx)
              (auth/signing-payload "c" 1 1 (assoc tx :count* 40)))
        "the number of rungs was not signed")
    (is (not= (auth/signing-payload "c" 1 1 tx)
              (auth/signing-payload "c" 1 1 (assoc tx :step 50)))
        "the spacing was not signed")))

(deftest the-root-commits-to-publisher-stake
  ;; Stake decides whose price wins, so a replica that disagreed about it would
  ;; compute a different mark from the same submissions — and margin reads the
  ;; mark.
  (let [a (st/new-exchange {:market mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}
                            :oracle-publishers [1 2] :publisher-stake {1 10 2 1}})
        b (st/new-exchange {:market mkt :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}
                            :oracle-publishers [1 2] :publisher-stake {1 1 2 10}})]
    (is (not= (st/state-root a) (st/state-root b))
        "the weights sat outside the root")))

;; ── builder codes ───────────────────────────────────────────────────────────
;;
;; A share of what a trade costs, paid to whoever wrote the client that routed
;; it — by the trader who signed the order, not by the venue.

(defn- builder-fixture []
  (-> (fresh)
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (funded [30 31 32] 1000000000)
      ;; 31 quotes, 30 will take, 32 is the builder
      (st/apply-tx {:tx :order :account 31 :market 1
                    :side bk/ask :level 500 :qty 20000})))

(deftest a-builder-is-paid-by-the-taker-and-not-by-the-venue
  (let [ex (builder-fixture)
        before-taker (get-in ex [:clearing :accounts 30 :collateral])
        before-builder (get-in ex [:clearing :accounts 32 :collateral])
        before-fees (get-in ex [:clearing :fees-collected])
        after (st/apply-tx ex {:tx :order :account 30 :market 1
                               :side bk/bid :level 500 :qty 20000
                               :builder 32 :builder-fee (fx/bps 5)})
        paid (- before-taker (get-in after [:clearing :accounts 30 :collateral]))
        earned (- (get-in after [:clearing :accounts 32 :collateral]) before-builder)]
    (is (pos? earned) "the builder was not paid")
    (is (> paid earned) "the taker paid only the builder, not the exchange too")
    (is (= (- (get-in after [:clearing :fees-collected]) before-fees)
           (- paid earned))
        "the builder's cut landed in the venue's fees")))

(deftest the-maker-never-pays-a-builder-it-did-not-choose
  (let [ex (builder-fixture)
        before-maker (get-in ex [:clearing :accounts 31 :collateral])
        after (st/apply-tx ex {:tx :order :account 30 :market 1
                               :side bk/bid :level 500 :qty 20000
                               :builder 32 :builder-fee (fx/bps 5)})
        ;; the maker's collateral moves by its own fee only, which is what it
        ;; would have moved without a builder at all
        with-builder (- before-maker (get-in after [:clearing :accounts 31 :collateral]))
        plain (let [a (st/apply-tx ex {:tx :order :account 30 :market 1
                                       :side bk/bid :level 500 :qty 20000})]
                (- before-maker (get-in a [:clearing :accounts 31 :collateral])))]
    (is (= with-builder plain)
        "the maker paid for the taker's builder")))

(deftest a-builder-fee-over-the-cap-is-refused
  (let [ex (builder-fixture)]
    (is (nil? (api/validate ex {:tx :order :account 30 :market 1
                                :side bk/bid :level 500 :qty 1
                                :builder 32 :builder-fee api/max-builder-fee-rate})))
    (is (= :builder-fee-too-high
           (api/validate ex {:tx :order :account 30 :market 1
                             :side bk/bid :level 500 :qty 1
                             :builder 32 :builder-fee (inc api/max-builder-fee-rate)}))
        "a client could charge whatever it wanted")))

(deftest the-signature-covers-who-is-paid-for-routing
  (let [tx {:tx :order :market 1 :side 0 :level 500 :qty 1
            :builder 32 :builder-fee 100}]
    (is (not= (auth/signing-payload "c" 1 1 tx)
              (auth/signing-payload "c" 1 1 (assoc tx :builder 99)))
        "the builder was not signed")
    (is (not= (auth/signing-payload "c" 1 1 tx)
              (auth/signing-payload "c" 1 1 (assoc tx :builder-fee 1000000)))
        "the builder's cut was not signed")))

;; ── referral ────────────────────────────────────────────────────────────────
;;
;; A share of what the VENUE keeps, not a surcharge on the trader.

(defn- referral-fixture []
  (-> (fresh)
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (funded [40 41 42] 1000000000)
      (st/apply-tx {:tx :order :account 41 :market 1
                    :side bk/ask :level 500 :qty 20000})))

(deftest a-referrer-is-paid-out-of-the-venues-fee
  ;; TWO fixtures, not one branched twice. `bk/place!` mutates the book's slab
  ;; in place — that is deliberate and documented — so two futures taken from
  ;; one state share a book, and the first to run eats the liquidity the second
  ;; was going to trade against. The first version of this did exactly that and
  ;; measured a fill that never happened.
  (let [a (referral-fixture)
        before-a (get-in a [:clearing :accounts 40 :collateral])
        plain (st/apply-tx a {:tx :order :account 40 :market 1
                              :side bk/bid :level 500 :qty 20000})
        b (referral-fixture)
        before-b (get-in b [:clearing :accounts 40 :collateral])
        before-ref (get-in b [:clearing :accounts 42 :collateral])
        referred (-> b
                     (st/apply-tx {:tx :set-referrer :account 40 :referrer 42})
                     (st/apply-tx {:tx :order :account 40 :market 1
                                   :side bk/bid :level 500 :qty 20000}))
        paid-plain (- before-a (get-in plain [:clearing :accounts 40 :collateral]))
        paid-referred (- before-b (get-in referred [:clearing :accounts 40 :collateral]))
        earned (- (get-in referred [:clearing :accounts 42 :collateral]) before-ref)]
    (is (pos? paid-plain) "nothing filled, so nothing is being measured")
    (is (pos? earned) "the referrer was not paid")
    (is (= paid-plain paid-referred)
        "being referred changed what the trader paid — that is a surcharge")
    (is (= (- (get-in plain [:clearing :fees-collected])
              (get-in referred [:clearing :fees-collected]))
           earned)
        "the referrer's share did not come out of the venue's fee")))

(deftest a-referrer-is-bound-once
  (let [ex (-> (referral-fixture)
               (st/apply-tx {:tx :set-referrer :account 40 :referrer 42})
               (st/apply-tx {:tx :set-referrer :account 40 :referrer 41}))]
    (is (= 42 (get-in ex [:clearing :referrers 40]))
        "the last client to touch the account took the stream")))

(deftest an-account-cannot-refer-itself
  (let [ex (st/apply-tx (referral-fixture) {:tx :set-referrer :account 40 :referrer 40})]
    (is (nil? (get-in ex [:clearing :referrers 40]))
        "a rebate was collected by pointing the referral at yourself")))

(deftest the-referrer-is-in-the-root
  (let [a (referral-fixture)
        b (st/apply-tx a {:tx :set-referrer :account 40 :referrer 42})]
    (is (not= (st/state-root a) (st/state-root b))
        "who is paid on every future fill sat outside the root")))

(deftest the-signature-covers-the-referrer
  (is (not= (auth/signing-payload "c" 1 1 {:tx :set-referrer :referrer 42})
            (auth/signing-payload "c" 1 1 {:tx :set-referrer :referrer 99}))
      "an attacker could claim somebody's fee stream for the life of the account"))

;; ── vaults ──────────────────────────────────────────────────────────────────
;;
;; The backstop was an ordinary account that could only hold its operator's
;; money. A vault takes outside deposits for shares, which is what stands
;; behind the liquidation waterfall on a venue that has one.

(def ^:private vault-acct 900)

(defn- vault-fixture []
  (-> (fresh)
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (funded [50 51] 1000000)))

(deftest the-first-deposit-sets-the-share-price
  (let [ex (st/apply-tx (vault-fixture)
                        {:tx :vault-deposit :account 50 :vault vault-acct :amount 1000})]
    (is (= [1000 1000] (cl/vault-shares (:clearing ex) vault-acct 50)))
    (is (= 1000 (get-in ex [:clearing :accounts vault-acct :collateral])))
    (is (= 999000 (get-in ex [:clearing :accounts 50 :collateral])))))

(deftest a-second-depositor-buys-at-the-current-price
  ;; The vault earned: 1000 in, 2000 held. A new depositor of 1000 must get
  ;; half the shares the first did, not the same number.
  (let [ex (-> (vault-fixture)
               (st/apply-tx {:tx :vault-deposit :account 50 :vault vault-acct :amount 1000})
               (update-in [:clearing :accounts vault-acct :collateral] + 1000)
               (st/apply-tx {:tx :vault-deposit :account 51 :vault vault-acct :amount 1000}))]
    (is (= 500 (first (cl/vault-shares (:clearing ex) vault-acct 51)))
        "the second depositor bought at the founding price")
    (is (= 1500 (second (cl/vault-shares (:clearing ex) vault-acct 51))))))

(deftest withdrawing-pays-a-share-of-what-the-vault-holds
  (let [ex (-> (vault-fixture)
               (st/apply-tx {:tx :vault-deposit :account 50 :vault vault-acct :amount 1000})
               ;; the vault doubles its money
               (update-in [:clearing :accounts vault-acct :collateral] + 1000)
               (st/apply-tx {:tx :vault-withdraw :account 50 :vault vault-acct :shares 500}))]
    (is (= 1000 (get-in ex [:clearing :accounts vault-acct :collateral]))
        "half the shares did not take half the pool")
    (is (= 500 (first (cl/vault-shares (:clearing ex) vault-acct 50))))
    (is (= 1000000 (get-in ex [:clearing :accounts 50 :collateral]))
        "the depositor got back their stake plus their share of the gain")))

(deftest you-cannot-withdraw-shares-you-do-not-hold
  (let [ex (-> (vault-fixture)
               (st/apply-tx {:tx :vault-deposit :account 50 :vault vault-acct :amount 1000}))
        after (st/apply-tx ex {:tx :vault-withdraw :account 51 :vault vault-acct :shares 100})]
    (is (= (get-in ex [:clearing :accounts vault-acct :collateral])
           (get-in after [:clearing :accounts vault-acct :collateral]))
        "a stranger withdrew from a vault they had not funded")))

(deftest a-deposit-too-small-to-buy-a-share-is-refused
  (let [ex (-> (vault-fixture)
               (st/apply-tx {:tx :vault-deposit :account 50 :vault vault-acct :amount 1000})
               ;; a large gain makes one share expensive
               (update-in [:clearing :accounts vault-acct :collateral] + 1000000)
               (st/apply-tx {:tx :vault-deposit :account 51 :vault vault-acct :amount 1}))]
    (is (= 0 (first (cl/vault-shares (:clearing ex) vault-acct 51))))
    (is (= 1000000 (get-in ex [:clearing :accounts 51 :collateral]))
        "the deposit was taken without minting anything — that is a donation")))

(deftest vault-shares-are-in-the-root
  (let [a (vault-fixture)
        b (st/apply-tx a {:tx :vault-deposit :account 50 :vault vault-acct :amount 1000})]
    (is (not= (st/state-root a) (st/state-root b))
        "a claim on the vault's collateral sat outside the root")))

;; ── staking and delegation ──────────────────────────────────────────────────
;;
;; `:publisher-stake` was a number in the config — an operator's assertion
;; about how much each voice weighs. A bond is the same claim made by somebody
;; putting collateral behind it, which is what makes the weight cost something.

(deftest bonded-collateral-is-not-free
  ;; A bond that could also back a position would be counted twice: as
  ;; security the chain can slash and as margin the trader can lose.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [60] 1000000))
        before (cl/free-collateral (:clearing ex) 60 (:marks ex) (:markets ex))
        after-ex (st/apply-tx ex {:tx :bond :account 60 :validator 7 :amount 400000})
        after (cl/free-collateral (:clearing after-ex) 60 (:marks after-ex) (:markets after-ex))]
    (is (= 400000 (cl/bonded (:clearing after-ex) 60)))
    (is (= (- before 400000) after)
        "bonded collateral was still spendable")
    (is (= 1000000 (get-in after-ex [:clearing :accounts 60 :collateral]))
        "the money left the account — slashing would have nothing to take")))

(deftest you-cannot-bond-what-you-do-not-have
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [61] 1000)
               (st/apply-tx {:tx :bond :account 61 :validator 7 :amount 999999}))]
    (is (= 0 (cl/bonded (:clearing ex) 61)))))

(deftest unbonding-waits-and-then-releases
  ;; A bond withdrawable the instant it is at risk is not security: a validator
  ;; would unbond the moment it equivocated.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [62] 1000000)
               (st/apply-tx {:tx :bond :account 62 :validator 7 :amount 500000})
               (st/apply-tx {:tx :unbond :account 62 :validator 7 :amount 500000}))]
    (is (= 0 (cl/bonded (:clearing ex) 62)) "the stake still counts as weight")
    (is (= 500000 (reduce + 0 (map :amount (get-in ex [:clearing :unbonding 62]))))
        "the unbonding was not queued")
    ;; too early
    (let [early (st/apply-tx (assoc ex :height 10) {:tx :collect-unbonded :account 62})]
      (is (seq (get-in early [:clearing :unbonding 62]))
          "it was collectable before the delay had passed"))
    (let [late (st/apply-tx (assoc ex :height (inc cl/unbond-delay-blocks))
                            {:tx :collect-unbonded :account 62})]
      (is (empty? (get-in late [:clearing :unbonding 62])))
      (is (= 1000000 (cl/free-collateral (:clearing late) 62 (:marks late) (:markets late)))
          "the collateral did not come back free"))))

(deftest bonds-decide-whose-price-wins
  (let [subs {1 {:price 100 :ts 0} 2 {:price 100 :ts 0} 3 {:price 500 :ts 0}}
        base (-> (fresh)
                 (assoc :oracle-publishers #{1 2 3})
                 (assoc :oracle-params {:quorum 2 :max-age 100})
                 (assoc-in [:clearing :accounts 3 :collateral] 1000000)
                 (assoc-in [:oracle-submissions 1] subs))
        ;; publisher 3 bonds; 1 and 2 do not
        bonded (assoc-in base [:clearing :bonds 3] {3 900000})
        price-of (fn [e]
                   (:oracle (st/apply-tx e {:tx :oracle-submit :account 3
                                            :market 1 :price 500})
                            ))]
    (is (some? (price-of base)))
    (is (not= (get-in (st/apply-tx base {:tx :oracle-submit :account 3 :market 1 :price 500})
                      [:oracle 1])
              (get-in (st/apply-tx bonded {:tx :oracle-submit :account 3 :market 1 :price 500})
                      [:oracle 1]))
        "bonding changed nothing about whose price won")))

(deftest bonds-are-in-the-root
  (let [a (-> (fresh) (funded [63] 1000000))
        b (st/apply-tx a {:tx :bond :account 63 :validator 7 :amount 100})]
    (is (not= (st/state-root a) (st/state-root b))
        "a claim the chain can slash sat outside the root")))

;; ── spot ────────────────────────────────────────────────────────────────────
;;
;; A spot trade is an exchange of two things both sides already hold. Nothing
;; is margined: no position to liquidate, no funding, no mark to be wrong
;; about.

(def ^:private spot-mkt
  (assoc (cl/market {:id 3 :symbol "BTC-USD" :max-leverage 1 :tick 1 :lot 1})
         :kind :spot :asset 77
         :taker-fee-rate (fx/bps 5) :maker-fee-rate (fx/bps 2)))

(defn- spot-fixture []
  (-> (st/new-exchange {:markets [mkt spot-mkt]
                        :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}})
      (funded [70 71] 100000000)
      ;; 71 holds the asset to sell
      (assoc-in [:clearing :balances 71 77] 1000)))

(deftest a-spot-fill-exchanges-balance-for-collateral
  (let [ex (-> (spot-fixture)
               (st/apply-tx {:tx :order :account 71 :market 3
                             :side bk/ask :level 500 :qty 100})
               (st/apply-tx {:tx :order :account 70 :market 3
                             :side bk/bid :level 500 :qty 100}))
        c (:clearing ex)]
    (is (= 100 (get-in c [:balances 70 77])) "the buyer did not receive the asset")
    (is (= 900 (get-in c [:balances 71 77])) "the seller still holds what it sold")
    (is (< (get-in c [:accounts 70 :collateral]) 100000000) "the buyer paid nothing")
    (is (> (get-in c [:accounts 71 :collateral]) 100000000) "the seller was not paid")
    (is (empty? (get-in c [:accounts 70 :positions]))
        "a spot trade opened a margined position")))

(deftest a-sell-of-what-you-do-not-hold-is-refused
  ;; A perp order asking for too much margin is a question answered later. A
  ;; spot sell of an asset you do not hold would CREATE it.
  (let [ex (spot-fixture)
        after (st/apply-tx ex {:tx :order :account 70 :market 3
                               :side bk/ask :level 500 :qty 10})]
    (is (= 0 (bk/resting-count (get-in after [:books 3])))
        "an unbacked sell rested on the book")))

(deftest resting-spot-orders-commit-what-they-will-owe
  ;; Without this an account could rest ten sells of everything it owns and
  ;; honour whichever filled first.
  (let [ex (-> (spot-fixture)
               (st/apply-tx {:tx :order :account 71 :market 3
                             :side bk/ask :level 500 :qty 1000}))]
    (is (= 1000 (get-in ex [:clearing :committed 71 77])))
    (is (= 0 (cl/balance (:clearing ex) 71 77))
        "the whole holding is committed, so nothing is free")
    (let [again (st/apply-tx ex {:tx :order :account 71 :market 3
                                 :side bk/ask :level 400 :qty 1})]
      (is (= 1 (bk/resting-count (get-in again [:books 3])))
          "a second sell rested against a holding already spoken for"))))

(deftest cancelling-a-spot-order-gives-the-reservation-back
  (let [ex (-> (spot-fixture)
               (st/apply-tx {:tx :order :account 71 :market 3
                             :side bk/ask :level 500 :qty 400}))
        oid (:oid (first (bk/level-orders (get-in ex [:books 3]) bk/ask 500)))
        after (st/apply-tx ex {:tx :cancel :account 71 :market 3 :oid oid})]
    (is (= 0 (get-in after [:clearing :committed 71 77])))
    (is (= 1000 (cl/balance (:clearing after) 71 77)))))

(deftest spot-balances-are-in-the-root
  (let [a (spot-fixture)
        b (assoc-in a [:clearing :balances 70 77] 5)]
    (is (not= (st/state-root a) (st/state-root b))
        "a holding sat outside the root")))

(deftest every-refusal-reason-has-its-own-code
  ;; `encode-rejections` folds an unknown reason to 0, so two different
  ;; refusals commit to the same bytes — and the root is supposed to make what
  ;; was refused as agreed as what was executed.
  (let [missing (remove #(contains? st/reason-codes %) api/reasons)]
    (is (empty? missing) (str "reasons with no code: " (vec missing))))
  (is (= (count (set (vals st/reason-codes))) (count st/reason-codes))
      "two reasons share a code, so they are indistinguishable in the root"))
(deftest a-spot-market-listed-through-json-is-still-spot
  ;; JSON has no keywords: `:kind :spot` goes out and "spot" comes back.
  ;; Comparing only to the keyword would list a spot market and run it as a
  ;; perp — balances that never move and positions nobody asked for.
  (let [ex (st/new-exchange {:markets [mkt (assoc spot-mkt :kind "spot")]
                             :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})]
    (is (st/spot? ex 3))))

;; ── reserve attestation ─────────────────────────────────────────────────────
;;
;; The chain can compute what it OWES. Whether the money exists is somewhere
;; else, so the most it can do is record the claim and let anybody compare.

(deftest only-the-bridge-may-attest
  (let [ex (assoc (fresh) :bridge-authority 77)]
    (is (= 500 (:amount (:reserve-attestation
                         (st/apply-tx ex {:tx :attest-reserves :account 77 :amount 500})))))
    (is (nil? (:reserve-attestation
               (st/apply-tx ex {:tx :attest-reserves :account 78 :amount 999})))
        "a stranger made the venue look solvent")
    (is (nil? (:reserve-attestation
               (st/apply-tx (dissoc ex :bridge-authority)
                            {:tx :attest-reserves :account 77 :amount 999})))
        "a chain with no escrow attested about one")))

(deftest an-attestation-carries-its-age
  (let [ex (-> (fresh) (assoc :bridge-authority 77 :height 4200)
               (st/apply-tx {:tx :attest-reserves :account 77 :amount 500}))]
    (is (= 4200 (:at (:reserve-attestation ex)))
        "an attestation with no age is a claim that never expires")))

(deftest the-shortfall-is-liabilities-less-attested-assets
  (let [ex (-> (fresh)
               (assoc :bridge-authority 77)
               (funded [80] 1000)
               (st/apply-tx {:tx :attest-reserves :account 77 :amount 600}))
        leaves (st/canonical-leaves ex)]
    (is (= 1000 (cm/reserves leaves)))
    (is (= 400 (cm/shortfall leaves (:amount (:reserve-attestation ex))))
        "the uncovered part is not what a reader would compute")
    (is (neg? (cm/shortfall leaves 5000)) "over-collateralised must be visible too")))

(deftest no-attestation-is-not-a-shortfall-of-zero
  ;; Silence is not a claim.
  (is (nil? (cm/shortfall (st/canonical-leaves (fresh)) nil))))

(deftest the-attestation-is-in-the-root
  (let [a (assoc (fresh) :bridge-authority 77)
        b (st/apply-tx a {:tx :attest-reserves :account 77 :amount 500})]
    (is (not= (st/state-root a) (st/state-root b))
        "the venue could change its attested reserves without changing the root")))

;; ── chosen leverage ─────────────────────────────────────────────────────────
;;
;; A trader picks how much margin to hold, bounded by what the market allows.
;; Choosing is always a request for a STRICTER requirement — the market's
;; maximum is the most anybody may take.

(deftest choosing-lower-leverage-raises-the-margin-required
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [10] 100000000))
        plain (cl/initial-margin-rate-for (:clearing ex) 10 1 (get-in ex [:markets 1]) 1000)
        after (st/apply-tx ex {:tx :set-leverage :account 10 :market 1 :leverage 2})
        chosen (cl/initial-margin-rate-for (:clearing after) 10 1
                                           (get-in after [:markets 1]) 1000)]
    (is (> chosen plain) "choosing 2x on a 40x market did not raise the requirement")
    (is (= (fx/fdiv fx/rate-scale 2) chosen))))

(deftest leverage-above-the-market-maximum-is-refused
  ;; Clamping would size a position against a number the trader did not get.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [11] 100000000)
               (st/apply-tx {:tx :set-leverage :account 11 :market 1 :leverage 400}))]
    (is (nil? (cl/chosen-leverage (:clearing ex) 11 1))
        "a trader took more leverage than the market allows")))

(deftest leverage-cannot-be-changed-under-an-open-position
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [12 13] 1000000000)
               (st/apply-tx {:tx :order :account 13 :market 1
                             :side bk/ask :level 500 :qty 100})
               (st/apply-tx {:tx :order :account 12 :market 1
                             :side bk/bid :level 500 :qty 100})
               (st/apply-tx {:tx :set-leverage :account 12 :market 1 :leverage 2}))]
    (is (pos? (:size (cl/position (:clearing ex) 12 1))))
    (is (nil? (cl/chosen-leverage (:clearing ex) 12 1))
        "the margin requirement moved under an open position")))

(deftest a-chosen-leverage-never-loosens-a-tier
  ;; The market's tier is the ceiling; a choice only ever tightens.
  (let [tiered (assoc (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1
                                  :margin-tiers [{:max-notional 100 :max-leverage 2}]})
                      :taker-fee-rate 0 :maker-fee-rate 0)
        ex (-> (st/new-exchange {:market tiered :book-opts {:n-levels 256 :cap 1024 :ev-cap 1024}})
               (funded [14] 100000000)
               (st/apply-tx {:tx :set-leverage :account 14 :market 1 :leverage 40}))
        ;; notional 50 falls in the 2x tier; asking for 40x must not escape it
        r (cl/initial-margin-rate-for (:clearing ex) 14 1 (get-in ex [:markets 1]) 50)]
    (is (= (fx/fdiv fx/rate-scale 2) r)
        "a chosen leverage escaped the market's own tier")))

(deftest the-chosen-leverage-is-in-the-root
  (let [a (-> (fresh) (funded [15] 1000000))
        b (st/apply-tx a {:tx :set-leverage :account 15 :market 1 :leverage 3})]
    (is (not= (st/state-root a) (st/state-root b))
        "what an account must hold sat outside the root")))

;; ── sub-accounts ────────────────────────────────────────────────────────────
;;
;; Margin is pooled across an account's cross positions, so two strategies in
;; one account are one strategy as far as liquidation is concerned. A
;; sub-account is a second margin pool under the same person.

(def ^:private owner-pk "OWNER-PUBKEY")

(defn- family-fixture []
  (-> (fresh)
      (st/apply-tx {:tx :oracle :market 1 :price 500})
      (funded [20] 1000000)
      (assoc-in [:account-keys 20] owner-pk)))

(deftest a-sub-account-is-signed-for-by-its-owner
  (let [ex (st/apply-tx (family-fixture) {:tx :create-sub-account :account 20 :sub 21})]
    (is (= 20 (cl/family (:clearing ex) 21)))
    (is (= owner-pk (get-in ex [:account-keys 21]))
        "the owner cannot sign for its own sub-account")))

(deftest an-account-that-already-means-something-cannot-be-adopted
  (let [ex (-> (family-fixture)
               (funded [22] 500)
               (st/apply-tx {:tx :create-sub-account :account 20 :sub 22}))]
    (is (nil? (get-in ex [:clearing :sub-of 22]))
        "an account with collateral was adopted"))
  (let [ex (-> (family-fixture)
               (assoc-in [:account-keys 23] "SOMEBODY-ELSE")
               (st/apply-tx {:tx :create-sub-account :account 20 :sub 23}))]
    (is (nil? (get-in ex [:clearing :sub-of 23]))
        "an account with a bound key was adopted")))

(deftest sub-accounts-do-not-nest
  (let [ex (-> (family-fixture)
               (st/apply-tx {:tx :create-sub-account :account 20 :sub 21})
               (st/apply-tx {:tx :create-sub-account :account 21 :sub 24}))]
    (is (nil? (get-in ex [:clearing :sub-of 24]))
        "a sub-account owned a sub-account, so `family` became a walk")))

(deftest collateral-moves-inside-the-family-and-not-outside
  (let [ex (-> (family-fixture)
               (st/apply-tx {:tx :create-sub-account :account 20 :sub 21})
               (st/apply-tx {:tx :transfer :account 20 :to 21 :amount 400000}))]
    (is (= 600000 (get-in ex [:clearing :accounts 20 :collateral])))
    (is (= 400000 (get-in ex [:clearing :accounts 21 :collateral])))
    (let [out (st/apply-tx ex {:tx :transfer :account 20 :to 99 :amount 100000})]
      (is (= 600000 (get-in out [:clearing :accounts 20 :collateral]))
          "collateral left the family without going through the bridge"))))

(deftest margin-is-not-pooled-across-sub-accounts
  ;; The whole point: a position in the sub is margined against the sub's own
  ;; collateral, not the owner's.
  (let [ex (-> (family-fixture)
               (st/apply-tx {:tx :create-sub-account :account 20 :sub 21})
               (st/apply-tx {:tx :transfer :account 20 :to 21 :amount 500000}))]
    (is (= 500000 (cl/free-collateral (:clearing ex) 21 (:marks ex) (:markets ex))))
    (is (= 500000 (cl/free-collateral (:clearing ex) 20 (:marks ex) (:markets ex)))
        "the two pools are not separate")))

(deftest a-transfer-cannot-move-margin-out-from-under-a-position
  (let [ex (-> (family-fixture)
               (funded [25] 1000000000)
               (st/apply-tx {:tx :create-sub-account :account 20 :sub 21})
               (st/apply-tx {:tx :order :account 25 :market 1
                             :side bk/ask :level 500 :qty 100})
               (st/apply-tx {:tx :order :account 20 :market 1
                             :side bk/bid :level 500 :qty 100}))
        before (get-in ex [:clearing :accounts 20 :collateral])
        after (st/apply-tx ex {:tx :transfer :account 20 :to 21 :amount before})]
    (is (= before (get-in after [:clearing :accounts 20 :collateral]))
        "the collateral backing an open position was moved away")))

(deftest the-family-is-in-the-root
  (let [a (family-fixture)
        b (st/apply-tx a {:tx :create-sub-account :account 20 :sub 21})]
    (is (not= (st/state-root a) (st/state-root b))
        "who may move an account's money sat outside the root")))

;; ── the escrow: deposits observed on another chain ──────────────────────────
;;
;; The escrow is THORChain. A user sends the asset to its vault with a memo
;; naming their torihiki account; what this chain decides is when that becomes
;; collateral here. `:deposit` — the bridge authority crediting an account — is
;; one key away from a mint and stays only because a chain with no bridge
;; cannot be funded at all. These are the tests for the other path.

(defn- validators
  "Bond `n` accounts to themselves so `stake-of` gives each of them weight —
  which is what `:deposit-attest` requires of an attestor."
  [ex accts]
  (reduce (fn [e a] (st/apply-tx e {:tx :bond :account a :validator a
                                    :amount 100000}))
          (funded ex accts 1000000)
          accts))

(deftest a-deposit-needs-a-quorum-and-credits-exactly-once
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (validators [201 202 203 204])
               (funded [300] 0))
        att (fn [e v] (st/apply-tx e {:tx :deposit-attest :account v
                                      :txid "THOR-ABC" :credit 300
                                      :amount 5000 :asset "ETH.ETH"}))
        one (att ex 201)
        two (att one 202)
        three (att two 203)
        four (att three 204)
        bal #(get-in % [:clearing :accounts 300 :collateral] 0)]
    (is (= 0 (bal one)) "one validator's word was enough to mint")
    (is (= 0 (bal two)) "two were enough — a minority can mint")
    (is (= 5000 (bal three)) "a quorum said so and nothing happened")
    (is (= 5000 (bal four)) "a late fourth attestation paid the deposit twice")
    (is (true? (get-in four [:inbound "THOR-ABC" :credited?])))))

(deftest a-stranger-cannot-attest-a-deposit
  ;; Weight is what makes a validator's word cost something. An account nobody
  ;; bonded is a stranger, and three strangers are three strangers.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (funded [301 401 402 403] 0))
        after (reduce (fn [e v] (st/apply-tx e {:tx :deposit-attest :account v
                                                :txid "THOR-XYZ" :credit 301
                                                :amount 9000 :asset "ETH.ETH"}))
                      ex [401 402 403])]
    (is (= 0 (get-in after [:clearing :accounts 301 :collateral] 0)))
    (is (nil? (get-in after [:inbound "THOR-XYZ"])))))

(deftest an-attestation-cannot-be-changed
  ;; Evidence that can be edited is not evidence. A validator that attested one
  ;; amount and then another for the same transaction is refused rather than
  ;; allowed to overwrite — otherwise the last validator to speak decides.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (validators [211 212 213])
               (funded [302] 0))
        a1 (st/apply-tx ex {:tx :deposit-attest :account 211 :txid "THOR-1"
                            :credit 302 :amount 1000 :asset "ETH.ETH"})
        bad (st/apply-tx a1 {:tx :deposit-attest :account 212 :txid "THOR-1"
                             :credit 302 :amount 999999 :asset "ETH.ETH"})
        a2 (st/apply-tx bad {:tx :deposit-attest :account 212 :txid "THOR-1"
                             :credit 302 :amount 1000 :asset "ETH.ETH"})
        a3 (st/apply-tx a2 {:tx :deposit-attest :account 213 :txid "THOR-1"
                            :credit 302 :amount 1000 :asset "ETH.ETH"})]
    (is (= 1000 (:amount (get-in bad [:inbound "THOR-1"])))
        "the second validator rewrote the first one's evidence")
    (is (= #{211} (:attests (get-in bad [:inbound "THOR-1"])))
        "a disagreeing attestation was counted toward the quorum")
    (is (= 1000 (get-in a3 [:clearing :accounts 302 :collateral] 0)))))

(deftest the-escrow-is-in-the-state-root
  ;; A credited deposit IS collateral. A replica that credited one its peers
  ;; did not would agree about every order and disagree about who can afford
  ;; them, and a root that did not cover this would call the two identical.
  (let [base (-> (fresh)
                 (st/apply-tx {:tx :oracle :market 1 :price 500})
                 (validators [221 222 223])
                 (funded [303] 0))
        att (fn [e v] (st/apply-tx e {:tx :deposit-attest :account v
                                      :txid "THOR-R" :credit 303
                                      :amount 700 :asset "ETH.ETH"}))
        one (att base 221)]
    (is (not= (st/state-root base) (st/state-root one))
        "an attestation nobody can see in the root is an attestation nobody
         can check")
    (is (not= (st/state-root one) (st/state-root (att (att one 222) 223))))))

(deftest the-exit-needs-a-quorum-too
  ;; `:withdraw-settle` is one key saying the money left, which is the same
  ;; shape as `:deposit` and has the same problem pointing the other way: the
  ;; key that can settle a real claim can settle one that was never paid.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (validators [231 232 233])
               (funded [304] 50000)
               (st/apply-tx {:tx :withdraw :account 304 :amount 20000}))
        claim (:withdraw-seq ex)
        att (fn [e v] (st/apply-tx e {:tx :withdraw-attest :account v
                                      :claim claim :txid "THOR-OUT-1"
                                      :dest "0xabc"}))
        one (att ex 231)
        three (att (att one 232) 233)]
    (is (some? (get-in ex [:withdrawals claim])) "the claim was never raised")
    (is (some? (get-in one [:withdrawals claim]))
        "one validator's word settled a claim")
    (is (nil? (get-in three [:withdrawals claim])))
    (is (= "THOR-OUT-1" (get-in three [:paid claim :txid]))
        "the payment left no record a reader could follow")
    (is (true? (get-in three [:paid claim :settled?])))))

(deftest an-exit-attestation-must-name-the-same-payment
  ;; A quorum agreeing that SOMETHING was paid is not evidence that this claim
  ;; was. The transaction and the destination are part of what has to match.
  (let [ex (-> (fresh)
               (st/apply-tx {:tx :oracle :market 1 :price 500})
               (validators [241 242 243])
               (funded [305] 50000)
               (st/apply-tx {:tx :withdraw :account 305 :amount 10000}))
        claim (:withdraw-seq ex)
        a1 (st/apply-tx ex {:tx :withdraw-attest :account 241 :claim claim
                            :txid "THOR-A" :dest "0xaaa"})
        other (st/apply-tx a1 {:tx :withdraw-attest :account 242 :claim claim
                               :txid "THOR-B" :dest "0xbbb"})
        a3 (st/apply-tx (st/apply-tx other {:tx :withdraw-attest :account 242
                                            :claim claim :txid "THOR-A"
                                            :dest "0xaaa"})
                        {:tx :withdraw-attest :account 243 :claim claim
                         :txid "THOR-A" :dest "0xaaa"})]
    (is (= #{241} (:attests (get-in other [:paid claim])))
        "a different payment was counted toward this claim's quorum")
    (is (nil? (get-in a3 [:withdrawals claim])))
    (is (= "0xaaa" (get-in a3 [:paid claim :dest])))))
