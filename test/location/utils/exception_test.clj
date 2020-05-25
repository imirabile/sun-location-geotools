(ns location.utils.exception-test
  (:require [clojure.test             :refer :all]
            [location.error-message   :as e]
            [location.utils.exception :as ex]
            [location.utils.test      :as t]))

(deftest process-errors-test
  (testing "Should return an error document given a not found error message"
    (let [actual (ex/process-errors [(partial e/get-error :not-found)])]
      (t/equal? 404 (:status actual))
      (t/not-nil? (get-in actual [:body :errors])))))
