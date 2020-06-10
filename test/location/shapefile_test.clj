;;(ns location.shapefile-test
;;  (:require [clojure.test        :refer :all]
;;            [location.utils.test :as t]
;;            [location.shapefile  :as shape])
;;  (:import [org.geotools.filter.text.cql2 CQL]))
;;
;;(def kilo-to-degree #'location.shapefile/kilo-to-degree)
;;(def calc-zoom-level #'location.shapefile/calc-zoom-level)
;;(def contains-filter #'location.shapefile/contains-filter)
;;(def dwithin-filter #'location.shapefile/dwithin-filter)
;;(def intersects-filter #'location.shapefile/intersection-filter)
;;(def get-shapefile-path #'location.shapefile/get-shapefile-path)
;;(def get-shapefile #'location.shapefile/get-shapefile)
;;(def get-features #'location.shapefile/get-features)
;;(def try-locale-attr #'location.shapefile/try-locale-attr)
;;(def expand-product #'location.shapefile/expand-product)
;;(def get-polygon #'location.shapefile/get-polygon)
;;(def contains-filter #'location.shapefile/contains-filter)
;;(def touches-filter #'location.shapefile/touches-filter)
;;(def dwihin-filter #'location.shapefile/dwithin-filter)
;;(def intersection-filter #'location.shapefile/intersection-filter)
;;(def resolve-indexed-shapefile #'location.shapefile/resolve-indexed-shapefile)
;;(def close-shape #'location.shapefile/close-shape)
;;(def test-filter (contains-filter "33.89,-84.46"))
;;
;;(deftest kilo-to-degree-test
;;  (testing "Should return one given 40076/360 km"
;;    (let [actual (kilo-to-degree (/ 40076 360))
;;          expected 1.0]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return .08 given 10 km"
;;    (let [actual (kilo-to-degree 10)
;;          expected 0.08982932428386066]
;;      (t/equal? expected actual))))
;;
;;(deftest calc-zoom-level-test
;;  (testing "Should return 180.0 given 0"
;;    (let [actual (calc-zoom-level 0)
;;          expected 180.0]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return .08 given 10"
;;    (let [actual (calc-zoom-level 11)
;;          expected 0.08982932428386066]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return nil given 15"
;;    (let [actual (calc-zoom-level 15)
;;          expected nil]
;;      (t/equal? expected actual))))
;;
;;(deftest contains-filter-test
;;  (testing "Should return a CQL contains filter given a latitude and longitude"
;;    (let [actual (CQL/toCQL (contains-filter "33.89,-84.46"))
;;          expected "CONTAINS(the_geom, POINT (-84.46 33.89))"]
;;      (t/equal? expected actual))))
;;
;;(deftest dwithin-filter-test
;;  (testing "Should return a CQL dwithin filter given a latitude, longitude and zoom level 0"
;;    (let [actual (CQL/toCQL (dwithin-filter "33.89,-84.46" 0))
;;          expected "DWITHIN(the_geom, POINT (-84.46 33.89), 180.0, kilometers)"]
;;      (t/equal? expected actual))))
;;
;;(deftest intersects-filter-test
;;  (testing "Should return a CQL dwithin filter given a latitude, longitude and zoom level 0"
;;    (let [actual (CQL/toCQL (intersects-filter (first (shape/get-geometries "33.89,-84.46" "pollen"))))
;;          intersect-regex (re-pattern "INTERSECTS")
;;          multi-polygon-regex (re-pattern "MULTIPOLYGON")]
;;      (is (re-find intersect-regex actual))
;;      (is (re-find multi-polygon-regex actual)))))
;;
;;(deftest filter-test
;;  (testing "Should return a contains filter given a geocode"
;;    (let [actual (type (contains-filter "33.89,-84.46"))
;;          expected org.geotools.filter.spatial.ContainsImpl]
;;      (t/equal? actual expected)))
;;
;;  (testing "Should return a dwithin filter given a geocode and zoom"
;;    (let [actual (type (dwithin-filter "33.89,-84.46" 0))
;;          expected org.geotools.filter.spatial.DWithinImpl]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return an intersects filter given an overlay object"
;;    (let [actual (type (intersection-filter (first (shape/get-geometries "33.89,-84.46" "pollen"))))
;;          expected org.geotools.filter.AndImpl]
;;      (t/equal? expected actual))))
;;
;;(deftest get-shapefile-path-test
;;  (testing "Should return file path based on configuration given pollen shapefile"
;;    (let [actual (get-shapefile-path "pollen")
;;          expected "resources/shapefiles/pollen/pollen.shp"]
;;      (t/equal? expected actual))))
;;
;;(deftest get-shapefile-test
;;  (testing "Should return a file object given pollen shapefile"
;;    (let [actual (type (get-shapefile "pollen"))
;;          expected java.io.File]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return nil given an invalid shapefile"
;;    (let [actual (type (get-shapefile "invalid"))
;;          expected nil]
;;      (t/equal? expected actual))))
;;
;;(deftest get-features-test
;;  (testing "Should return a feature reader given a contains filter and pollen shapefile"
;;    (let [[store actual] (get-features "pollen" (contains-filter "33.89,-84.46"))]
;;      (t/in-message? #"ContentFeatureCollection" (str (type actual)))
;;      (close-shape store actual)))
;;
;;  (testing "Should return nil given an invalid shapefile"
;;    (let [[store actual] (get-features "invalid" (contains-filter "33.89,-84.46"))
;;          expected nil]
;;      (t/equal? expected actual)
;;      (close-shape store actual))))
;;
;;(deftest try-locale-attr-test
;;  (let [[store features] (get-features "adminDistrict3_Namer" (contains-filter "35.46,-97.52"))
;;        data (.next features)]
;;
;;  (testing "Should return Oklahoma Plaats given nl-nl and valid data source"
;;    (let [actual (try-locale-attr data "nl-nl")
;;          expected "Oklahoma Plaats"]
;;     (t/equal? expected actual)))
;;
;;  (testing "Should return Oklahoma City given tlh-US and valid data source"
;;    (let [actual (try-locale-attr data "tlh-US")
;;          expected "Oklahoma City"]
;;     (t/equal? expected actual)))
;;
;;  (testing "Should return Oklahoma City given nil locale and valid data source"
;;    (let [actual (try-locale-attr data nil)
;;          expected "Oklahoma City"]
;;     (t/equal? expected actual)))
;;
;;  (close-shape store features)))
;;
;;(deftest expand-product-test
;;  (testing "should return [\"zone\" \"country\"] given alert"
;;    (let [actual (expand-product "alerts")
;;          expected ["county" "zone"]]
;;      (t/equal? expected actual)))
;;
;;  (testing "should return \"zone\" given zoneId"
;;    (let [actual (expand-product "zoneId")
;;          expected ["zone"]]
;;      (t/equal? expected actual)))
;;
;;  (testing "should return nil given invalid product"
;;    (let [actual (expand-product "invalid")]
;;      (t/equal? nil actual))))
;;
;;(deftest resolve-indexed-shapefile-test
;;  (testing "Should return adminDistrict3_Namer given adminDistrict3 and a filter with North America point"
;;    (let [fltr (contains-filter "35.46,-97.52")
;;          actual (resolve-indexed-shapefile "adminDistrict3" fltr)
;;          expected "adminDistrict3_Namer"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return adminDistrict3_Samer given adminDistrict3 and a filter with South America point"
;;    (let [fltr (contains-filter "-35.46,-97.52")
;;          actual (resolve-indexed-shapefile "adminDistrict3" fltr)
;;          expected "adminDistrict3_Samer"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return adminDistrict3 given adminDistrict3 and a nil filter"
;;    (let [actual (resolve-indexed-shapefile "adminDistrict3" nil)
;;          expected "adminDistrict3"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return postalKey given postalKey a filter with North America point"
;;    (let [fltr (contains-filter "35.46,-97.52")
;;          actual (resolve-indexed-shapefile "postalKey" fltr)
;;          expected "postalKey"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return postalKey given postalKey a nil filter"
;;    (let [fltr nil
;;          actual (resolve-indexed-shapefile "postalKey" fltr)
;;          expected "postalKey"]
;;      (t/equal? expected actual))))
;;
;;(deftest get-polygon-test
;;  (testing "Should return Oklahoma Plaats given nl-nl, adminDistrict3_Namer, and a contains filter"
;;    (let [actual (first (get-polygon (contains-filter "35.46,-97.52")
;;                                     "nl-nl"
;;                                     "adminDistrict3_Namer"))
;;          expected "Oklahoma Plaats"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return Oklahoma City given tlh-US, adminDistrict3_Namer, and a contains filter"
;;    (let [actual (first (get-polygon (contains-filter "35.46,-97.52")
;;                                     "tlh-US"
;;                                     "adminDistrict3_Namer"))
;;          expected "Oklahoma City"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return Oklahoma Plaats given nl-nl, adminDistrict3, a contains filter resolving the index"
;;    (let [fltr (contains-filter "35.46,-97.52")
;;          actual (first (get-polygon fltr "nl-nl" (resolve-indexed-shapefile "adminDistrict3" fltr)))
;;          expected "Oklahoma Plaats"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return Oklahoma City given tlh-US, adminDistrict3, a contains filter resolving the index"
;;    (let [fltr (contains-filter "35.46,-97.52")
;;          actual (first (get-polygon fltr "tlh-US" (resolve-indexed-shapefile "adminDistrict3" fltr)))
;;          expected "Oklahoma City"]
;;      (t/equal? expected actual))))
;;
;;(deftest get-polygons-test
;;  (testing "Should return pollen value given Request for pollen"
;;    (let [actual (first (shape/get-polygons "33.89,-84.46" #{"pollen"} "en-US"))
;;          expected "ATL"]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return pollen values given Request for pollen and zoom level 8"
;;    (let [actual (first (shape/get-polygons "33.89,-84.46" #{"pollen"} "en-US" "8"))
;;          expected (seq ["ANB" "ATL"])]
;;      (t/equal? expected actual)))
;;
;;  (testing "Should return multiple postalKeys given request with zoom level 10"
;;    (let [zipcodes (flatten (shape/get-polygons "33.89,-84.46"
;;                                                    #{"postalKey"}
;;                                                    "en-US"
;;                                                    "10"))]
;;      (is (< 1 (count zipcodes)))
;;      (is (coll? zipcodes))))
;;
;;  (testing "Should return nil given Request for invalid"
;;    (let [polygons (shape/get-polygons "33.89,-84.46" #{"invalid"} "en-US" "14")]
;;      (is (nil? (:invalid polygons))))))
;;
;;(deftest get-intersecs-test
;;  (testing "Should return a sequence of vectors when given valid geocode, product, and overlay"
;;    (let [actual (shape/get-intersecs "33.89,-84.46" "postalKey" "pollen")
;;          actual-alerts (shape/get-intersecs "33.89,-84.46" "postalKey" "alerts")]
;;      (t/equal? (count actual) 1)
;;      (t/equal? (count actual-alerts) 2))))
