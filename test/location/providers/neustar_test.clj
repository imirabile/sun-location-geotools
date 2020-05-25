(ns location.providers.neustar-test
  (:require [clojure.test               :refer :all]
            [clojure.string             :as str]
            [location.utils.test        :as t]
            [location.providers.neustar :as ip]))

(def generate-signature #'ip/generate-signature)
(def get-url #'ip/get-url)
(def process-result #'ip/process-result)

(def test-ip-result 
  {:Location {:continent "north america"
              :latitude 33.79846
              :longitude -84.38828
              :CountryData {:country "united states"
                            :country_code "us"
                            :country_cf 99}
              :region "southeast"
              :StateData {:state "georgia"
                          :state_code "ga"
                          :state_cf 95}
              :dma 524
              :msa 12060
              :CityData {:city "atlanta"
                         :postal_code "30309"
                         :time_zone -5
                         :area_code "404"
                         :city_cf 80}}})

(deftest generate-signature-test
  (testing "Should return a 32 digit alphanumeric string"
    (let [actual (generate-signature)]
      (is (string? actual))
      (t/equal? 32 (count actual)))))

(deftest get-url-test
  (testing "Should return a url beginning with http://api.neustar.biz/ipi/gpp/v1/ipinfo/96.8.88.93?format=json&apikey=220.1.5692e8c4e4b005ca242356f0.yu
          vXw5DHA given 96.8.88.93, en-US, and json."
    (let [actual (get-url "96.8.88.93" "en-US" "json")
          expected "http://api.neustar.biz/ipi/gpp/v1/ipinfo/96.8.88.93?format=json&apikey=220.1.5692e8c4e4b005ca242356f0.yuvXw5DHA"]
      (t/equal? expected (first (str/split actual #"&sig"))))))

(deftest process-result-test
  (testing "Should return a map of location data given a sample ip result." 
    (let [actual (process-result (:Location test-ip-result))]
      (t/equal? 33.79846 (:latitude actual))
      (t/equal? -84.38828 (:longitude actual))
      (t/equal? "north america" (:continent actual))
      (t/equal? 12060 (:msa actual))
      (t/equal? 524 (:dma actual))
      (t/equal? "southeast" (:region actual)))))
