(ns location.validation.core-test
  (:require [clojure.test             :refer :all]
            [location.utils.test      :as t]
            [location.validation.core :as v]))

(def ^:private valid-common-request "language=xx&format=x")
(def ^:private common-params #{:language :format})
(def ^:private valid-common-req-map {:params {:language "en-US"
                                              :format "json"}
                                     :query-string valid-common-request})
(def ^:private bad-common-req-map {:params {:not-lang "x"}})

;;  For null parameter tests
(def ^:private params-with-nil-map {:params {:geocode nil
                                             :language "en-us"
                                             :format "json"}})

(def ^:private params-with-multiple-nil-map {:params {:geocode nil
                                                      :language nil
                                                      :format "json"}})

(def ^:private params-without-nil-map {:params { :address 30076
                                                 :language "en-us"
                                                 :format "json"}})

(def ^:private param-without-nil-map {:params { :address 30076}})

(def ^:private param-with-nil-map {:params { :address nil}})

(def ^:private point-map-without-required-field {:params {:type "countyId"}
                                                 :lookup "point-map"})

(def ^:private point-map-with-required-field {:params {:type "countyId"
                                                       :format "json"
                                                       :id "008620:CA"}
                                              :lookup "point-map"})

(def ^:private hit-test-without-required-field {:params {:geocode "test"}
                                                :lookup "hit-test"})

(def ^:private hit-test-with-required-field {:params {:geocode "countyId"
                                                      :format "json"
                                                      :product "008620:CA"}
                                             :lookup "hit-test"})

(def ^:private resolve-without-required-field {:params {:geocode "test"}
                                               :lookup "resolve"})

(def ^:private resolve-with-required-field {:params { :type "countyId"
                                                      :format "json"
                                                      :id "008620:CA"
                                                      :product "008620:CA"}
                                            :lookup "resolve"})


(deftest validate-test
  (testing "Should return nil given a passing validation"
    (let [valid (v/validate #(when (odd? %) "invalid") identity 2)]
      (is nil? valid)))

  (testing "Should return invalid given odd value and identity function"
    (let [valid ((v/validate #(when (odd? %) "invalid") identity 1))]
      (t/equal? "invalid" valid))))

(deftest unsupported-value-test
  (testing "Should return nil given valid is a supported value"
    (let [unsupported (apply hash-map (v/unsupported-value ["valid"] :test "valid"))]
      (is (empty? unsupported))))

  (testing "Should return :test given an unsupported value invalid"
    (let [unsupported (v/unsupported-value ["valid"] :test "invalid")]
      (t/equal? :test unsupported))))

(deftest nil-values-test
  (testing "Should return nil if each of multiple parameters are populated"
    (let [nulls (v/null-values? params-without-nil-map)]
      (t/equal? nil nulls)))

  (testing "Should return nil if the only parameter is populated"
    (let [nulls (v/null-values? param-without-nil-map)]
      (t/equal? nil nulls)))

  (testing "Should return a vector when called with one parameter that is null"
    (let [nulls (v/null-values? param-with-nil-map)]
      (t/equal? nulls [:address])))

  (testing "Should return vector of null params when called with multiple parameters that are not null and one that is"
    (let [nulls (v/null-values? params-with-nil-map)]
      (t/equal?  nulls  [:geocode])))

  (testing "Should return vector of null params when called with multiple parameters that are null and one that is not"
    (let [nulls (v/null-values? params-with-multiple-nil-map)]
      (t/equal? nulls [:geocode :language])))

  (testing "Should return a comma dilimited string of the keyword seq passed in"
    (let [test-names [:language :format]]
      (t/equal? (v/null-vals-to-string test-names) "language, format")))

  (testing "Should return nil if nil or other falsey value is passed in"
      (t/equal? (v/null-vals-to-string nil) nil)))

(deftest required-field-test
  (testing "Should return a partial if required field is missing in point-map route"
    (let [res ((v/validate-required-fields point-map-without-required-field))]
      (is (seq res))))

  (testing "Should return nil if no required fields are missing in point-map route"
    (let [res (v/validate-required-fields point-map-with-required-field)]
      (is (nil? res))))

  (testing "Should return a partial if required field is missing in hit-test route"
    (let [res ((v/validate-required-fields hit-test-without-required-field))]
      (is (seq res))))

  (testing "Should return a partial if no required fields are missing in hit-test route"
    (let [res (v/validate-required-fields hit-test-with-required-field)]
      (is (nil? res))))

  (testing "Should return a partial if required field is missing in resolve route"
    (let [res ((v/validate-required-fields resolve-without-required-field))]
      (is (seq res))))

  (testing "Should return a partial if no required fields are missing in resolve route"
    (let [res (v/validate-required-fields resolve-with-required-field)]
      (is (nil? res)))))

(deftest valid-item-test
  (testing "Should return nil if valid item are passed in as item"
    (let [res (v/validate-item-value {:params {:item "product"}})]
      (is (nil? res))))

  (testing "Should return nil if valid items are passed in with comma separator"
    (let [res (v/validate-item-value {:params {:item "product;type"}})]
      (is (nil? res))))

  (testing "Should return a partial if invalid value is passed in as item"
    (let [res (v/validate-item-value {:params {:item "invalid"}})]
      (is (seq (res)))))

  (testing "Should return a partial if invalid values are passed in as comma separated items"
    (let [res (v/validate-item-value {:params {:item "type;invalid"}})]
      (is (seq (res))))))