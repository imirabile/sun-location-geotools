(ns location.macros-test
  (:use [location.macros])
  (:require [clojure.test :refer :all]
            [location.utils.test :as t]))

(deftest nil->test
  (testing "Should return nil given a map and several functions which don't return values."
    (let [actual (nil-> {:a 2} :b :c :d)
          expected nil]
      (t/equal? expected actual)))

  (testing "Should return 2 given a map and :a :b :c functions."
    (let [actual (nil-> {:a 2} :a :b :c)
          expected 2]                                                                              
      (t/equal? expected actual)))

  (testing "Should return 2 given a map and :b :c :a :d functions."
    (let [actual (nil-> {:a 2} :b :c :a :d)
          expected 2]
      (t/equal? expected actual)))

  (testing "Should return 2, not 3, and given a map and :a inc functions."
    (let [actual (nil-> {:a 2} :a inc)
          expected 2]
      (t/equal? expected actual))))

(deftest nil->>test
  (testing "Should return nil given a map and several functions which don't return values."
    (let [actual (nil->> {:a 2} :b :c :d)
          expected nil]
      (t/equal? expected actual)))

  (testing "Should return 2 given a map and :a :b :c functions."
    (let [actual (nil->> "test" (str "nil") :b :c)
          expected "niltest"] 
      (t/equal? expected actual)))

  (testing "Should return 2 given a map and :b :c :a :d functions."
    (let [actual (nil->> "test" (str "nil") :c (str "nil2") :d)
          expected "niltest"]
      (t/equal? expected actual)))

  (testing "Should return 2, not 3, and given a map and :a inc functions."
    (let [actual (nil->> {:a 2} :a inc)
          expected 2]
      (t/equal? expected actual))))

(deftest conj->test
  (testing "Should return [1 2] given 1 and identity and *2 forms"
    (let [actual (conj-> 1 [] identity (* 2))
          expected [1 2]]
      (t/equal? expected actual))))

(deftest conj->>test
  (testing "Should return [1 2] given 1 and identity and *2 forms"
    (let [actual (conj->> 1 [] identity (* 2))
          expected [1 2]]
      (t/equal? expected actual))))
