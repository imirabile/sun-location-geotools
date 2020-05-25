(ns location.lookup-test
  (:require [clojure.test        :refer :all]
            [location.utils.test :as t]
            [location.lookup     :as l]))

(def ^:private type-fields #'location.lookup/type-fields)
(def ^:private expand-alias #'location.lookup/expand-alias)
(def ^:private process-types #'location.lookup/process-types)

(deftest type-fields-test
  (testing "Should return a given a is allowed"
    (let [actual (type-fields #{"input"} #{"input"})
          expected #{"input"}]
      (t/equal? expected actual)))

  (testing "Should return a given a and b are allowed"
    (let [actual (type-fields #{"a"} #{"a" "b"})
          expected #{"a"}]
      (t/equal? expected actual)))

  (testing "Should return an empty set given b and c are allowed"
   (let [actual (type-fields #{"a"} #{"b" "c"})
         expected #{}]
     (t/equal? expected actual))))

(deftest expand-alias-test
  (testing "Should expand test to test-alias"
    (let [actual (expand-alias "test")
          expected ["test-alias"]]
      (t/equal? expected actual)))

  (testing "Should expand not-test to not-alias"
    (let [actual (expand-alias "not-test")
          expected "not-test"]
      (t/equal? expected actual))))

(deftest process-types-test
  (testing "Should return pollen, timeZones given those params and pollen_polygon alias"
    (let [actual (process-types ["pollen_polygons" "timeZones"])
          expected #{"pollen" "timeZones"}]
      (t/equal? expected actual)))

  (testing "Should return adminDistrict3, timeZones given those params and no aliases"
    (let [actual (process-types ["adminDistrict3" "timeZones"])
          expected #{"adminDistrict3" "timeZones"}]
      (t/equal? expected actual))))

(deftest ^:integration geocoder
  (testing "Should return a location record given a request containing all fields"
    (let [actual (l/geocoder "33.89,-84.46" {:language "en-US" :format "json"})]
      (t/not-nil? (:location actual)))))

(deftest add-conditional-point-data-test
  (testing "Should return a map with an airportName key given iataCode as a type and accurate coordinates"
    (let [actual (l/add-conditional-point-data "iataCode" "33.63,-84.42")]
      (t/equal? actual {:airportName "Hartsfield-Jackson Intl"})))

  (testing "Should return empty map when given valid coordinates and geocode as the parameter"
    (let [actual (l/add-conditional-point-data "geocode" "33.63,-84.42")]
      (t/equal? {} actual)))

  (testing "Should return a map with an airportName key with a value of nil given iataCode as a type and invalid coordinates"
    (let [actual (l/add-conditional-point-data "iataCode" "90,90")]
      (is (nil? (:airportName actual))))))

(deftest get-conditional-field-values-test
  (testing "Should return a map with an airportName key given iataCode as a type and accurate coordinates, an empty object and airportName as the field name"
    (let [actual (l/get-conditional-field-data "iataCode" "33.63,-84.42" {} "airportName")]
      (t/equal? actual {:airportName "Hartsfield-Jackson Intl"})))

  (testing "Should return an empty map with iataCode as a type and accurate coordinates, an empty object and nil as the field name"
    (let [actual (l/get-conditional-field-data "iataCode" "33.63,-84.42" {} nil)]
      (is (empty? actual))))

  (testing "Should return an empty map with nil as a type and accurate coordinates, an empty object and airportName as the field name"
    (let [actual (l/get-conditional-field-data nil "33.63,-84.42" {} "airportName")]
      (is (empty? actual)))))
