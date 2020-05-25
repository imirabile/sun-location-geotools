(ns location.providers.mapbox-test
  (:require [clojure.test              :refer :all]
            [clojure.string            :as str]
            [location.utils.test       :as t]
            [location.utils.placeid    :as plid]
            [location.config           :as cfg]
            [clojure.tools.logging   :as log]
            [location.providers.mapbox :as mapbox]))

(def ^:private add-limit #'location.providers.mapbox/add-limit)
(def ^:private add-filter #'location.providers.mapbox/add-filter)
(def ^:private add-country-filter #'location.providers.mapbox/add-country-filter)
(def ^:private add-limit-and-filter #'location.providers.mapbox/add-limit-and-filter)
(def ^:private add-common-params #'location.providers.mapbox/add-common-params)
(def ^:private mapbox-process-result #'location.providers.mapbox/process-result)
(def ^:private decrypt-message #'location.utils.placeid/decrypt-message)
(def ^:private add-language-mode #'location.providers.mapbox/add-language-mode)

(def geocode-url "https://api.mapbox.com/geocoding/v5/mapbox.places/-84.458455,33.893576.json?access_token=pk.eyJ1Ijoid2VhdGhlciIsImEiOiJjaWxtaHN0Z3U2NmlndXRtMDVyeHoyeHNoIn0.cWl0ItkGKb2cEagmkBM7Ug&language=fr-CA&types=place,region,postcode,neighborhood,locality,district")

(def address-url "https://api.mapbox.com/geocoding/v5/mapbox.places/300 Interstate North Pkwy, Atlanta, GA 30339.json?access_token=pk.eyJ1Ijoid2VhdGhlciIsImEiOiJjaWxtaHN0Z3U2NmlndXRtMDVyeHoyeHNoIn0.cWl0ItkGKb2cEagmkBM7Ug&language=en-US&limit=20&types=place,region,postcode,neighborhood,locality,district")

(def test-record "{\"type\":\"FeatureCollection\",\"query\":[-73.989,40.733],\"features\":[{\"id\":\"address.8563733399016392\",\"type\":\"Feature\",\"text\":\"E 13th St\",\"place_name\":\"114 E 13th St New York, New York 10003 United States\",\"relevance\":1,\"properties\":{},\"bbox\":[-73.99370499999999,40.727712999999994,-73.97750999999998,40.73540469999999],\"center\":[-73.989132,40.732943],\"geometry\":{\"type\":\"Point\",\"coordinates\":[-73.989132,40.732943]},\"address\":\"114\",\"context\":[{\"id\":\"neighborhood.21161\",\"text\":\"Gramercy-Flatiron\"},{\"id\":\"place.37501\",\"text\":\"New York\"},{\"id\":\"postcode.2254639497\",\"text\":\"10003\"},{\"id\":\"region.628083222\",\"text\":\"New York\"},{\"id\":\"country.4150104525\",\"text\":\"United States\",\"short_code\":\"us\"}]},{\"id\":\"place.37501\",\"type\":\"Feature\",\"text\":\"New York\",\"place_name\":\"New York, New York, United States\",\"relevance\":1,\"properties\":{},\"bbox\":[-74.04728500751165,40.68392799015035,-73.91058699000139,40.87764500765852],\"center\":[-74.006,40.7143],\"geometry\":{\"type\":\"Point\",\"coordinates\":[-74.006,40.7143]},\"context\":[{\"id\":\"postcode.2254639497\",\"text\":\"10003\"},{\"id\":\"region.628083222\",\"text\":\"New York\"},{\"id\":\"country.4150104525\",\"text\":\"United States\"}]},{\"id\":\"postcode.2254639497\",\"type\":\"Feature\",\"text\":\"10003\",\"place_name\":\"10003, New York, United States\",\"relevance\":1,\"properties\":{},\"bbox\":[-73.99960399999998,40.722933,-73.97986399999999,40.73967299999999],\"center\":[-73.991023,40.731226],\"geometry\":{\"type\":\"Point\",\"coordinates\":[-73.991023,40.731226]},\"context\":[{\"id\":\"region.628083222\",\"text\":\"New York\"},{\"id\":\"country.4150104525\",\"text\":\"United States\"}]},{\"id\":\"region.628083222\",\"type\":\"Feature\",\"text\":\"New York\",\"place_name\":\"New York, United States\",\"relevance\":1,\"properties\":{},\"bbox\":[-79.76241799999997,40.477398999999984,-71.77749099999998,45.015864999999984],\"center\":[-76.181929,42.773969],\"geometry\":{\"type\":\"Point\",\"coordinates\":[-76.181929,42.773969]},\"context\":[{\"id\":\"country.4150104525\",\"text\":\"United States\"}]},{\"id\":\"country.4150104525\",\"type\":\"Feature\",\"text\":\"United States\",\"place_name\":\"United States\",\"relevance\":1,\"properties\":{},\"bbox\":[-179.23023299999997,18.866158999999996,179.85968099999994,71.43776899999999],\"center\":[-98.958425,36.778951],\"geometry\":{\"type\":\"Point\",\"coordinates\":[-98.958425,36.778951]}}],\"attribution\":\"NOTICE: © 2015 Mapbox and its suppliers. All rights reserved. Use of this data is subject to the Mapbox Terms of Service (https://www.mapbox.com/about/maps/). This response and the information it contains may not be retained.\"}")

(def test-json "{\"number\":1, \"string\":\"test\"}")

(def new-york-place-type-record "{
  \"type\": \"FeatureCollection\",
  \"query\": [
    \"new\",
    \"york\"
  ],
  \"features\": [
    {
      \"id\": \"place.3866\",
      \"type\": \"Feature\",
      \"text\": \"New York\",
      \"place_name\": \"New York, United States\",
      \"place_type\": [
        \"region\"
      ],
      \"relevance\": 0.99,
      \"properties\": {
        \"short_code\": \"US-NY\",
        \"wikidata\": \"Q1384\"
      },
      \"language\": \"en\",
      \"bbox\": [
        -79.762418,
        40.420528,
        -71.678024,
        45.015865
      ],
      \"center\": [
        -76.301667,
        42.685498
      ],
      \"geometry\": {
        \"type\": \"Point\",
        \"coordinates\": [
          -76.301667,
          42.685498
        ]
      },
      \"context\": [
        {
          \"text\": \"United States\",
          \"language\": \"en\",
          \"id\": \"country.3145\",
          \"short_code\": \"us\",
          \"wikidata\": \"Q30\"
        }
      ]
    }
  ],
  \"attribution\": \"NOTICE: © 2017 Mapbox and its suppliers. All rights reserved. Use of this data is subject to the Mapbox Terms of Service (https://www.mapbox.com/about/maps/). This response and the information it contains may not be retained.\"}")

(def new-york-no-place-type-record "{
  \"type\": \"FeatureCollection\",
  \"query\": [
    \"new\",
    \"york\"
  ],
  \"features\": [
    {
      \"id\": \"place.3866\",
      \"type\": \"Feature\",
      \"text\": \"New York\",
      \"place_name\": \"New York, United States\",
      \"relevance\": 0.99,
      \"properties\": {
        \"short_code\": \"US-NY\",
        \"wikidata\": \"Q1384\"
      },
      \"language\": \"en\",
      \"bbox\": [
        -79.762418,
        40.420528,
        -71.678024,
        45.015865
      ],
      \"center\": [
        -76.301667,
        42.685498
      ],
      \"geometry\": {
        \"type\": \"Point\",
        \"coordinates\": [
          -76.301667,
          42.685498
        ]
      },
      \"context\": [
        {
          \"text\": \"United States\",
          \"language\": \"en\",
          \"id\": \"country.3145\",
          \"short_code\": \"us\",
          \"wikidata\": \"Q30\"
        }
      ]
    }
  ],
  \"attribution\": \"NOTICE: © 2017 Mapbox and its suppliers. All rights reserved. Use of this data is subject to the Mapbox Terms of Service (https://www.mapbox.com/about/maps/). This response and the information it contains may not be retained.\"}")

(deftest add-limit-test
  (testing "Should return test&limit=20 given test url"
    (let [actual (add-limit "test" "forward-geo")
          expected "test&limit=20"]
      (t/equal? expected actual))))

(deftest add-filter-test
  (testing "Should return test&types=city given test url and locality filter"
    (let [actual (add-filter "test" "city")
          expected "test&types=place"]
      (t/equal? expected actual)))

  (testing "Should return test given test url and nil filter"
    (let [actual (add-filter "test" nil)
          expected "test&types=place,region,postcode,neighborhood,locality,district"]
      (t/equal? expected actual))))

(deftest add-country-filter-test
  (testing "Should return test&country=CA given test url and locality filter"
    (let [actual (add-country-filter "test" "CA")
          expected "test&country=ca"]
      (t/equal? expected actual)))

  (testing "Should return test given test url and nil filter"
    (let [actual (add-country-filter "test" nil)
          expected "test"]
      (t/equal? expected actual))))

(deftest add-limit-and-filter-test
  (testing "Should return test&types=city given test and reverse-geo lookup"
    (let [actual (add-limit-and-filter "test" "reverse-geo" "city")
          expected "test&types=place"]
      (t/equal? expected actual)))

  (testing "Should return test&limit=20&types=city given test and forward-geo lookup"
    (let [actual (add-limit-and-filter "test" "forward-geo" "city")
          expected "test&limit=20&types=place"]
      (t/equal? expected actual)))

  (testing "Should return test given test and reverse-geo lookup and no filter"
    (let [actual (add-limit-and-filter "test" "reverse-geo" nil)
          expected "test&types=place,region,postcode,neighborhood,locality,district"]
      (t/equal? expected actual)))

  (testing "Should return test&limit=20 given test, forward-geo lookup and no filter"
    (let [actual (add-limit-and-filter "test" "forward-geo" nil)
          expected "test&limit=20&types=place,region,postcode,neighborhood,locality,district"]
      (t/equal? expected actual)))

  (testing "Should return 'url&languageMode=strict'"
    (let [actual (add-language-mode "url" {:lookup "search" :address nil :language "fr" :locationType nil})
          expected "url&languageMode=strict"]
      (t/equal? expected actual)))

  (testing "Should return simply 'url' due to language='en'"
    (let [actual (add-language-mode "url" {:address nil :language "en" :locationType nil})
          expected "url"]
      (t/equal? expected actual)))

  (testing "Should return simply 'url' due to address='address'"
    (let [actual (add-language-mode "url" {:address "address" :language "fr" :locationType nil})
          expected "url"]
      (t/equal? expected actual)))

  (testing "Should return simply 'url' due to address='address' and locationType='address'"
    (let [actual (add-language-mode "url" {:address "address" :language "fr" :locationType "address"})
          expected "url"]
      (t/equal? expected actual)))

  (testing "Should return simply 'url' due to address='address' and locationType='address' and language='en'"
    (let [actual (add-language-mode "url" {:address "address" :language "en" :locationType "address"})
          expected "url"]
      (t/equal? expected actual)))

  (testing "Should return simply 'url' due to locationType='address'"
    (let [actual (add-language-mode "url" {:address nil :language "fr" :locationType "address"})
          expected "url"]
      (t/equal? expected actual))))

(deftest add-common-params-test
  (testing "Should return test.json?access_token=pk.eyJ1Ijoid2VhdGhlciIsImEiOiJjaWxtaHN0Z3U2NmlndXRtMDVyeHoyeHNoIn0.cWl0ItkGKb2cEagmkBM7Ug&language=en given test and en"
    (let [actual (add-common-params "test" "en-US")
          expected "test.json?access_token=pk.eyJ1Ijoid2VhdGhlciIsImEiOiJjaWxtaHN0Z3U2NmlndXRtMDVyeHoyeHNoIn0.cWl0ItkGKb2cEagmkBM7Ug&language=en-US"]
      (t/equal? expected actual))))

(deftest get-url-test
  (testing "Should return a mapbox url given lat/long"
    (let [actual (mapbox/get-url "33.893576,-84.458455" {:address nil :language "fr-CA"})
          expected geocode-url]
      (t/equal? expected actual)))

  (testing "Should return a mapbox url given an address"
    (let [actual (mapbox/get-url "300 Interstate North Pkwy, Atlanta, GA 30339" {:address "address" :language "en-US"})
          expected address-url]
      (t/equal? expected actual))))

(deftest extract-location-data-test
  (testing "Should return center, place_name, and context given test-record"
    (let [extracted (first (mapbox/extract-location-data test-record '(:center :place_name :context)))]
      (is (contains? extracted :center))
      (is (contains? extracted :place_name))
      (is (contains? extracted :context))
      (t/equal? 3 (count extracted))))

  (testing "Should return an empty map given a key not present in record"
    (let [extracted (first (mapbox/extract-location-data test-record '(:foo) ))]
      (is (empty? extracted)))))

(deftest flatten-context-test
  (testing "Should return a flattened map given a seq of maps"
    (let [flattened (mapbox/flatten-context [{:id "key", :val "val"}])]
      (is (map? flattened))
      (t/equal? "val" (get-in flattened ["key" :val]))))

  (testing "Should return a flattened map given a seq with nested maps"
    (let [flattened (mapbox/flatten-context [{:id "key" :one 1 :two 2}])]
      (is (map? flattened))
      (t/equal? {:one 1 :two 2} (get flattened "key")))))

(deftest split-id-test
  (testing "Should return :id given 'id.1234'"
    (let [actual (mapbox/split-id "id.1234")
          expected :id]
      (t/equal? expected actual))))

(deftest sanitize-location-data-test
  (testing "Should return a sanitized clojure map given flattened mapbox context array"
    (let [sanitized (mapbox/sanitize-location-data {"one.1234" "one" "two.2345" 2})]
      (is (map? sanitized))
      (t/equal?"one" (:one sanitized)))))

(deftest sanitize-context-test
  (testing "Should return a Clojure map with place New York given a raw mapbox context"
    (let [context (:context (first (mapbox/extract-location-data test-record '(:context))))
          sanitized (mapbox/sanitize-context context)
          place (:place sanitized)]
      (is (map? sanitized))
      (t/equal? "New York" (:text place)))))

(deftest process-result-test
  (testing "Should return a Location record with postal code 10003 given test-record"
    (let [location (mapbox-process-result
                     (first (mapbox/extract-location-data test-record '(:context :center :place_name :id :text)))
                     {:language "en" :lookup "map-point" })]
      (map? location)
      (t/equal? "10003" (:postalCode location)))))

(deftest placeid-from-place-type-test
  (let [region-placeid "62b3df22433003eab627833845a26fc3eef9159d78665ec68811a9cc8fd73bad"
        place-placeid  "62b3df22433003eab627833845a26fc322ce96a02b3056da66ef25b4d80b844b"]
    (testing "Should return a placeId for calculated from the new mapbox field \"place_type\""
      ;; Logic will use the \"region\" as the location type.
      (let [temp (->> (cfg/get-locationdata-keys "mapbox")
                          (mapbox/extract-location-data new-york-place-type-record)
                          first)
            location (mapbox-process-result temp {:language "en" :lookup "map-point" })]
        (map? location)
        (t/equal? region-placeid (:placeId location))))

    (testing "Should return a decrypted message with the new york region based on the placeId calculated from the new mapbox field \"place_type\""
      (let [message (decrypt-message region-placeid)]
        (t/equal? "42.685498,-76.301667:region" message)))

    (testing "Should return a placeId calculated from the old mapbox field \"id\""    
      ;; Due to the absence of \"place_type\" in this record, Logic will perform a fallback 
      ;; and use the \"place\" instead of the \"region\" used above, hence, the placeId will differ. 
      (let [temp (->> (cfg/get-locationdata-keys "mapbox")
                      (mapbox/extract-location-data new-york-no-place-type-record)
                      first)
            location (mapbox-process-result temp {:language "en" :lookup "map-point" })]
        (map? location)
        (t/equal? place-placeid (:placeId location))))))

(deftest nil-vals-test
  (testing "Should return a seq of keywords that contain nil values of given map"
    (let [actual (mapbox/nil-vals {:test nil :real-key "value"})
          multiple-nil (mapbox/nil-vals {:test nil :real-key nil})]
      (t/equal? actual '(:test))
      (t/equal? multiple-nil '(:test :real-key))))

  (testing "Should return nil if a map is passed in with no nil values"
    (let [actual (mapbox/nil-vals {:test "test" :real-key "value"})]
      (t/equal? actual nil)))

  (testing "Should return nil if nil is passed in"
    (let [actual (mapbox/nil-vals nil)]
      (t/equal? actual nil)))

  (testing "Should return nil if {} is passed in"
    (let [actual (mapbox/nil-vals {})]
      (t/equal? actual nil))))

(deftest find-missing-fields-test
  (testing "Should return a map with a value for city when passed in valid GA coordinates with nil city"
    (let [actual (mapbox/find-missing-fields {:latitude "34.025064" :longitude "-84.300303" :city nil :feature "postcode"} "en-US" ["city"])
          expected {:city "Roswell"}]
      (t/equal? actual expected)))

  (testing "Should return nil when passed in valid GA coordinates with non-nil city"
    (let [actual (mapbox/find-missing-fields {:latitude "34.025064" :longitude "-84.300303" :city "Roswell" :feature "postcode"} "en-US" ["city"])
          expected nil]
      (t/equal? actual expected))))

(deftest add-missing-fields-test
  (testing "Should return a full response set with valid city given :city as nil and valid geo coordinates"
    (let [actual (mapbox/add-missing-fields {:latitude "34.025064" :longitude "-84.300303" :city nil :feature "postcode" } "en-US" ["city"])
          expected {:latitude "34.025064" :longitude "-84.300303" :city "Roswell" :feature "postcode"}]
      (t/equal? actual expected)))

  (testing "Should return a full response set with valid city given valid :city and valid geo coordinates"
    (let [actual (mapbox/add-missing-fields {:latitude "34.025064" :longitude "-84.300303" :city "Roswell" :feature "postcode" } "en-US" ["city"])
          expected {:latitude "34.025064" :longitude "-84.300303" :city "Roswell" :feature "postcode"}]
      (t/equal? actual expected))))

(testing "Should return url&types=<locale expansion> given 'url' as url and 'locale' as filter. 
Note: location.providers.mapbox/add-filter uses cfg/expand-filter to expand 'locale'"
   (let [actual (add-filter "url" "locale")
         expected (str "url&types=" (str/join "," (cfg/get-config "geoLocation.lookup.mapbox.filter.locale")))]
     (t/equal? expected actual)))
