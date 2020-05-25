(ns location.point-map-test
  (:require [clojure.test          :refer :all]
            [location.utils.test   :as t]
            [clojure.tools.logging :as log]
            [location.point-map    :as pm]))

(def ^:private parse-records #'location.point-map/parse-records)
(def ^:private get-identity #'location.point-map/get-identity)
(def ^:private format-response #'location.point-map/format-response)
(def ^:private test-mappings (atom {}))

(deftest parse-records-test
  (testing "Should return {a b} given a||b"
    (let [actual (parse-records "a||b")
          expected {"a" "b"}]
      (t/equal? expected actual))))

(deftest get-identity-test
  (testing "Should return 33.89,-84.46 given geocode type and 33.89,-84.46"
    (let [actual (get-identity  "geocode" "33.89,-84.46")
          expected "33.89,-84.46"]
      (t/equal? expected actual)))

  (testing "Should return nil given icaoCode type and KATL"
    (let [actual (get-identity  "icaoCode" "KATL")
          expected nil]
      (t/equal? expected actual))))

(deftest format-response-test
  (testing "Should return a formatted map of values given icaoCode, katl, and 33.89,-84.46"
    (let [actual (format-response "icaoCode" "KATL" "33.89,-84.46")
          expected {:type "icaoCode" :id "KATL" :geocode "33.89,-84.46"}]
      (t/equal? expected actual))))

(deftest get-point-test
  (testing "Should return {:type \"icaoCode\" :id \"KATL:US\" :geocode \"33.6366996,-84.427864\""
    (let [actual (pm/get-point "icaoCode" "KATL")
          expected {:type "icaoCode" :id "KATL" :geocode "33.6366996,-84.427864"}]
      (t/equal? expected actual)))
  
  (testing "Should return a 404 error document given invalid id"
    (let [actual (pm/get-point "icaoCode" "NOT_REAL")
          expected nil]
      (t/equal? expected actual))))

(deftest get-postal-place-test
  (testing "Should return \"West New York\" for key \"07093:en:US\""
    (let [actual (pm/get-postal-place (str "07093" ":" "en" ":" "US"))
          expected "West New York"]
      (t/equal? expected actual)))
  
  (testing "Should return \"Atlanta\" for key \"30339||en||US|\""
    (let [actual (pm/get-postal-place (str "30339" ":" "en" ":" "US"))
          expected "Atlanta"]
      (t/equal? expected actual)))

  (testing "Should return nil for invalid key \"99999||en||US|\""
    (let [actual (pm/get-postal-place (str "99999" ":" "en" ":" "US"))
          expected nil]
      (t/equal? expected actual)))
 
  (testing "Should return a 404 error document when trying to get a point from a postalPlace"
    (let [actual (pm/get-point "postalPlace" (str "07093" ":" "en" ":" "US"))
          expected nil]
      (t/equal? expected actual)))

  (testing "Should return a 200 when a [CA]nada postal place is queried with full and partial"
    (pm/pre-load-mappings)
    (let [expected #{"Qu�bec", "Québec"}
          place1 (pm/get-postal-place "G1R 4P5:fr:CA")
          place2 (pm/get-postal-place "G1R:fr:CA")]
      (is (contains? expected place1))
      (is (contains? expected place2)))
    (let [k "V6B 2B7:fr:CA"
          n "Downtown Vancouver"]
      (swap! pm/mappings assoc :postalPlace {k n})
      (let [place3 (pm/get-postal-place k)]
        (t/equal? place3 n)))))
