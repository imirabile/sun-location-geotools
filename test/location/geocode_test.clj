(ns location.geocode-test
  (:require [clojure.test         :refer :all]
            [location.utils.test  :as t]
            [location.geocode     :as geo]))

(deftest split-geocode-test
  (testing "Should return an empty seq given an invalid geocode"
           (let [geo (geo/split-geocode "4000,-74.98")]
             (is (empty? geo))))
  
  (testing "Should return a geocode pair given a valid geocode"
           (let [pair (geo/split-geocode "40.73,-74.98")]
             (t/equal? "40.73" (first pair))
             (t/equal? "-74.98" (second pair))))
  
  (testing "Should return an empty seq given a geocode triple"
           (let [geo (geo/split-geocode "40.73,40.73,40.73")]
             (is (empty? geo)))))

(deftest sanitize-geocode-test
  (testing "Should return a valid geocode pair given 40.73,90.73"
           (let [pair (geo/split-geocode "40.73,90.73")]
             (t/equal? "40.73" (first pair))
             (t/equal? "90.73" (second pair)))))

(deftest reverse-geocode-test
  (testing "Should return 90.00,0.00 when given 0.00,90.00"
           (let [pair (geo/split-geocode (geo/reverse-geocode "0.00,90.00"))]
             (t/equal? "90.00" (first pair))
             (t/equal? "0.00"  (second pair)))))

(deftest geocode-to-map-test
  (testing "Should return a map with lat and lon given a 33.89,-84.46."
           (let [actual (geo/geocode-to-map "33.89,-84.46")
                 expected {:latitude "33.89" :longitude "-84.46"}]
             (t/equal? (:latitude actual) (:latitude expected))
             (t/equal? (:longitude actual) (:longitude expected)))))
