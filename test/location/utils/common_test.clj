(ns location.utils.common-test
  (:require [clojure.test :refer :all]
            [location.utils.test   :as t]
            [location.utils.common :as util]))

(def ^:private test-json "{\"number\":1, \"string\":\"test\"}")
(def ^:private test-query "language=en-US&geocode=40.73,-74.98&format=json")

(def ^:private apply-nils #'location.utils.common/apply-nils)

(deftest url-encode-test
  (testing "Should replace spaces with '+' given 'test mock url'"
    (let [actual (util/url-encode "test mock url")
          expected "test+mock+url"]
      (t/equal? expected actual)))

  (testing "Should replace encoded spaces with '+' given 'test%20mock%url'"
    (let [actual (util/url-encode "test%20mock%20url")
          expected "test+mock+url"]
      (t/equal? expected actual)))

  (testing "Should leave string alone with no spaces given weather.com"
    (let [actual (util/url-encode "weather.com")
          expected "weather.com"]
      (t/equal? expected actual))))

(deftest apply-nils-test
  (testing "Should return [:key nil] give [:key]"
    (let [actual (apply-nils [:key])
          expected [:key nil]]
      (t/equal? expected actual)))

  (testing "Should return [:key \"val\"] give [:key \"val\"]"
    (let [actual (apply-nils [:key "val"])
          expected [:key "val"]]
      (t/equal? expected actual))))

(deftest query-to-map-test
  (testing "Should return a map when given a query string"
    (let [mapped (util/query-to-map test-query)]
      (t/equal? "en-US" (:language mapped))
      (t/equal? "40.73,-74.98" (:geocode mapped))
      (t/equal? "json" (:format mapped))
      (is (map? mapped)))))

(deftest query-to-value-pairs-test
  (testing "Should return a vector of pairs given a valid query string"
    (let [paired (util/query-to-value-pairs test-query)]
      (t/equal? [["language" "en-US"] ["geocode" "40.73,-74.98"] ["format" "json"]]     
             paired)
      (is (vector? paired)))))
 
(deftest clojurize-json-test
  (testing "Should return a map, keywordize json keys, and leave values alone given a json document"
    (let [clojurized (util/clojurize-json test-json)]
      (is (map? clojurized))
      (t/equal? [:number :string] (keys clojurized))
      (t/equal? [1 "test"] (vals clojurized)))))

(deftest nillable-inc
  (testing "Should insert {:blank 1} if key is not in map given a non-existing key."
    (let [actual (update-in {} [:blank] util/nillable-inc)
          expected 1]
      (t/equal? expected (:blank actual))))

  (testing "Should increment as usual if key is in map given existing key"
    (let [actual (update-in {:key 3} [:key] util/nillable-inc)
          expected 4]
      (t/equal? expected (:key actual)))))

(deftest not-nil-test
  (testing "Should return true given an non nil value"
    (let [actual (util/not-nil? "not nil")
          expected true]
      (t/equal? expected actual)))
  
  (testing "Should return false given nil value"
    (let [actual (util/not-nil? nil)
          expected false]
      (t/equal? expected actual))))

(deftest read-file-test
  (testing "Should return a file object given valid file"
    (let [actual (type (util/read-file "default-languages.json"))
          expected java.lang.String]
      (t/equal? expected actual)))

  (testing "Should return nil given invalid file"
    (let [actual (util/read-file "invalid")
          expected nil]
      (t/equal? expected actual))))

(deftest load-languages-test
  (testing "Should return a map of langauges given a valid lang file"
    (let [actual (util/load-languages "default-languages.json")]
     (is (map? actual)))) 

  (testing "Should return nil given an invalid lang file"
    (let [actual (util/load-languages "invalid")
          expected nil]
     (t/equal? expected actual)))) 

(deftest get-language-test
  (testing "Should return en-US given en-US"
    (let [actual (util/get-language "en-us")
          expected "en-US"]
      (t/equal? expected actual)))
  
  (testing "Should return en-US given en-TWC"
    (let [actual (util/get-language "en-TWC")
          expected "en-US"]
      (t/equal? expected actual)))
  
  (testing "Should return nil given tlh-TWC"
    (let [actual (util/get-language "tlh-TWC")
          expected nil]
      (t/equal? expected actual))))

(deftest nil-or-empty-test
  (testing "Should return not-nil given not-nil"
    (let [actual (util/nil-or-empty? "not-nil")
          expected "not-nil"]
      (t/equal? expected actual)))

  (testing "Should return nil given nil"
    (let [actual (util/nil-or-empty? nil)
          expected nil]
      (t/equal? expected actual)))

  (testing "Should return nil given an empty string"
    (let [actual (util/nil-or-empty? "")
          expected nil]
      (t/equal? expected actual))))

(deftest flatten-single-val-test
  (testing "Should return the single value ATL given [\"ATL\"]"
    (let [actual (util/flatten-single-val ["ATL"])
          expected "ATL"]
      (t/equal? expected actual)))

  (testing "Should return the collection [\"ATL\" \"NYC\"] given that [\"ATL\" \"NYC\"]"
    (let [actual (util/flatten-single-val ["ATL" "NYC"])
          expected ["ATL" "NYC"]]
      (t/equal? expected actual))))

(deftest parse-number-test
  (testing "Given a parsable string, returns the number."
    (let [actual-int (util/parse-number "23")
          actual-decimal (util/parse-number "25.1234")
          actual-string (util/parse-number "not a num 55 1234")
          actual-other (util/parse-number {:a 1 :b 2})
          expected-int 23
          expected-deimal 25.1234
          expected-string "not a num 55 1234"
          expected-other {:a 1 :b 2}]
      (t/equal? actual-int expected-int)
      (t/equal? actual-decimal expected-deimal)
      (t/equal? actual-string expected-string)
      (t/equal? actual-other expected-other))))

(deftest split-strip-conf-test
  (testing "Should return seq (\"hello\" \"world\" when given the string \"hello , world\""
    (let [actual-ws (util/split-strip-conf "\"hello , world\"")
          actual    (util/split-strip-conf "hello,world")]
      (t/equal? actual-ws (seq ["hello" "world"]))
      (t/equal? actual (seq ["hello" "world"])))))

(deftest name-case-tests
  (testing "Testing converstion TO camel case using ->camel"
    (t/equal? (util/->camel "this-is-from-kebab") "thisIsFromKebab")
    (t/equal? (util/->camel "this_is_from_snake") "thisIsFromSnake")
    (t/equal? (util/->camel "custom+case+here" \+) "customCaseHere")
    (t/equal? (util/->camel "custom+case*here" \+ \*) "customCaseHere")
    (t/equal? (util/->camel "test&no&change" \+ \*) "test&no&change")
    (t/equal? (util/->camel "CapitalTest_now_here-we-*go" \* \- \_) "capitaltestNowHereWeGo")
    (t/equal? (util/->camel "__Leading_trailing_underscore_") "leadingTrailingUnderscore"))
  
  (testing "Testing converstion FROM camel case using camel->"
    (t/equal? (util/camel-> "thisIsCamelCase" \-) "this-is-camel-case")
    (t/equal? (util/camel-> "thisIsCamelCase" "--=+=--") "this--=+=--is--=+=--camel--=+=--case")
    (t/equal? (util/camel-> "thisIs.Camel_*!Case" \&) "this&is.&camel_*!&case")
    (t/equal? (util/camel-> "!@#$%^&*(" "") "!@#$%^&*(")
    (t/equal? (util/camel-> "BBBAAA**zA389@#^XyzAZZbc35BadVoodoo" "!!!")
              "b!!!b!!!b!!!a!!!a!!!a**z!!!a389@#^!!!xyz!!!a!!!z!!!zbc35!!!bad!!!voodoo")
    (t/equal? (util/camel-> "CapLetterStart88Now!!Yes" \_) "cap_letter_start88_now!!_yes")
    (t/equal? (util/camel-> "someString" "") "someString")
    (t/equal? (util/camel-> "someString" nil) "someString"))

  ; the below test are wrappers for ->camel and camel-> so not as extensive
  (testing "Testing snake and kebab with camel"
    (t/equal? (util/snake-to-camel "Some_snake_test") "someSnakeTest")
    (t/equal? (util/kebab-to-camel "some-kebab-test-a-b-c") "someKebabTestABC")
    (t/equal? (util/camel-to-snake "someSnakeTest") "some_snake_test")
    (t/equal? (util/camel-to-kebab "someKebabTestABC") "some-kebab-test-a-b-c"))
  
  (testing "Testing snake and kebab with each other"
    (t/equal? (util/snake-to-kebab "___snake___to_kebab_TEst__") "snake-to-kebab-test")
    (t/equal? (util/snake-to-kebab "snake_to_hyphenated-word-long_test") "snake-to-hyphenated-word-long-test")
    (t/equal? (util/kebab-to-snake "---kebab-test-with---hyphens--") "kebab_test_with_hyphens")
    (t/equal? (util/kebab-to-snake "kebab-regular-with-some_underscores-here") "kebab_regular_with_some_underscores_here"))
  
  (testing "Pascal tests.  ->pascal is identical to ->camel but with uppercase.  Others are
            direct calls to camel functions so only testing that they exist."
    (t/equal? (util/->pascal "this-is-from-kebab") "ThisIsFromKebab")
    (t/equal? (util/->pascal "this_is_from_snake") "ThisIsFromSnake")
    (t/equal? (util/->pascal "custom+case+here" \+) "CustomCaseHere")
    (t/equal? (util/->pascal "custom+case*here" \+ \*) "CustomCaseHere")
    (t/equal? (util/->pascal "test&no&change" \+ \*) "test&no&change")
    (t/equal? (util/->pascal "CapitalTest_now_here-we-*go" \* \- \_) "CapitaltestNowHereWeGo")
    (t/equal? (util/->pascal "__Leading_trailing_underscore_") "LeadingTrailingUnderscore")
    (t/equal? (util/pascal-> "CapLetterStart88Now!!Yes" \_) "cap_letter_start88_now!!_yes"))

  (testing "Testing snake and kebab with pascal"
    (t/equal? (util/snake-to-pascal "Some_snake_test") "SomeSnakeTest")
    (t/equal? (util/kebab-to-pascal "some-kebab-test-a-b-c") "SomeKebabTestABC")
    (t/equal? (util/pascal-to-snake "SomeSnakeTest") "some_snake_test")
    (t/equal? (util/pascal-to-kebab "SomeKebabTestABC") "some-kebab-test-a-b-c")))
