(ns torihiki.state-test
  "The tests that matter for a chain: two replays of the same block must reach
  the same state root, and any difference in the block must change it."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.fixed :as fx]
            [torihiki.book :as bk]
            [torihiki.clearing :as cl]
            [torihiki.liquidation :as liq]
            [torihiki.api :as api]
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
