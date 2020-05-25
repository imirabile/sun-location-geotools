(ns location.error-message-test
  (:require [clojure.test           :refer :all]
            [location.utils.test    :as t]
            [location.error-message :as e]))

(deftest unsupported-value-test
  (testing "Should return 400 status, LOC:PVE-0005 error code, and message"
    (let [error (e/get-error :unsupported-values "test")
          error-body (:error error)]
      (t/in-message? #"test" (:message error-body))
      (t/in-message? #"Unsupported" (:message error-body))
      (t/equal? "LOC:PVE-0005" (:code error-body))
      (t/equal? 400 (:status (meta error))))))

(deftest null-value-test
  (testing "Should return 400 status, LOC:NUL-0001 error code, and message"
    (let [error (e/get-error :null-parameter "test")
          error-body (:error error)]
      (t/in-message? #"test" (:message error-body))
      (t/in-message? #"values:" (:message error-body))
      (t/equal? "LOC:NUL-0001" (:code error-body))
      (t/equal? 400 (:status (meta error))))))

(deftest missing-field-test
  (testing "Should return 400 status, LOC:MIF-0006 error code, and message"
    (let [error (e/get-error :missing-field "test")
          error-body (:error error)]
      (t/in-message? #"test" (:message error-body))
      (t/in-message? #"missing" (:message error-body))
      (t/equal? "LOC:MIF-0006" (:code error-body))
      (t/equal? 400 (:status (meta error))))))

(deftest bad-ip-address-test
  (testing "Should return 400 status, LOC:IIF-0001 error code, and message"
    (let [error (e/get-error :bad-ip-address)
          error-body (:error error)]
      (t/in-message? #"Invalid IP address" (:message error-body))
      (t/equal? "LOC:IIF-0001" (:code error-body))
      (t/equal? 400 (:status (meta error))))))

(deftest server-error-test
  (testing "Should return 500 status, RUE-0001 error, and a message"
    (let [error (e/get-error :server-error)
          error-body (:error error)]
      (t/not-nil? (:message error-body))
      (t/equal? "LOC:RUE-0001" (:code error-body))
      (t/equal? 500 (:status (meta error))))))

(deftest not-found-test
  (testing "Should return 404 status, and a message"
    (let [error (e/get-error :not-found)
          error-body (:error error)]
      (t/not-nil? (:message error-body))
      (t/equal? "LOC:NFE-0001" (:code error-body))
      (t/equal? 404 (:status (meta error))))))

(deftest timeout-test
  (testing "Should return 408 status, TME-0001 error, and a message"
    (let [error (e/get-error :timeout)
          error-body (:error error)]
      (t/not-nil? (:message error-body))
      (t/equal? "LOC:TME-0001" (:code error-body))
      (t/equal? 408 (:status (meta error))))))

(deftest provider-error-test
  (testing "Should return 503 status, LOC:PVE-0001 error, and a message"
    (let [error (e/get-error :provider-error)
          error-body (:error error)]
      (t/not-nil? (:message error-body))
      (t/equal? "LOC:PVE-0001" (:code error-body))
      (t/equal? 503 (:status (meta error))))))