(ns location.geoboundary
  (:require [location.shapefile    :as shape]
            [location.utils.common :as util]
            [location.shapefile    :as shape]))

(defn ^:private format-coordinates
  "Formats the vector of coordinates to produce a seq of lat/long maps."
  [coordinates]
  ;;  Using reduce with conj on a list is essential for effectively "reversing" the order of coordinates to abide by GeoJSON standards.
  (reduce #(conj %1 [(.x %2) (.y %2)]) '() coordinates))

(defn format-geometries
  "Takes in a LinearRing from get-geometries and returns a geoJSON formatted coordinate vector"
  [rings]
  (let [coords (map #(.getCoordinates %) rings)]
    (map format-coordinates coords)))

(defn ^:private format-props
  "Returns a map for \"product\" property of geoJSON response"
  [prop k]
  {:product prop
   :key k})

(defn flatten-polygon
  "If the type of coordinates is a Polygon, this will strip the outer vector to conform with geoJSON Polygon type standards."
  [coll]
  (if (> (count coll) 1)
    coll
    (first coll)))

(defn make-geo-json-feature
  "Returns a map for one feature property based on the geometry passed in."
  [{:keys [coordinates product key type]}]
  {:type "Feature"
   :geometry {:type type
              :coordinates (flatten-polygon (map format-geometries coordinates))}
   :properties (format-props product key)})

(defn get-boundaries
  "Retrieves the polygon geometry of the requested product."
  [geocode product]
  ;;  Filters out any nil values, as get-geometries will return a seq with a nil value if the file is not found
  (when-let [bounds (seq (flatten (keep identity (shape/get-geometries geocode product))))]
    {:features (map #(make-geo-json-feature %) (flatten bounds))}))
