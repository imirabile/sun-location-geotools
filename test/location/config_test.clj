(ns location.config-test
  (:require [clojure.test        :refer :all]
            [clojure.java.io     :as io]
            [location.utils.test :as t]
            [location.config     :as cfg]))

(def ^:private prep-conf-for-read #'cfg/prep-conf-for-read)
(def ^:private split-conf-line #'cfg/split-conf-line)
(def ^:private split-config #'cfg/split-config)
(def ^:private get-config #'cfg/get-config)
(def ^:private get-config-group #'cfg/get-config-group)

(deftest prep-conf-for-read-test
  (testing "Should return test given a newly created file containing 'test'"
    (let [file (t/create-test-file "test" ".txt")
          actual (prep-conf-for-read file io/file)
          expected "test"]
      (t/equal? expected actual)
      (t/remove-test-file file)))
  
  (testing "Should return nil given an non existing file"
    (let [actual (prep-conf-for-read "not-real.nr" io/file)
          expected nil]
      (t/equal? expected actual))))

(deftest split-conf-line-test
  (testing "Should return [key value] given key=value"
    (let [actual (split-conf-line "key=value")
          expected ["key" "value"]]
      (t/equal? expected actual)))
  
  (testing "Should return nil given an empty line."
    (let [actual (split-conf-line "")
          expected nil]
      (t/equal? expected actual)))
  
  (testing "Should return nil if input can't be split on an =."
    (let [actual (split-conf-line "key&value")
          expected nil]
      (t/equal? expected actual))))

(deftest split-config-test
  (testing "Should return a seq of vector pairs given a file with multiple key=value pairs."
    (let [file (t/create-test-file "test=true\nnot-test=false" ".conf")
          prepped (prep-conf-for-read file io/file)
          actual (split-config prepped)
          expected (seq [["test" "true"] ["not-test" "false"]])]
      (t/equal? expected actual)
      (t/remove-test-file file)))
  
  (testing "Should return an empty seq given a file with commented config."
    (let [file (t/create-test-file "#test=true" ".conf")
          prepped (prep-conf-for-read file io/file)
          actual (split-config prepped)
          expected ()]
      (t/equal? expected actual)
      (t/remove-test-file file))))

(deftest parse-conf-test
  (testing "Should return {:a \"test\"} given a generated file."
    (let [file (t/create-test-file "a=\"test\"" ".conf")
          actual (cfg/parse-conf file io/file)
          expected {'a ["test"]}]
      (t/equal? expected actual)
      (t/remove-test-file file)))

  (testing "Should return nil given an invalid file."
    (let [actual (cfg/parse-conf "file" io/file)
          expected nil]
      (t/equal? expected actual))))

(deftest get-config-files
  (testing "Should return 'dev.conf' given the devops.config.path config variable."
    (let [actual (cfg/get-config-files (cfg/get-config-first "devops.config.path"))
          expected (seq ["etc/dev.conf"])]
      (t/equal? expected actual)))

  (testing "Should return an empty seq given a path with no conf files."
    (let [actual (cfg/get-config-files "dev-resources")
          expected ()]
      (t/equal? expected actual)))

  (testing "Should return an empty seq given a non-existing path."
    (let [actual (cfg/get-config-files "not-real/")
          expected ()]
      (t/equal? expected actual))))

(deftest load-config-test
  (testing "Should return a map of config settings."
    (let [actual (cfg/load-config)]
      (t/not-nil? actual)
      (is (map? actual)))))

(deftest get-config-test
  (testing "Should return ls given service name config"
    (let [actual (get-config "port")
          expected 8080]
      (t/equal? expected actual)))

  (testing "Should return nil given not real config"
    (let [actual (get-config "not-real")
          expected nil]
      (t/equal? expected actual))))

(deftest get-config-group-test
  (testing "Should return a seq of settings given default group."
    (let [actual (get-config-group "default")]
      (t/not-nil? actual)
      (is (seq? actual))))

  (testing "Should return an empty seq of settings given not real group."
    (let [actual (get-config-group "not-real")]
      (is (empty? actual))
      (is (seq? actual)))))

(deftest get-provider-test
  (testing "Should return mapbox provider given any provider other than 'ip' or 'geocoder-v2'"
    (let [actual (cfg/get-provider "geocode")
          expected "location.providers.mapbox"]
      (t/equal? expected actual)))

  (testing "Should return neustar provider given ip provider"
    (let [actual (cfg/get-provider "ip")
          expected "location.providers.neustar"]
      (t/equal? expected actual))))

(deftest expand-error-entries-test
  (testing "Should return {:code \"LOC:MIF-0006\"} when given [\"ERRORCODE_missing-field_code\"]"
    (let [actual (cfg/expand-err-entries ["error-code.missing-field.code"])
          expected {:code "LOC:MIF-0006"}]
      (is map? actual)
      (t/equal? actual expected)))

  (testing "Should return {:code \"LOC:MIF-0006\"} when given [\"ERRORCODE_missing-field_code\"]"
    (let [actual (cfg/expand-err-entries ["error-code.fail.code"])
          expected {:code nil}]
      (is map? actual)
      (t/equal? actual expected))))

(deftest config-to-keyword-test
  (testing "Should return a keyword :TEST when given MY_TEST_VAR and second as accessor function."
    (let [actual (cfg/config-to-keyword 'MY.TEST.VAR second)]
      (t/equal? actual :TEST)))

  (testing "Should return a keyword :TEST_VAR when given TEST_VAR and no accessor function."
    (let [actual (cfg/config-to-keyword 'MY.TEST.VAR)]
      (t/equal? actual :MY.TEST.VAR))))

(deftest get-config-errortest
  (testing "Should return ls given service name config"
    (let [errors cfg/error-codes
          missing-field (:missing-field errors)
          provider-error (:provider-error errors)
          unavailable (:unavailable errors)
          timeout (:timeout errors)
          not-found (:not-found errors)
          bad-ip-address (:bad-ip-address errors)
          unsupported-values (:unsupported-values errors)
          default (:default errors)
          null-parameter (:null-parameter errors)]
      (t/equal? (:code missing-field) "LOC:MIF-0006")
      (t/equal? (:code provider-error) "LOC:PVE-0001")
      (t/equal? (:code unavailable) "LOC:UNV-0001")
      (t/equal? (:code timeout) "LOC:TME-0001")
      (t/equal? (:code not-found) "LOC:NFE-0001")
      (t/equal? (:code bad-ip-address) "LOC:IIF-0001")
      (t/equal? (:code unsupported-values) "LOC:PVE-0005")
      (t/equal? (:code default) "LOC:RUE-0001")
      (t/equal? (:code null-parameter) "LOC:NUL-0001"))))
