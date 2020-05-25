(ns location.geocoder.reverse-test
  (:require [clojure.test                 :refer :all]
            [clojure.java.io              :as io]
            [location.utils.test          :as t]
            [location.geocoder.reverse    :as rev]))

(deftest reverse-geo-test
  (testing "Should return a map of results given valid geocode \"33.89,-84.46\" as a parameter"
    (let [r (first (rev/reverse-geo "33.89,-84.46"))]
      (t/equal? "33.89" (:latitude r))
      (t/equal? "-84.46" (:longitude r))
      (t/equal? "United States" (:country r))
      (t/equal? "US" (:countryCode r))
      (t/equal? "Georgia" (:adminDistrict r))
      (t/equal? "GA" (:adminDistrictCode r))
      (t/equal? "Atlanta" (:city r))
      (t/equal? "America/New_York" (:ianaTimeZone r))
      (t/equal? "30339" (:postalCode r))
      (t/equal? "30339:US" (:postalKey r))))

  (testing "Should return nil given invalid coordinates"
    (let [actual (rev/reverse-geo "35.9078,-187.7669")]
      (t/equal? actual nil))))
