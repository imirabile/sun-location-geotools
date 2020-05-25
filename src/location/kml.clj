(ns location.kml
  (:require [clojure.string    :as str]
            [location.config   :as cfg]))


(def top-header (str "<?xml version=\"" (cfg/get-config-first "responseData.kml.xmlVersion") "\" encoding=\"" (cfg/get-config-first "responseData.kml.xmlEncoding") "\"?>"))

(defn format-attr
  "Formats a vector pair of key and value for the attribute to put inside of an opening tag. [key val] -> key=\"val\""
  [[key val]]
  (when (and key val)
    (str " " (name key) "=\"" val "\"")))

(defn format-attrs
  "Takes a collection of vector pairs (or preferably just a map) and returns a string of space separated attribute values for XML/HTML tags"
  [coll]
  (str/join (map format-attr coll)))

(defn tag
  "Returns an opening and closing tag with the specified name and attributes, and puts the content between the tags"
  [name attrs content]
  (when name
    (str "<" name (format-attrs attrs) ">" content "</" name ">")))

; Pass formated KML tags as the final argument for these functions: They will 'wrap' whatever is passed in.  These are commonly used tags that don't usually get any attributes associated with them.
(def wrap-with-placemark (partial tag "Placemark" nil))
(def wrap-with-extended-data (partial tag "ExtendedData" nil))
(def wrap-with-linear-ring (partial tag "LinearRing" nil))
(def wrap-with-outer-boundary (partial tag "outerBoundaryIs" nil))
(def wrap-with-inner-boundary (partial tag "innerBoundaryIs" nil))
(def wrap-with-document-tag (partial tag "Document" nil))
(def wrap-with-polygon (partial tag "Polygon" nil))

(defn data-tag
  "Forms a <Data></Data> tag (corresponds with the properties object of geojson).  Contains a <value> tag inside which holds prop value"
  [[key val]]
  (when (and key val)
    (tag "Data"
      {:name (name key)}
      (tag "value" nil val))))

(defn make-data-tags
  "Takes in an entire Feature map, and makes a data tag for each kv pair in the properties map"
  [{:keys [properties]}]
  (when (seq properties)
    (str/join (map data-tag properties))))

(defn format-coordinates-tag
  "Creates <coordinates> tags with the properly comma separated lat/long pairs, and wraps that tag in a <LinearRing> tag"
  [coord]
  (when (seq coord)
    (wrap-with-linear-ring (tag "coordinates" nil (str/join " " (map #(str/join "," %) coord))))))

(defn format-coordinates
  "Takes the collection of all coordinates (outer and inner if applicable) and creates appropraite wrapping tags"
  [coordinate-coll]
  (when (seq coordinate-coll)
    (str/join
      (concat (wrap-with-outer-boundary (format-coordinates-tag (first coordinate-coll)))
              (map (comp wrap-with-inner-boundary format-coordinates-tag) (next coordinate-coll))))))

(defn make-shape-tags
  "Takes in the a Feature and uses the geometry property to create a proper shape tag, usually <Polygon>, and sticks in the formatted coordinates"
  [{:keys [geometry]}]
  (when (seq geometry)
    (if (= (:type geometry) "MultiPolygon")
      (tag "MultiGeometry"
           nil
           (apply str (map #(wrap-with-polygon (format-coordinates %)) (:coordinates geometry))))
      (tag (:type geometry)
           nil
           (format-coordinates (:coordinates geometry))))))

(defn create-feature-tag
  "takes in a feature and creates the proper data and shape kml tags"
  [feature]
  (-> feature
       make-data-tags
       wrap-with-extended-data
       (str (make-shape-tags feature))
       wrap-with-placemark))

(defn add-body
  "Kicks off the iterative process of creating KML shapes based on the features collection from geojson passed in."
  [features]
  (str/join (map create-feature-tag features)))

(defn make-kml
  "Takes the KML header, adds a base KML tag and document tag, and wraps formatted KML shape data"
  [geojson]
  (str top-header
       (tag "kml" {:xmlns (cfg/get-config-first "responseData.kml.xmlns")} (wrap-with-document-tag (add-body (:features geojson))))))
