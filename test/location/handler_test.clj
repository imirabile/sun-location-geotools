(ns location.handler-test
  (:require [clojure.test             :refer :all]
            [clojure.data.json        :as json]
            [clojure.string           :as str]
            [clojure.set              :as s]
            [clojure.tools.logging    :as log]
            [ring.mock.request        :as mock]
            [location.error-message   :as e]
            [location.utils.test      :as t]
            [location.utils.common    :as util]
            [location.utils.exception :as ex]
            [location.config          :as cfg]
            [location.handler         :refer :all]))

(def ^:private format-response #'location.handler/format-response)
(def ^:private get-metric #'location.handler/get-metric)

(def ^:private json "application/json")
(def ^:private xml "text/xml")
(def ^:private text "text/html")
(def ^:private charset "charset=utf-8")
(def ^:private success 86400)
(def ^:private success-v2 86400)
(def ^:private error 300)
(def ^:private ip-success 3600)


(defn ^:private verify-headers
  "Tests that the Content-Type and Cache-Control headers are set correctly"
  [response ct cache]
  (let [headers (:headers response)]
    (t/equal? (str ct "; " charset) (get headers "Content-Type"))
    (t/equal? (str "max-age=" cache) (get headers "Cache-Control"))))

(deftest format-response-test
  (testing "Should return test doc given a map with a status"
    (let [actual (format-response {:status 200})
          expected {:status 200}]
      (t/equal? expected actual)))

  (testing "Should return test doc given a map with a status"
    (let [actual (format-response {:test true})
          expected {:status 200 :headers {} :body {:test true}}]
      (t/equal? expected actual))))

(deftest get-metric-test
  (testing "Should return a meter and timer for the request metric given a route"
    (let [actual (get-metric "search")]
      (is (instance? com.codahale.metrics.Meter (:meter actual)))
      (is (instance? com.codahale.metrics.Timer (:timer actual))))))

(deftest process-errors-test
  (testing "Should return an error document given a not found error message"
    (let [actual (ex/process-errors [(partial e/get-error :not-found)])]
      (t/equal? 404 (:status actual))
      (t/not-nil? (get-in actual [:body :errors])))))

(deftest heartbeat-route-test
  (testing "Should return 200 given a request for heartbeat"
    (let [response (app (mock/request :get "/heartbeat"))]
      (verify-headers response text success)
      (t/equal? 200 (:status response))
      (t/equal? (:body response) "OK"))))

(deftest not-found-test
  (testing "Should return 404 given a request for invalid"
    (let [response (app (mock/request :get "/invalid"))]
      (verify-headers response json error)
      (t/equal? 404 (:status response)))))



(deftest ^:integration legacy-route-test-v2
  (testing "Should return 200, no errors, given location route with valid, period separated geocode"
    (let [response (app (mock/request :get "/v2/location?geocode=33.89,-84.45&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          headers (:headers response)
          location (:addresses body)]
      (verify-headers response json success-v2)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200 given location route with valid geocode that returns no location"
    (let [response (app (mock/request :get "/v2/location?geocode=90.00,90.00&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success-v2)
      (is empty? (:error response))
      (t/equal? 200 (:status response))))


  (testing "Should return 200, no errors, given location route with full postal address"
    (let [response (app (mock/request :get "/v2/location?address=300%20interstate%20North%20Parkway,%20Atlanta,%20GA,%2030339&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:addresses body)]
      (verify-headers response json success-v2)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200, no errors, given location route with zip code"
    (let [response (app (mock/request :get "/v2/location?address=30339&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:addresses body)]
      (verify-headers response json success-v2)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200, no errors, given location route with city name"
    (let [response (app (mock/request :get "/v2/location?address=paris&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:addresses body)]
      (verify-headers response json success-v2)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200, no errors, given location route with city,state"
    (let [response (app (mock/request :get "/v2/location?address=Atlanta,GA&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:addresses body)]
      (verify-headers response json success-v2)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location))))

(deftest ^:integration legacy-route-test
  (testing "Should return 200, no errors, given location route with valid, period separated geocode"
    (let [response (app (mock/request :get "/v3/location?geocode=33.89,-84.45&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          headers (:headers response)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 400, no errors, given location route with invalid, comma separated geocode"
    (let [response (app (mock/request :get "/v3/location?geocode=33,89,-84,45&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json error)
      (t/equal? 400 (:status response))
      (t/equal? "LOC:PVE-0005" (-> body :errors first :error :code))
      (t/equal? "Unsupported value(s): geocode" (-> body :errors first :error :message))))

  (testing "Should return 404, LOC:NFE-0001 error, given location route with valid geocode that returns no location"
    (let [response (app (mock/request :get "/v3/location?geocode=90.00,90.00&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err))))

  (testing "Should return 200, no errors, given location route with zip code"
    (let [response (app (mock/request :get "/v3/location?address=30339&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200, no errors, given location route with city name"
    (let [response (app (mock/request :get "/v3/location?address=paris&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200, no errors, given location route with city,state"
    (let [response (app (mock/request :get "/v3/location?address=Atlanta,GA&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location))))

(deftest ^:integration search-route-test
  (testing "Should return 200, no errors, given loc route with full postal address"
    (let [response (app (mock/request :get "/v3/location/search?query=300%20interstate%20North%20Parkway,%20Atlanta,%20GA,%2030339&locationType=address&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (is (vector? (:latitude location)))
      (t/equal? 200 (:status response))))

  (testing "Should return 200, no errors, given location route with city name"
    (let [response (app (mock/request :get "/v3/location/search?query=paris&locationType=city&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (is (vector? (:latitude location)))
      (t/equal? 200 (:status response))))

  (testing "Should return 200, no errors, given location route with city,state"
    (let [response (app (mock/request :get "/v3/location/search?query=Atlanta,GA&locationType=city&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (is (vector? (:latitude location)))
      (t/equal? 200 (:status response))))

  (testing "Should return 200, no errors, given city filter"
    (let [response (app (mock/request :get "/v3/location/search?query=atlanta&locationType=city&language=en-US&format=json&filter=city"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (is (vector? (:latitude location)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

 (testing "Should return 200, no errors, countryCode=US"
    (let [response (app (mock/request :get "/v3/location/search?query=atlanta&locationType=city&language=en-US&format=json&countryCode=US"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (is (vector? (:latitude location)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))
 
 (testing "Should return 200, no errors with only the locationTypes specified by the expansion of the locationType=locale which are: locality,neighborhood,district,place. Since the results that we received are already processed by location services, we do not assert mapbox results; instead, we know what the location services results should look like (from calling mapbox with the parameters above) and assert that."
   (let [response (app (mock/request :get "/v3/location/search?query=atlanta&locationType=locale&language=en-US&format=json&countryCode=US"))
         body (json/read-str (:body response) :key-fn keyword)
         location (:location body)]
     (verify-headers response json success)
     (is (empty? (:errors response)))
     (is (vector? (:latitude location)))
     (t/equal? "Atlanta, Georgia, United States" (-> location :address first))
     (t/equal? "Atlanta University Center, Atlanta, 30314, Georgia, United States" (-> location :address second))
     (t/equal? 200 (:status response))
     (t/not-nil? location)))
  
 (testing "Should return 400, PVE-0005, given invalid locationType"
   (let [response (app (mock/request :get "/v3/location/search?query=atlanta&language=en&format=json&locationType=invalid"))
         errors (json/read-str (:body response) :key-fn keyword)
         err (first (:errors errors))]
     (verify-headers response json error)
     (is (comp not empty? (:errors response)))
     (t/equal? 400 (:status response))
     (t/equal? "LOC:PVE-0005" (get-in err [:error :code]))
     (t/equal? "Unsupported value(s): locationType" (get-in err [:error :message]))))

 (testing "Should return 404, LOC:NFE-0001 error,given search returning no data"
   (let [response (app (mock/request :get "/v3/location/search?query=atlanta&locationType=city&language=en-US&format=json&countryCode=RU"))
         errors (json/read-str (:body response) :key-fn keyword)
         err (:error (first (:errors errors)))]
     (verify-headers response json error)
     (is (comp not empty? (:error response)))
     (t/equal? 404 (:status response))
     (t/equal? "LOC:NFE-0001" (:code err))))

 (testing "Should return 200, no errors given countryCode=US and adminDistrictCode=GA"
   (let [response (app (mock/request :get "/v3/location/search?query=atl&locationType=city&language=en-US&format=json&countryCode=US&adminDistrictCode=GA"))
         body (json/read-str (:body response) :key-fn keyword)
         location (:location body)]
     (verify-headers response json success)
     (is (empty? (:errors response)))
     (t/not-nil? (:latitude location))
     (t/equal? 200 (:status response))
     (t/not-nil? location)))

 (testing "Should return 404, LOC:NFE-0001, given countryCode=US and adminDistrictCode=HI"
   (let [response (app (mock/request :get "/v3/location/search?query=atl&locationType=city&language=en-US&format=json&countryCode=US&adminDistrictCode=HI"))
         errors (json/read-str (:body response) :key-fn keyword)
         err (:error (first (:errors errors)))]
     (verify-headers response json error)
     (is (comp not empty? (:error response)))
     (t/equal? 404 (:status response))
     (t/equal? "LOC:NFE-0001" (:code err))))
)

(deftest ^:integration ip-lookup-route-test
  (testing "Should return 200, no errors, given location route with IPv4 address"
    (let [response (app (mock/request :get "/v3/location/iplookup?ip=96.8.88.93&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (is (empty? (:errors response)))
      (verify-headers response json ip-success)
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 200, no errors, given location route with IPv6 address"
    (let [response (app (mock/request :get "/v3/location/iplookup?ip=2001:4860::1:0:87aa&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json ip-success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location))))

(comment
 (deftest polygon-route-test
  (testing "Should return 200, no errors, given valid params and pollen type"
    (let [response (app (mock/request :get "/v3/location/polygon?fields=pollen&geocode=33.89,-84.45&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          products (:polygons body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? (t/get-key "pollen" products))))

  (testing "Should return 200, no errors, given valid params and pollen_polygon types"
    (let [response (app (mock/request :get "/v3/location/polygon?fields=pollen_polygons&geocode=33.89,-84.45&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          products (:polygons body)]
      (verify-headers response json success)
      (t/equal? 200 (:status response))
      (is (empty? (:errors response)))
      (t/not-nil? (t/get-key "pollen" products))))

  (testing "Should return 200, no errors, given valid params and alias for all fields"
    (let [response (app (mock/request :get "/v3/location/polygon?fields=all&geocode=33.89,-84.45&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (t/equal? 200 (:status response))
      (is (empty? (:errors response)))))

  (testing "Should return 200, no errors, given valid params and zoom level of 10"
    (let [response (app (mock/request :get "/v3/location/polygon?fields=pollen_polygons&geocode=33.89,-84.45&language=en-US&format=json&zoom=10"))
          body (json/read-str (:body response) :key-fn keyword)
          products (:polygons body)]
      (verify-headers response json success)
      (t/equal? 200 (:status response))
      (is (empty? (:errors response)))
      (t/not-nil? (t/get-key "pollen" products))))

  (testing "Should return 400, PVE-0005, given valid params and invalid zoom level"
    (let [response (app (mock/request :get "/v3/location/polygon?fields=all&zoom=twc&geocode=33.89,-84.45&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 400 (:status response))
      (t/equal? "LOC:PVE-0005" (get-in err [:error :code]))))

  (testing "Should return 404, NFE-0001, given valid params and invalid field"
    (let [response (app (mock/request :get "/v3/location/polygon?fields=invalid&geocode=33.89,-84.45&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (get-in err [:error :code]))))))

(deftest geo-hit-test-route-test
  (testing "Should return 200, no errors, given valid geocode and product test"
    (let [response (app (mock/request :get "/v3/location/geohittest?geocode=33.89,-84.46&product=test&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (t/equal? 200 (:status response))
      (t/equal? "test" (:product body))
      (t/equal? "ATL" (:key body))))

  (testing "Should return 200, no errors, given valid geocode, product = alert, and esi format"
    (let [response (app (mock/request :get "/v3/location/geohittest?geocode=33.89,-84.46&product=test&language=en-US&format=esi"))]
      (verify-headers response xml success)
      (t/equal? 200 (:status response))
      (t/equal? "<esi:vars><esi:assign name=\"key\" value=\"'''ATL'''\" /><esi:assign name=\"product\" value=\"'''test'''\" /></esi:vars>"
                (:body response))))

  (testing "Should return 404, NFE-0001, given valid geocode and invalid product"
    (let [response (app (mock/request :get "/v3/location/geohittest?geocode=33.89,-84.46&product=invalid&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (get-in err [:error :code])))))

(deftest point-map-route-test
  (testing "Should return 200, no errors, given valid type and id"
    (let [response (app (mock/request :get "/v3/location/pointmap?type=icaoCode&id=KATL&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (t/equal? 200 (:status response))
      (t/equal? "33.6366996,-84.427864" (:geocode body))
      (t/equal? "icaoCode" (:type body))
      (t/equal? "KATL" (:id body))))

  (testing "Should return 404, NFE error, given valid type and invalid id"
    (let [response (app (mock/request :get "/v3/location/pointmap?type=icaoCode&id=K:US&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err))))

  (testing "Should return 200, no errors, given valid type and id and format = esi"
    (let [response (app (mock/request :get "/v3/location/pointmap?type=icaoCode&id=KATL&language=en-US&format=esi"))]
      (verify-headers response xml success)
      (t/equal? 200 (:status response))
      (t/equal? "<esi:vars><esi:assign name=\"geocode\" value=\"'''33.6366996,-84.427864'''\" /><esi:assign name=\"id\" value=\"'''KATL'''\" /><esi:assign name=\"type\" value=\"'''icaoCode'''\" /></esi:vars>"
                (:body response))))

  (testing "Should return 404, NFE-0001, given invalid type"
    (let [response (app (mock/request :get "/v3/location/pointmap?type=invalid&id=x&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (get-in err [:error :code])))))

(deftest resolve-route-test
  (testing "Should return 200, no errors, given valid product, type, and id."
    (let [response (app (mock/request :get "/v3/location/resolve?type=icaoCode&id=KATL&product=test&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (t/equal? 200 (:status response))
      (t/equal? "icaoCode" (:type body))
      (t/equal? "test" (:product body))
      (t/equal? "ATL" (:key body))
      (t/equal? "KATL" (:id body))))

  (testing "Should return 200, no errors and resolve near (point) product: airport for postakKey:30339:US"
    (let [response (app (mock/request :get "/v3/location/resolve?type=postalKey&id=30339:US&product=airport&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (true? (cfg/is-near-product? "airport")))
      (t/equal? 200 (:status response))
      (t/equal? "postalKey" (:type body))
      (t/equal? "airport" (:product body))
      (t/equal? "KMGE" (:key body))
      (t/equal? "30339:US" (:id body))))

  (testing "Should return 200, no errors and resolve near (point) product: pws for postakKey:30339:US"
    (let [response (app (mock/request :get "/v3/location/resolve?type=postalKey&id=30339:US&product=pws&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (true? (cfg/is-near-product? "pws")))
      (t/equal? 200 (:status response))
      (t/equal? "postalKey" (:type body))
      (t/equal? "pws" (:product body))
      (t/equal? "KGAVININ2" (:key body))
      (t/equal? "30339:US" (:id body)))) 

  (testing "Should return 200, no errors and resolve near (point) product: ski for postalKey:30339:US"
    (let [response (app (mock/request :get "/v3/location/resolve?type=postalKey&id=30339:US&product=ski&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (true? (cfg/is-near-product? "ski")))
      (t/equal? 200 (:status response))
      (t/equal? "postalKey" (:type body))
      (t/equal? "ski" (:product body))
      (t/equal? "347" (:key body))
      (t/equal? "30339:US" (:id body))))


  (testing "Should return 200, no errors and resolve non-near (polygon) product: zone for postalKey:30339:US"
    (let [response (app (mock/request :get "/v3/location/resolve?type=postalKey&id=30339:US&product=zone&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (false? (cfg/is-near-product? "zone")))
      (t/equal? 200 (:status response))
      (t/equal? "postalKey" (:type body))
      (t/equal? "zone" (:product body))
      (t/equal? "GAZ032" (:key body))
      (t/equal? "30339:US" (:id body))))

  (testing "Should return 200, no errors, given valid type, product, and id and format = esi"
    (let [response (app (mock/request :get "/v3/location/resolve?type=icaoCode&id=KATL&product=test&language=en-US&format=esi"))]
      (verify-headers response xml success)
      (t/equal? 200 (:status response))
      (t/equal? "<esi:vars><esi:assign name=\"key\" value=\"'''ATL'''\" /><esi:assign name=\"product\" value=\"'''test'''\" /><esi:assign name=\"id\" value=\"'''KATL'''\" /><esi:assign name=\"type\" value=\"'''icaoCode'''\" /></esi:vars>"
                (:body response))))


  (testing "Should return 404, LOC:NFE-0001, given valid product, type, and invalid id."
    (let [response (app (mock/request :get "/v3/location/resolve?type=icaoCode&id=invalid&product=test&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (get-in err [:error :code]))))

  (testing "Should return 404, NFE-0001, given invalid type"
    (let [response (app (mock/request :get "/v3/location/resolve?type=invalid&id=KATL&product=test&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (get-in err [:error :code]))))

  (testing "Should return 404, NFE-0001, given invalid product"
    (let [response (app (mock/request :get "/v3/location/resolve?type=icaoCode&id=KATL&product=invalid&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (first (:errors errors))]
      (verify-headers response json error)
      (is (comp not empty? (:errors response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (get-in err [:error :code])))))

(deftest ^:integration point-route-test
  (testing "Should return 200, no errors, given valid type and id"
    (let [response (app (mock/request :get "/v3/location/point?icaoCode=KATL&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)
          location (:location body)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? location)))

  (testing "Should return 404, NFE-0001 error, given valid type and invalid id"
    (let [response (app (mock/request :get "/v3/location/point?iataCode=invalid&language=en-US&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err)))))

(deftest geo-boundary-route-test
  (testing "Should return 200, no errors, given geocode as type, a valid geocode for id, and pollen product"
    (let [response (app (mock/request :get "/v3/location/boundary?geocode=33.89,-84.46&product=pollen&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? body)))

  (testing "Should return 404, NFE-0001 error, given invalid product and valid geocode"
    (let [response (app (mock/request :get "/v3/location/boundary?geocode=33.89,-84.46&product=invalid&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err))))

  (testing "Should return 404, NFE-0001 error, given valid product and geocode with no data"
    (let [response (app (mock/request :get "/v3/location/boundary?geocode=90.00,90.00&product=pollen&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err)))))

(deftest intersection-route-test
  (testing "Should return 200, no errors, given valid geocode, postalKey product, and pollen overlay"
    (let [response (app (mock/request :get "/v3/location/intersection?type=geocode&id=33.89,-84.46&product=postalKey&overlay=pollen"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? body)))

  (testing "Should return 200, no errors, given valid type countyId and valid id, postalKey product, and pollen overlay"
    (let [response (app (mock/request :get "/v3/location/intersection?type=countyId&id=GAC121&product=postalKey&overlay=pollen"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? body)))

  (testing "Should return 200, no errors, given valid type countyId and valid id, postalKey product, and pollen overlay"
    (let [response (app (mock/request :get "/v3/location/intersection?type=postalKey&id=30339:US&product=postalKey&overlay=pollen"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? body)))

  (testing "Should return 404, NFE-0001 error, given invalid product and valid geocode and valid overlay"
    (let [response (app (mock/request :get "/v3/location/intersection?type=geocode&id=33.89,-84.46&product=invalid&overlay=pollen"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err))))

  (testing "Should return 404, NFE-0001 error, given invalid overlay and valid geocode and valid product"
    (let [response (app (mock/request :get "/v3/location/intersection?type=geocode&id=33.89,-84.46&product=postalKey&overlay=invalid"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err))))

  (testing "Should return 404, NFE-0001 error, given valid product and geocode with no data"
    (let [response (app (mock/request :get "/v3/location/intersection?type=geocode&id=90.00,90.00&product=pollen&overlay=alerts"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err)))))

(deftest catalog-route-test
  (testing "Should return 200 and no errors when all required parameters are supplied and valid"
    (let [response (app (mock/request :get "/v3/location/catalog?format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? body))))

(deftest near-route-test
  (testing "Should return 200, no errors, given pws product and valid geocode"
    (let [response (app (mock/request :get "/v3/location/near?product=pws&geocode=33.89,-84.46&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:errors response)))
      (t/equal? 200 (:status response))
      (t/not-nil? body)))

  (testing "Should return 404, NFE error, given invalid product and valid geocode"
    (let [response (app (mock/request :get "/v3/location/near?product=invalid&type=geocode&id=33.89,-84.46&format=json"))
          errors (json/read-str (:body response) :key-fn keyword)
          err (:error (first (:errors errors)))]
      (verify-headers response json error)
      (is (comp not empty? (:error response)))
      (t/equal? 404 (:status response))
      (t/equal? "LOC:NFE-0001" (:code err)))))

(deftest search-by-zip
  (testing "City name should come from postalPlaces mapping file if not supplied by MapBox"
    (let [response (app (mock/request :get "/v3/location/search?query=10032&language=en-US&format=json"))
          body (json/read-str (:body response) :key-fn keyword)]
      (verify-headers response json success)
      (is (empty? (:error response)))
      (t/equal? 200 (:status response))
      (prn body)
      (t/not-nil? body))))
