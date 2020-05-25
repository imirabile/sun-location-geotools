(ns location.providers.bing-test
  (:require [clojure.test :refer :all]
            [location.utils.test     :as t]
            [location.providers.bing :as bing]))

(def geocode-url "http://dev.virtualearth.net/REST/v1/Locations/33.89,-84.46?o=json&incl=ciso2&maxResults=20&c=en-US&key=AjYRc8eoG27Zm5Obmrhd9uIwMdmGvSrP6_9ZCY3ZoL19OFPKnWNfO-Tsx4ueTU7W") 
(def address-url "http://dev.virtualearth.net/REST/v1/Locations/?q=300+Interstate+North+Pkwy%2C+Atlanta%2C+GA+30339&o=json&incl=ciso2&maxResults=20&c=en-US&key=AjYRc8eoG27Zm5Obmrhd9uIwMdmGvSrP6_9ZCY3ZoL19OFPKnWNfO-Tsx4ueTU7W")

(def test-record "{\"authenticationResultCode\":\"ValidCredentials\",\"brandLogoUri\":\"http:\\/\\/dev.virtualearth.net\\/Branding\\/logo_powered_by.png\",\"copyright\":\"Copyright © 2015 Microsoft and its suppliers. All rights reserved. This API cannot be accessed and the content and any results may not be used, reproduced or transmitted in any manner without express written permission from Microsoft Corporation.\",\"resourceSets\":[{\"estimatedTotal\":1,\"resources\":[{\"__type\":\"Location:http:\\/\\/schemas.microsoft.com\\/search\\/local\\/ws\\/rest\\/v1\",\"bbox\":[33.683296203613281,-84.578361511230469,34.237503051757813,-83.497245788574219],\"name\":\"Gwinnett\",\"point\":{\"type\":\"Point\",\"coordinates\":[33.96171188354492,-84.02356719970703]},\"address\":{\"addressLine\":\"Sugar Cane Pl\",\"adminDistrict\":\"GA\",\"adminDistrict2\":\"Gwinnett Co.\",\"countryRegion\":\"United States\",\"countryRegionIso2\":\"US\",\"formattedAddress\":\"Sugar Cane Pl, Gwinnett\",\"locality\":\"Duluth\",\"postalCode\":\"30096\"},\"confidence\":\"High\",\"entityType\":\"AdminDivision2\",\"geocodePoints\":[{\"type\":\"Point\",\"coordinates\":[33.961711883544922,-84.023567199707031],\"calculationMethod\":\"Rooftop\",\"usageTypes\":[\"Display\"]}],\"matchCodes\":[\"Good\"]}]}],\"statusCode\":200,\"statusDescription\":\"OK\",\"traceId\":\"3d5f33b3ad1a4e95b29e676edec7b2cf|HK20271655|02.00.163.1200|HK2SCH010280324, HK2SCH010301755, HK2SCH010281520\"}")

(def test-json "{\"number\":1, \"string\":\"test\"}")

(def address-request {:lookup "address" 
                      :params {:address "300 Interstate North Pkwy, Atlanta, GA 30339" 
                               :language "en-US"
                               :format "json"}})

(deftest filterAddressLine-test
  (testing "Should return nil if given certain strings like \"Street\""
    (t/equal? (bing/filterAddressLine "Street") nil)
    (t/equal? (bing/filterAddressLine "Valid Address") "Valid Address")))

(deftest get-url-test
  (testing "Should return valid bing geocode url given lat/long"
    (let [actual (bing/get-url "33.89,-84.46" "en-US" "json")
          expected geocode-url]
      (t/equal? expected actual))) 

  (testing "Should return valid bing address url given street address"
    (let [actual (bing/get-url "300 Interstate North Pkwy, Atlanta, GA 30339" "en-US" "json")
          expected address-url]
      (t/equal? expected actual))))

(deftest extract-location-data-test
  (testing "Should extract only the fields needed to populate response given bing location record"
    (let [extracted (first (bing/extract-location-data test-record '(:point :address)))]
      (is (contains? extracted :point))
      (is (contains? extracted :address))
      (t/equal? 2 (count extracted))))

  (testing "Should return an empty map given invalid key"
    (let [extracted (first (bing/extract-location-data test-record '(:foo)))]
      (is (empty? extracted)))))

(deftest process-result-test
  (testing "Should return a location map with postal code 30096 given test-record"
    (let [location (bing/process-result (first (bing/extract-location-data test-record '(:point :address))))]
      (map? location)
      (t/equal? "30096" (:postal_code location)))))
