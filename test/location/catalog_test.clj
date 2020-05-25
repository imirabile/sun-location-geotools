(ns location.catalog-test
  (:require [clojure.test         :refer :all]
            [location.utils.test  :as t]
            [location.catalog     :as catalog]
            [location.config      :as cfg]))

(deftest get-item-test
  (testing "Should return the catalog of products when :product keyword is passed in"
    (is (comp not empty? (catalog/get-item :product))))

  (testing "Should return nil when :invalid keyword is passed in"
    (is (nil? (catalog/get-item :invalid)))))

(deftest get-catalog-test
  (testing "Should return a map containg products as the key when given product for the item parameter"
    (let [actual (catalog/get-catalog "product")]
      (t/equal? [:items] (keys actual))
      (is (= (count (:items actual)) 1))))

  (testing "Should return a map containg products and types as the keys when given no parameters"
    (let [actual (catalog/get-catalog "product;type")]
      (is (= (count (:items actual)) 2))))

  (testing "Should return empty values when given invalid parameters"
    (let [actual (catalog/get-catalog "invalid")]
      (is  (empty? (:values actual)))))

  (testing "Should return a map containg multiple results when given no parameters"
    (let [actual (catalog/get-catalog nil)]
      (is (> (count (:items actual)) 1)))))

