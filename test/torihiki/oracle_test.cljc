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
  (let [now 1000
        s (subs* [101 100 999] [102 100 990] [103 100 100])]   ; 103 is 900s old
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
