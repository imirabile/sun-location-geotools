(ns location.geohittest-test
  (:require [clojure.test        :refer :all]
            [location.utils.test :as t]
            [location.geohittest :as ght]))

(def get-identity #'ght/get-identity)
(def get-key #'ght/get-key)

(deftest get-identity-test
  (testing "Should return geocode given request for location."
    (let [actual (get-identity "location" "33.89,-84.46")
          expected "33.89,-84.46"]
      (t/equal? expected actual)))

  (testing "Should return nil given request for alert."
    (let [actual (get-identity "alerts" "33.89,-84.46")
          expected nil]
      (t/equal? expected actual))))

(deftest get-key-test
  (testing "Should return key (\"GAZ032\") given zone product and 33.89,-84.46"
    (let [actual (get-key {:product "zone" :data {:zoneId "GAZ032" :notId "test"}})
          expected "GAZ032"]
      (t/equal? expected actual))))

(deftest get-keys-test
  (testing "Should return keys (\"GAC067\" \"GAZ032\") given alert product and 33.89,-84.46"
    (let [actual (:keys (ght/get-keys "33.89,-84.46" "alerts" "en-US"))
          expected (seq ["GAC067" "GAZ032"])]
      (t/equal? expected actual)))
  
  (testing "Should return keys 33.89,-84.46 given loc product and 33.89,-84.46"
    (let [actual (:key (ght/get-keys "33.89,-84.46" "location" "en-US"))
          expected "33.89,-84.46"]
      (t/equal? expected actual))))
