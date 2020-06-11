(ns location.utils.geometry
  "Provides helper functions for processing data within shapefiles and geopackages."
  (:use [location.macros])
  (:require [location.utils.common :as util]
            [location.geocode :as geo]
            [location.config :as cfg]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.set :as set])
  (:import
    [org.geotools.filter.text.cql2 CQL]
    [com.vividsolutions.jts.geom GeometryFactory]
    [java.lang Math]))

(defonce ^:private geo-factory (new GeometryFactory))

(defn is-line-string-geometry?
  "Verifies if this polygon is either a LineString or MultiLineString "
  [polygon-type]
  (contains? #{"LineString" "MultiLineString"} polygon-type))

(defn ^:private kilo-to-degree
  "Converts a number from kilometers to degrees. Uses 40076 / 360 as one degree since the circumference of the Earth is 40076 KM."
  [d]
  (let [km-per-deg (/ 40076 360)]
    (double (/ d km-per-deg))))

(def ^:private zoom-levels
  {0  20038
   1  10019
   2  5009
   3  2505
   4  1252
   5  626
   6  313
   7  157
   8  78
   9  39
   10 20
   11 10
   12 5
   13 2
   14 1})

(defn ^:private calc-zoom-level
  [z]
  (when-let [zoom-km (get zoom-levels z)]
    (kilo-to-degree zoom-km)))

(defn ^:private get-point-str
  "Returns a longitude latitude pair separated by spaces"
  [geocode]
  (str (geo/get-longitude geocode) " " (geo/get-latitude geocode)))

;;
;; Filter Helpers
;;

(defn touches-filter
  "Geotools filter for determining if a point is touched by a LineString"
  ([geocode]
   (CQL/toFilter (str "TOUCHES(the_geom, POINT("
                      (get-point-str geocode) "))")))

  ([geocode loc-type]
   (CQL/toFilter (str "loc_type='" loc-type "' AND TOUCHES(geom, POINT("
                      (get-point-str geocode) "))"))))


(defn contains-filter
  "Geotools filter for determining if a point is contained within a polygon"
  ([geocode]
   (CQL/toFilter (str "CONTAINS(the_geom, POINT("
                      (get-point-str geocode) "))")))

  ([geocode loc-type]
   (CQL/toFilter (str "loc_type='" loc-type "' AND CONTAINS(geom, POINT("
                      (get-point-str geocode) "))"))))

(defn dwithin-filter
  "Geotools filter for determining if a polygon within a radius of the given point."
  ([geocode zoom]
   (CQL/toFilter (str "DWITHIN(the_geom, POINT("
                      (get-point-str geocode) "), "
                      (calc-zoom-level zoom) ", kilometers)")))

  ([geocode zoom loc-type]
   (CQL/toFilter (str "loc_type='" loc-type "' AND DWITHIN(geom, POINT("
                      (get-point-str geocode) "), "
                      (calc-zoom-level zoom) ", kilometers)"))))

(defn intersection-filter
  "Geotools filter for determining if a polygon is included in the overlay field
  Must ultimately end up with a MultiPolygon made up of Polygon(s)
  derived from each set of Coordinates, as some polygons have 'holes', ie Arizona's ianaTimeZone"
  ([overlay]
   (let [poly (.createMultiPolygon
                geo-factory
                (into-array (map (fn [[shell & holes]]
                                   (.createPolygon geo-factory shell (when holes (into-array holes))))
                                 (:coordinates overlay))))]
     (CQL/toFilter (str "INTERSECTS(the_geom, " poly
                        ") AND NOT TOUCHES(the_geom, " poly ")"))))

  ([overlay loc-type]
   (let [poly (.createMultiPolygon
                geo-factory
                (into-array (map (fn [[shell & holes]]
                                   (.createPolygon geo-factory shell (when holes (into-array holes))))
                                 (:coordinates overlay))))]
     (CQL/toFilter (str "loc_type='" loc-type "' AND INTERSECTS(geom, " poly
                        ") AND NOT TOUCHES(geom, " poly ")")))))

;;
;; Geometry Helpers
;;

(defn haversine
  "Calculates the distance between to points on a sphere according to the Haversine formula."
  [lon1 lat1 lon2 lat2]
  (let [R 6372.8                                            ; kilometers
        dlat (Math/toRadians (- lat2 lat1))
        dlon (Math/toRadians (- lon2 lon1))
        lat1 (Math/toRadians lat1)
        lat2 (Math/toRadians lat2)
        a (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2))) (* (Math/sin (/ dlon 2)) (Math/sin (/ dlon 2)) (Math/cos lat1) (Math/cos lat2)))]
    (* R 2 (Math/asin (Math/sqrt a)))))

(defn calculate-distance
  "Takes a map with a lat long and appends the Haversine formula distance to it."
  [geocode {:keys [latitude longitude] :as obj}]
  (let [[lat lon] (map read-string (geo/split-geocode geocode))
        dist-km (haversine lon lat (read-string longitude) (read-string latitude))]
    (conj obj {:distanceKm (util/round-num dist-km 2)
               :distanceMi (util/round-num (* dist-km 0.621371) 2)})))

;;
;; Shape and product helpers
;;

(defn expand-product
  "Retrieves the geopackages that make up the supplied product."
  [product]
  (if-let [products (cfg/get-config (str "product." product))]
    products
    [product]))

(def valid-shapes
  "Fetches only shapefiles from the configured directory."
  (let [shapefile-path (str (cfg/get-config-first "shapefile.path"))
        dir (io/file shapefile-path)]
    (log/debug "Searching for shapefiles in" shapefile-path)
    (->> dir
         file-seq
         (map (comp last #(str/split % #"\/") str))
         (filter #(re-find #"(\w+)\.shp$" %))
         (map (comp first #(str/split % #"\.")))
         set)))

(def all-shapes
  "Returns shapefiles and products from the configured directory."
  (set/union valid-shapes cfg/products))

(def shapes
  "A set of all available shapefiles. This is the set difference between all files in the directory and the blacklisted files from configuration."
  (do
    (log/info "Available products: " (str/join ", " cfg/products))
    (log/info "Available shapefiles: " (str/join ", " valid-shapes))
    (when ((comp not empty?) (cfg/get-config "shapefile.blacklist"))
      (log/info "Blacklisted shapefiles: " (str/join ", " (cfg/get-config "shapefile.blacklist"))))
    (when-let [aliases nil]
      (log/info "Aliases containing blacklisted files: " aliases))
    (set/difference all-shapes (cfg/get-config "shapefile.blacklist"))))
