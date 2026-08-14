(ns torihiki.oracle-test
  "The mark is anchored to the oracle so a thin book cannot liquidate people.
  That defence is only as good as the oracle."
  (:require [clojure.test :refer [deftest is testing]]
            [torihiki.clearing :as cl]
            [torihiki.oracle :as orc]
            [torihiki.api :as api]
            [torihiki.state :as st]))

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
