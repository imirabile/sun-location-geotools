(ns location.geoboundary-test
  (:require [clojure.test         :refer :all]
            [location.geoboundary :as gb]
            [location.utils.test  :as t]))

(deftest flatten-polygon-test
  (testing "Should return the parameter if it is a multidimensional vector with more than one item"
    (let [actual (gb/flatten-polygon [["test"] ["hello"]])]
      (t/equal? actual [["test"] ["hello"]])
      (t/equal? (count actual) 2)))

  (testing "Should strip the outer vector if there is only one vector within it."
    (let [actual (gb/flatten-polygon [["test"]])]
      (t/equal? actual ["test"]))))

(deftest get-boundaries-test
  (testing "Should return a map with keys product and coordinates given pollen product and geocode"
    (let [actual (gb/get-boundaries "33.89,-84.46" "pollen")]
      (t/equal? (get-in (first (:features actual)) [:properties :product]) "pollen")
      (is (seq? (get-in (first (:features actual)) [:geometry :coordinates])))))

  (testing "Should return a seq with 2 maps of features."
    (let [actual (gb/get-boundaries "33.89,-84.46" "alerts")]
      (is (map? actual))
      (t/equal? 2 (count (:features actual))))))
