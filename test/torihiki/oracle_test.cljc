(ns torihiki.oracle-test
  "The mark is anchored to the oracle so a thin book cannot liquidate people.
  That defence is only as good as the oracle."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.clearing :as cl]
            [torihiki.oracle :as orc]
            [torihiki.api :as api]
            [torihiki.state :as st]
            [torihiki.auth :as auth]))

(def params orc/default-params)
(def pubs #{101 102 103 104 105})

(defn- subs* [& pairs] (into {} (map (fn [[p v t]] [p {:price v :ts (or t 0)}]) pairs)))

;; ── the pure aggregate ──────────────────────────────────────────────────────

(deftest a-minority-cannot-move-the-median
  (testing "the property a mean does not have"
    (let [honest (subs* [101 1000] [102 1000] [103 1000])
          attacked (assoc honest 104 {:price 999999 :ts 0}
                                 105 {:price 1 :ts 0})]
      (is (= 1000 (:price (orc/aggregate honest pubs 0 params))))
      (is (= 1000 (:price (orc/aggregate attacked pubs 0 params)))
          "two extreme liars out of five moved it by nothing"))))

(deftest one-publisher-cannot-set-the-price
  (let [r (orc/aggregate (subs* [101 50000]) pubs 0 params)]
    (is (:stale? r))
    (is (nil? (:price r)) "below quorum there is no price, not a bad one")))

(deftest below-quorum-there-is-no-price
  (is (:stale? (orc/aggregate (subs* [101 100] [102 100]) pubs 0 params)))
  (is (not (:stale? (orc/aggregate (subs* [101 100] [102 100] [103 100])
                                   pubs 0 params)))))

(deftest stale-submissions-do-not-count
  ;; Ages are derived from the window rather than written as literals. The
  ;; window is in `:ts` units, and `:ts` advances by the consensus layer's
  ;; block-interval (100) per block — so a literal here silently means a
  ;; different number of blocks whenever that window is corrected, which is
  ;; exactly what happened when `:max-age` was 60 (0.6 blocks) and this test
  ;; still passed.
  (let [age (:max-age params)
        now (* 10 age)
        s (subs* [101 100 (- now 1)] [102 100 (- now 10)]
                 [103 100 (- now age 1)])]   ; 103 is one tick past the window
    (is (= 2 (:n (orc/aggregate s pubs now params))))
    (is (:stale? (orc/aggregate s pubs now params))
        "a quorum of fresh submissions, not of any submissions")))

(deftest unauthorised-submissions-do-not-count
  (let [s (subs* [101 100] [102 100] [999 100])]
    (is (= 2 (:n (orc/aggregate s pubs 0 params))))
    (is (:stale? (orc/aggregate s pubs 0 params)))))

(deftest the-even-case-is-stated-and-total
  (testing "lower middle, and equal prices break ties by publisher"
    (is (= 200 (orc/median (orc/fresh (subs* [101 100] [102 200] [103 300] [104 400])
                                      pubs 0 60)))
        "of the two middles (200 and 300) the LOWER is taken")
    (is (= 100 (orc/median (orc/fresh (subs* [101 100] [102 300]) pubs 0 60)))
        "and with two values that is the first"))
  (let [f (orc/fresh (subs* [104 100] [101 100] [103 200]) pubs 0 60)]
    (is (= [[101 100] [104 100] [103 200]] f)
        "sorted by price then publisher, so the position read is deterministic")))

;; ── wired into the engine ───────────────────────────────────────────────────

(def mkt (cl/market {:id 1 :max-leverage 40 :tick 1 :lot 1}))

(defn- ex-with-publishers []
  (st/new-exchange {:market mkt
                    :oracle-publishers pubs
                    :book-opts {:n-levels 4096 :cap 8192 :ev-cap 8192}}))

(deftest the-direct-setter-is-closed-when-publishers-exist
  (let [ex (ex-with-publishers)]
    (is (= :oracle-is-aggregated
           (api/validate ex {:tx :oracle :market 1 :price 1000}))
        "otherwise an attacker just uses the door that does not aggregate")
    (is (nil? (api/validate ex {:tx :oracle-submit :account 101 :market 1 :price 1000})))))

(deftest the-direct-setter-stays-open-without-publishers
  (let [ex (st/new-exchange {:market mkt :book-opts {:n-levels 1024 :cap 512 :ev-cap 512}})]
    (is (nil? (api/validate ex {:tx :oracle :market 1 :price 1000})))))

(deftest a-non-publisher-is-refused
  (is (= :not-a-publisher
         (api/validate (ex-with-publishers)
                       {:tx :oracle-submit :account 7 :market 1 :price 1000}))))

(deftest quorum-publishes-and-a-single-submission-does-not
  (let [ex (ex-with-publishers)
        one (st/apply-block ex {:height 1 :ts 10
                                :txs [{:tx :oracle-submit :account 101 :market 1 :price 1000}]})]
    (is (true? (get-in one [:oracle-stale 1])))
    (is (= 0 (get-in one [:oracle 1] 0)) "nothing was published")
    (let [three (st/apply-block one
                                {:height 2 :ts 11
                                 :txs [{:tx :oracle-submit :account 102 :market 1 :price 1002}
                                       {:tx :oracle-submit :account 103 :market 1 :price 998}]})]
      (is (false? (get-in three [:oracle-stale 1])))
      (is (= 1000 (get-in three [:oracle 1])) "the median of 998, 1000, 1002"))))

(deftest a-lying-publisher-does-not-move-the-published-price
  (let [ex (st/apply-block (ex-with-publishers)
                           {:height 1 :ts 10
                            :txs [{:tx :oracle-submit :account 101 :market 1 :price 1000}
                                  {:tx :oracle-submit :account 102 :market 1 :price 1000}
                                  {:tx :oracle-submit :account 103 :market 1 :price 1000}]})
        before (get-in ex [:oracle 1])
        after (st/apply-block ex {:height 2 :ts 11
                                  :txs [{:tx :oracle-submit :account 104 :market 1
                                         :price 9999999}]})]
    (is (= 1000 before))
    (is (= 1000 (get-in after [:oracle 1]))
        "a fourth, extreme submission cannot move the median")))

(deftest the-oracle-goes-stale-when-submissions-age-out
  (let [ex (st/apply-block (ex-with-publishers)
                           {:height 1 :ts 10
                            :txs [{:tx :oracle-submit :account 101 :market 1 :price 1000}
                                  {:tx :oracle-submit :account 102 :market 1 :price 1000}
                                  {:tx :oracle-submit :account 103 :market 1 :price 1000}]})]
    (is (false? (get-in ex [:oracle-stale 1])))
    ;; much later, one lone submission — the others are now stale
    (let [later (st/apply-block ex {:height 2 :ts 5000
                                    :txs [{:tx :oracle-submit :account 101 :market 1
                                           :price 1000}]})]
      (is (true? (get-in later [:oracle-stale 1])))
      (is (= 1000 (get-in later [:oracle 1]))
          "the last known price is kept, but flagged rather than trusted"))))

(deftest a-stale-oracle-stops-liquidation
  (testing "closing a position at a price nobody vouches for is irreversible"
    (let [ex (-> (ex-with-publishers)
                 (st/apply-block {:height 1 :ts 10
                                  :txs [{:tx :deposit :account 1 :amount 1000}
                                        {:tx :oracle-submit :account 101 :market 1 :price 1000}
                                        {:tx :oracle-submit :account 102 :market 1 :price 1000}
                                        {:tx :oracle-submit :account 103 :market 1 :price 1000}]}))
          ;; let the feed go stale
          stale (st/apply-block ex {:height 2 :ts 9999
                                    :txs [{:tx :oracle-submit :account 101 :market 1 :price 1000}]})]
      (is (true? (get-in stale [:oracle-stale 1])))
      ;; the liquidate transaction must be a no-op rather than acting on a
      ;; price the system cannot vouch for
      (is (= (:clearing stale)
             (:clearing (st/apply-tx stale {:tx :liquidate :market 1})))))))

(deftest the-root-commits-to-submissions
  (testing "a sequencer must not be able to add or drop one invisibly"
    (let [a (st/apply-block (ex-with-publishers)
                            {:height 1 :ts 10
                             :txs [{:tx :oracle-submit :account 101 :market 1 :price 1000}]})
          b (st/apply-block (ex-with-publishers)
                            {:height 1 :ts 10
                             :txs [{:tx :oracle-submit :account 102 :market 1 :price 1000}]})]
      (is (not= (st/state-root a) (st/state-root b))
          "same price, different publisher, different root"))))

;; ── stake weighting ─────────────────────────────────────────────────────────
;;
;; A plain median counts publishers. Publishers are cheap to create, so
;; counting them means a majority of small voices moves the price. Weighting by
;; stake makes the cost of moving it the cost of acquiring the stake.

(deftest stake-decides-whose-price-wins
  (let [subs {1 {:price 100 :ts 0} 2 {:price 100 :ts 0} 3 {:price 500 :ts 0}}
        pubs #{1 2 3}
        params {:quorum 2 :max-age 10}]
    (is (= 100 (:price (orc/aggregate subs pubs 0 params)))
        "unweighted, the two agreeing publishers win")
    ;; publisher 3 has more at risk than 1 and 2 together
    (let [stake {1 1 2 1 3 10}]
      (is (= 500 (:price (orc/aggregate subs pubs 0 params #(get stake % 0))))
          "stake did not move the aggregate"))))

(deftest an-unbonded-publisher-weighs-nothing
  (let [subs {1 {:price 100 :ts 0} 2 {:price 900 :ts 0} 3 {:price 900 :ts 0}}
        pubs #{1 2 3}
        params {:quorum 2 :max-age 10}
        stake {1 10 2 0 3 0}]
    (is (= 100 (:price (orc/aggregate subs pubs 0 params #(get stake % 0))))
        "two publishers with nothing at risk outvoted the one that had")))

(deftest with-no-stake-at-all-it-is-the-plain-median
  ;; A chain whose publishers are unbonded has nothing to weigh. Falling back
  ;; is better than stopping: the alternative is an outage caused by a
  ;; governance gap.
  (let [subs {1 {:price 100 :ts 0} 2 {:price 200 :ts 0} 3 {:price 300 :ts 0}}
        pubs #{1 2 3}
        params {:quorum 2 :max-age 10}]
    (is (= (:price (orc/aggregate subs pubs 0 params))
           (:price (orc/aggregate subs pubs 0 params (constantly 0)))))))

(deftest weighting-still-refuses-below-quorum
  (let [subs {1 {:price 100 :ts 0}}
        pubs #{1 2 3}
        params {:quorum 2 :max-age 10}]
    (is (:stale? (orc/aggregate subs pubs 0 params #(get {1 1000} % 0)))
        "one heavily staked publisher was allowed to set the price alone")))

;; ── one publisher, many markets, one nonce ──────────────────────────────────

(deftest a-batch-spends-one-nonce-and-obeys-the-same-rule
  (testing "Nonces are strictly sequential per account, so a publisher pricing
            N markets one transaction at a time must emit N per block in the
            right order, and one gap wedges every later one. The batch is a
            way to spend ONE nonce — not a second set of rules."
    (let [spec {:tick 1 :lot 1 :initial-margin-rate 25000000
                :maintenance-margin-rate 12500000}
          ex (-> (st/new-exchange {:market (assoc spec :id 1)})
                 (assoc :oracle-publishers #{7} :ts 0)
                 (assoc-in [:markets 2] (assoc spec :id 2)))
          batch {:tx :oracle-submit-batch :account 7 :prices {2 200 1 100}}]
      (is (nil? (api/validate ex batch)) "a well-formed batch was refused")
      (let [ex' (st/apply-tx ex batch)]
        (is (= 100 (get-in ex' [:oracle-submissions 1 7 :price])))
        (is (= 200 (get-in ex' [:oracle-submissions 2 7 :price]))
            "the batch recorded one market and dropped the other"))
      (testing "and every entry is checked by the single-submission rule"
        (is (some? (api/validate ex (assoc batch :prices {999 100})))
            "priced a market that does not exist")
        (is (some? (api/validate ex (assoc batch :prices {})))
            "an empty batch is a transaction that does nothing")
        (is (some? (api/validate ex (assoc batch :prices
                                           (zipmap (range 1000) (repeat 100)))))
            "an unbounded batch is unbounded work bought with one signature")))))

(deftest a-batch-is-signed-over-a-canonical-string
  (testing "A map has no order. Two nodes that serialised it in the order they
            happened to hold it would compute different payloads for the same
            transaction, and each would call the other's signature invalid."
    (is (= "1=6253900,2=186591766"
           (auth/canonical-prices {2 186591766 1 6253900})
           (auth/canonical-prices (into (sorted-map) {1 6253900 2 186591766}))))
    (is (nil? (auth/canonical-prices nil)))
    (is (nil? (auth/canonical-prices {})))
    (is (not= (auth/signing-payload "c" 7 1 {:tx :oracle-submit-batch
                                             :prices {1 100}})
              (auth/signing-payload "c" 7 1 {:tx :oracle-submit-batch
                                             :prices {1 101}}))
        "the prices are not covered by the signature")))

(deftest the-freshness-window-is-changeable-and-bounded
  (testing "It was genesis data and nothing else, so a wrong value could only
            be corrected by destroying the chain. :max-age WAS wrong — 60,
            against a :ts that advances 100 per block — and the deployed venue
            could not be fixed without being rebuilt. A constant that can only
            be right at genesis is one that will be wrong in production."
    (let [ex (-> (st/new-exchange {:market {:id 1 :tick 1 :lot 1
                                            :initial-margin-rate 25000000
                                            :maintenance-margin-rate 12500000}})
                 (assoc :bridge-authority 5 :oracle-publishers #{7 8 9}))
          set-tx (fn [q a] {:tx :set-oracle-params :account 5 :quorum q :max-age a})]
      (is (nil? (api/validate ex (set-tx 3 3000))))
      (is (= 3000 (:max-age (:oracle-params (st/apply-tx ex (set-tx 3 3000))))))

      (testing "and it is a dial with bounds, not a free parameter"
        (is (some? (api/validate ex (set-tx 1 3000)))
            "a quorum of one lets a single publisher move every market")
        (is (some? (api/validate ex (set-tx 3 (inc orc/max-age-ceiling))))
            "a window past the ceiling makes stale? stop meaning anything —
             liquidation is gated on it")
        (is (some? (api/validate ex (set-tx 3 0))) "a zero window"))

      (testing "and only the bridge may turn it"
        (is (= (:oracle-params ex)
               (:oracle-params (st/apply-tx ex (assoc (set-tx 3 3000)
                                                      :account 99))))
            "a stranger changed how old a price may be"))

      (testing "quorum cannot exceed the publishers who could meet it"
        (is (= (:oracle-params ex)
               (:oracle-params (st/apply-tx ex (set-tx 9 3000))))
            "a quorum no set of publishers can reach freezes every price")))))

(deftest a-batch-survives-the-json-round-trip
  (testing "`clj->js` has no integer keys to give, so a batch that goes out
            through JSON comes back keyed by strings. Measured: a batch signed
            with integer keys, submitted, and silently never applied — no
            refusal counter moved, because `1` and \"1\" are not the same
            market to anything downstream, and the transaction simply did
            nothing."
    (let [spec {:tick 1 :lot 1 :initial-margin-rate 25000000
                :maintenance-margin-rate 12500000}
          ex (-> (st/new-exchange {:market (assoc spec :id 1)})
                 (assoc :oracle-publishers #{7} :ts 0)
                 (assoc-in [:markets 2] (assoc spec :id 2)))
          ints {:tx :oracle-submit-batch :account 7 :prices {1 100 2 200}}
          strs {:tx :oracle-submit-batch :account 7 :prices {"1" 100 "2" 200}}]
      (testing "the canonical string does not depend on which side is looking"
        (is (= (auth/canonical-prices (:prices ints))
               (auth/canonical-prices (:prices strs))
               "1=100,2=200")))
      (testing "and neither does validation or application"
        (is (= (api/validate ex ints) (api/validate ex strs) nil))
        (is (= 100 (get-in (st/apply-tx ex strs) [:oracle-submissions 1 7 :price])))
        (is (= 200 (get-in (st/apply-tx ex strs) [:oracle-submissions 2 7 :price]))))
      (testing "and the numeric order is numeric, not lexicographic"
        (is (= "2=1,10=2" (auth/canonical-prices {"10" 2 "2" 1}))
            "sorted as strings, market 10 would come before market 2 and two
             nodes holding the same batch would sign different payloads")))))
