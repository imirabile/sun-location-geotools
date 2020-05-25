(ns location.esi-test
  (:require [clojure.test        :refer :all]
            [location.utils.test :as t]
            [location.esi        :as esi]))

(def ^:private esi-str-encode #'esi/esi-str-encode)
(def ^:private esi-coll-encode #'esi/esi-coll-encode)

(deftest esi-str-encode-test
  (testing "Should return \"test\" given test and \""
    (let [actual (esi-str-encode "test" \")
          expected "\"test\""]
      (t/equal? expected actual)))

  (testing "Should return \"'''test'''\" given test and ''' and \""
    (let [actual (esi-str-encode "test" "'''" \")
          expected "\"'''test'''\""]
      (t/equal? expected actual)))

  (testing "Should return test given test no quotes"
    (let [actual (esi-str-encode "test")
          expected "test"]
      (t/equal? expected actual))))

(deftest esi-coll-encode-test
  (testing "Should return \"['''test''']\" given test and \""
    (let [actual (esi-coll-encode ["test"])
          expected "['''test''']"]
      (t/equal? expected actual))))

(deftest vars-test
  (testing "Should return an esi vars block given test"
    (let [actual (esi/vars "test")
          expected "<esi:vars>test</esi:vars>"]
      (t/equal? expected actual)))
  
  (testing "Should return an esi vars block given ['a', 'b']"
    (let [actual (esi/vars ["a" "b"])
          expected "<esi:vars>ab</esi:vars>"]
      (t/equal? expected actual))))

(deftest assign-test
  (testing "Should return an esi assign block given test and val"
    (let [actual (esi/assign "test" "val")
          expected "<esi:assign name=\"test\" value=\"'''val'''\" />"]
      (t/equal? expected actual)))
  
  (testing "Should return an esi block given test and ['a', 'b']"
    (let [actual (esi/assign "test" (seq ["a" "b"]))
          expected "<esi:assign name=\"test\" value=\"['''a''', '''b''']\" />"]
      (t/equal? expected actual))))

(deftest encode-test
  (testing "Should return a full esi doc given {:test \"a\"}"
    (let [actual (esi/encode {:test "a"})
          expected "<esi:vars><esi:assign name=\"test\" value=\"'''a'''\" /></esi:vars>"]
      (t/equal? expected actual)))

  (testing "Should return a full esi doc given {:test \"a\" :key \"b\"}"
    (let [actual (esi/encode {:test "a" :key "b"})
          expected "<esi:vars><esi:assign name=\"key\" value=\"'''b'''\" /><esi:assign name=\"test\" value=\"'''a'''\" /></esi:vars>"]
      (t/equal? expected actual))))
