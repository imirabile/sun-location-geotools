(ns location.kml-test
  (:require [clojure.test         :refer :all]
            [location.kml         :as kml]
            [location.utils.test  :as t]))

(def ^:private testing-features [{:type "Feature" :geometry {:type "Polygon"
                                                             :coordinates [ [[1 2] [3 4]]
                                                                           [[2 4] [5 6]] ]}
                                  :properties {:product "test" :key "test-key"}}
                                 {:type "Feature" :geometry {:type "Polygon"
                                                             :coordinates [ [[7 8] [9 10]]
                                                                           [[11 14] [15 16]] ]}
                                  :properties {:product "test" :key "test-key"}}])

(deftest format-attr-test
  (testing "Should return ' key=\"val\"' given a vector pair of key and val"
    (let [actual (kml/format-attr ["key" "val"])]
      (t/equal? actual " key=\"val\"")))

  (testing "Should return nil if key or value is missing"
    (let [actual-nil-val (kml/format-attr ["key" nil])
          actual-nil-key (kml/format-attr [nil "val"])
          actual-nil-both (kml/format-attr [nil nil])
          actual-nil (kml/format-attr nil)]
      (t/equal? actual-nil-val nil)
      (t/equal? actual-nil-key nil)
      (t/equal? actual-nil-both nil)
      (t/equal? actual-nil nil))))

(deftest format-attrs-test
  (testing "Should return space separated values for attributes when given a collection of vector pairs or a mpa"
    (let [actual-vec-pairs (kml/format-attrs [["test" "value"] ["another" "test"]])
          actual-map (kml/format-attrs {:test "value" :another "test"})
          expected " test=\"value\" another=\"test\""]
      (t/equal? expected actual-vec-pairs)
      (t/equal? expected actual-map))))

(deftest tag-test
  (testing "Should return a <kml></kml> when not given any contents or attributes"
    (let [actual (kml/tag "kml" nil nil)]
      (t/equal? actual "<kml></kml>")))

  (testing "Should return a <kml id=\"test\"></kml> when given attributes and not given any contents"
    (let [actual (kml/tag "kml" {:id "test"} nil)]
      (t/equal? actual "<kml id=\"test\"></kml>")))

  (testing "Should return a <kml id=\"test\">content</kml> when given attributes given contents"
    (let [actual (kml/tag "kml" {:id "test"} "content")]
      (t/equal? actual "<kml id=\"test\">content</kml>")))

  (testing "Should return nil when a tag name is not supplied"
    (let [actual (kml/tag nil {:id "test"} "content")]
      (t/equal? actual nil))))

(deftest data-tag-test
  (testing "Should return a data tag with a name attribute and a nested value tag."
    (let [actual (kml/data-tag ["testKey" "test123"])]
      (t/equal? actual "<Data name=\"testKey\"><value>test123</value></Data>")))

  (testing "Should return empty data tag with nil params"
    (let [actual (kml/data-tag [nil "test123"])]
      (t/equal? actual nil))))

(deftest make-data-tags-test
  (testing "Should return set of data tags given a map with a properties entry"
    (let [actual (kml/make-data-tags {:properties {:test "first test" :another "another test"}})]
      (t/equal? actual "<Data name=\"test\"><value>first test</value></Data><Data name=\"another\"><value>another test</value></Data>")))

  (testing "Should return nil when there are is no :properties in the map"
    (let [actual (kml/make-data-tags {:properties nil})
          actual-no-prop (kml/make-data-tags {})]
      (t/equal? actual nil))))

(deftest format-coordinates-tag-test
  (testing "Should return a <LinearRing> tag with a coordinates tag of comma dilimited lat,lon pairs given vector pairs of coordinates"
    (let [actual (kml/format-coordinates-tag [[1 2] [3 4]]) ]
      (t/equal? actual "<LinearRing><coordinates>1,2 3,4</coordinates></LinearRing>")))

  (testing "Should return nil when passed in nil or empty collection"
    (let [actual-nil (kml/format-coordinates-tag nil)
          actual-empty (kml/format-coordinates-tag [])]
      (t/equal? actual-nil nil)
      (t/equal? actual-empty nil))))

(deftest format-coordinates-test
  (testing "Should return only <outerBoundaryIs> with formatted coordinates tag when given map with one collection of vector paris in the property"
    (let [actual (kml/format-coordinates [ [[1 2] [3 4]] ] )]
      (is (seq (re-seq #"outerBoundaryIs" actual)))
      (is (seq (re-seq #"LinearRing" actual)))
      (is (seq (re-seq #"coordinates" actual)))
      (is (not (seq (re-seq #"innerBoundaryIs" actual))))))

  (testing "Should return <outerBoundaryIs> with formatted coordinates tag and <innerBoudnaryIs> when given map with multiple collections of vector paris in the property"
    (let [actual (kml/format-coordinates [ [[1 2] [3 4]]
                                          [[5 6] [7 8]] ]) ]
      (is (seq (re-seq #"outerBoundaryIs" actual)))
      (is (seq (re-seq #"LinearRing" actual)))
      (is (seq (re-seq #"coordinates" actual)))
      (is (seq (re-seq #"innerBoundaryIs" actual)))))

  (testing "Should return nil when passed in an empty collection or nil"
    (let [actual-empty (kml/format-coordinates [] )
          actual-nil (kml/format-coordinates nil )]
      (is (nil? actual-empty))
      (is (nil? actual-nil)))))

(deftest make-shape-tags-test
  (testing "Should return Polygon tag when given geojson geometry with Polygon as type property and coordinates"
    (let [actual (kml/make-shape-tags {:geometry {:type "Polygon"
                                                  :coordinates [[[1 2] [3 4]]] }}) ]
      (is (seq (re-seq #"Polygon" actual)))))

  (testing "Should return Polygon tag when given geojson geometry with Polygon as type property and coordinates"
    (let [actual (kml/make-shape-tags {:geometry nil}) ]
      (is (nil? actual)))))

(deftest add-body-test
  (testing "Should return nested XML with two Polygon tags, two Placemark tags, and four LinearRing tags given a vector with two features, each feature having inner and outer boundaries"
    (let [actual (kml/add-body testing-features)]
      (t/equal? 2 (count (re-seq #"<Polygon>" actual)))
      (t/equal? 2 (count (re-seq #"<Placemark>" actual)))
      (t/equal? 4 (count (re-seq #"<LinearRing>" actual)))))

  (testing "Should return nested XML with one Polygon tag, one Placemark tag, and two LinearRing tags given a vector with one feature having inner and outer boundaries"
    (let [actual (kml/add-body [(first testing-features)])]
      (t/equal? 1 (count (re-seq #"<Polygon>" actual)))
      (t/equal? 1 (count (re-seq #"<Placemark>" actual)))
      (t/equal? 2 (count (re-seq #"<LinearRing>" actual))))))