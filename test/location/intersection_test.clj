(ns location.intersection-test
  (:require [clojure.test         :refer :all]
            [location.intersection :as inter]
            [location.utils.test  :as t]))

(def format-intersections #'location.intersection/format-intersections)

(deftest format-intersections-test
  (testing "Should return a seq of maps given a seq of vectors, a valid overlay and valid product, and valid geocode"
    ;; test-data simulates a response of product coordinates filtered against an overlay filter
    (let [ test-data (seq [ ["12345" "6789"]["9876" "54321"] ])
          actual (format-intersections test-data "postalKey" "33.89,-84.46" "alerts")]
      (t/equal? (count actual) 2)
      (t/equal? (count (set (flatten (map keys actual)))) 4))))

(deftest get-intersections-test
  (testing "Should return a sequence of vectors when given valid geocode, product, and overlay"
    (let [actual (inter/get-intersections "33.89,-84.46" "postalKey" "pollen")
          actual-alerts (inter/get-intersections "33.89,-84.46" "postalKey" "alerts")]
      (t/equal? (count actual) 1)
      (t/equal? (count actual-alerts) 2)))

  (testing "Should return a sequence of vectors when given valid geocode, alerts as product, and overlay"
    (let [actual-alerts (inter/get-intersections "33.89,-84.46" "alerts" "pollen")]
      (t/equal? (count actual-alerts) 2)))

  (testing "Should return nil when given valid geocode, product, and invalid overlay"
    (let [actual (inter/get-intersections "33.89,-84.46" "postalKey" "invalid")]
      (t/equal? actual nil)))

  (testing "Should return four maps when given valid geocode, and alerts (expanded) values for both product and overlay"
    (let [actual (inter/get-intersections "33.89,-84.46" "alerts" "alerts")]
      (t/equal? (count actual) 4)))

  (testing "Should return nil when given valid geocode, alerts as product, and invalid overlay"
    (let [actual (inter/get-intersections "33.89,-84.46" "alerts" "invalid")]
      (t/equal? actual nil)))

  (testing "Should return nil when given valid geocode, invalid product, and valid overlay"
    (let [actual (inter/get-intersections "33.89,-84.46" "invalid" "pollen")
          actual-alerts (inter/get-intersections "33.89,-84.46" "invalid" "alerts")]
      (t/equal? actual nil)
      (t/equal? actual-alerts nil)))

  (testing "Should return nil when given invalid geocode, valid product, and valid overlay"
    (let [actual (inter/get-intersections "90.00,90.00" "postalKey" "pollen")
          actual-alerts (inter/get-intersections "90.00,90.00" "postalKey" "alerts")]
      (t/equal? actual nil)
      (t/equal? actual-alerts nil))))
