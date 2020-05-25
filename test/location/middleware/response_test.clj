(ns location.middleware.response-test
  (:require [clojure.test                 :refer :all]
            [ring.mock.request            :as req]
            [location.utils.test          :as t]
            [location.middleware.response :as r]))

(deftest extract-error-codes-test
  (testing "Should return 400 given a single document with 400 status"
    (let [status (r/extract-error-codes [(with-meta {:code 1} {:status 400})])]
      (t/equal? 400 (first status))
      (t/equal? 1 (count status))))
  
  (testing "Should return an empty set given no document with no errors"
    (let [status (r/extract-error-codes [])]
      (is (empty? status))
      (is (nil? (first status)))))

  (testing "Should return a set of 400 and 404 given documents with those statuses"
    (let [status (r/extract-error-codes [(with-meta {:code 1} {:status 400})
                                         (with-meta {:code 2} {:status 404})])]
      (t/equal? #{400 404} status))))

(deftest process-status-test
  (testing "Should return response with status 400 given body containing 400 error"
    (let [resp (r/process-status {:body {:errors [(with-meta {:code 1} {:status 400})]}})]
      (t/equal? 400 (:status resp))))
  
  (testing "Should return response with 'first' status given body with multiple errors"
    (let [resp (r/process-status {:body {:errors [(with-meta {:code 1} {:status 400})
                                                  (with-meta {:code 2} {:status 404})]}})]
      (is (or (= 400 (:status resp)) (= 404 (:status resp))))))
  
  (testing "Should return response with no status given body with no errors"
    (let [resp (r/process-status {:body {}})]
      (is (nil? (:status resp))))))

(deftest expand-lookup-value-test
  (testing "Should return a metadata map with lat and long given a map with geocode"
    (let [actual (r/expand-lookup-value {:geocode "33.89,-84.46"})]
      (t/equal? "33.89" (:latitude actual))
      (t/equal? "-84.46" (:longitude actual))))
  
  (testing "Should return an unchanged metadata map given a map without a geocode"
    (let [actual (r/expand-lookup-value {:test "test"})
          expected {:test "test"}]
      (t/equal? expected actual))))

(deftest base-response-metadata-test
  (testing "Should return a map with basic metadata fields given status 200"
    (let [actual (r/base-response-metadata 200 {:headers {"transaction-id" "test"} :query-string "a=1&b=2" :uri "/v3/search"})]
      (t/equal? "test" (:transaction_id actual))
      (t/equal? 200 (:status_code actual)))))

(deftest success-metadata-test
  (testing "Should return a metadata map with version and params given those values"
    (let [actual (r/success-metadata "a=1&b=2")]
      (t/equal? (:a actual) "1")
      (t/equal? (:b actual) "2"))))

(deftest generate-metadata-test

  (testing "Should return base + success metadata given a response with status 200 and version of v2"
    (let [actual (r/generate-metadata {:query-string "address=30309&language=en-US" :uri "/v2/location"}
                                      {:status 200})]
      (t/equal? 200 (:status_code actual))
      (t/equal? "30309" (:address actual))
      (t/equal? "en-US" (:language actual))
      (t/equal? (:version actual) "v2")
      (t/equal? 86400 (:total_cache_time_secs actual))
      (t/not-nil? (:generated_time actual))))

  (testing "Should return base + success metadata given a response with status 200 and version of v3"
    (let [actual (r/generate-metadata {:query-string "address=30309&language=en-US" :uri "/v3/location"}
                                      {:status 200})]
      (t/equal? 200 (:status_code actual))
      (t/equal? "30309" (:address actual))
      (t/equal? "en-US" (:language actual))
      (t/equal? (:version actual) "v3")
      (t/equal? 86400 (:total_cache_time_secs actual))
      (t/not-nil? (:generated_time actual))))

  
  (testing "Should return only base given a response with status != 200"
    (let [actual (r/generate-metadata {:query-string "address=30309&language=en-US&a=1&b=2" :uri "/v3/location"}
                                      {:status 408})]
      (t/equal? 408 (:status_code actual))
      (is (nil? (:a actual)))
      (is (nil? (:b actual))))))


