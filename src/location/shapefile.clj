(ns location.shapefile
  "Provides functions for processing data within shapefiles."
  (:use     [location.macros])
  (:require [clojure.java.io          :as io]
            [clojure.string           :as str]
            [clojure.tools.logging    :as log]
            [clojure.core.memoize     :as memo]
            [clojure.set              :as set]
            [location.utils.common    :as util]
            [location.geocode         :as geo]
            [location.config          :as cfg])
  (:import [org.geotools.data FileDataStoreFinder]
           [org.geotools.filter.text.cql2 CQL]
           [com.vividsolutions.jts.geom GeometryFactory]
           [java.lang Math]))

(def ^:private default-attr "PRESENT_NM")

(defonce ^:private geo-factory (new GeometryFactory))

(defn ^:private close-shape
  "Closes the data stream from the shapefile and disposes its data store"
  [store data]
  (when (some? data) (.close data))
  (when (some? store) (.dispose store)))

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

(def ^:private blacklist-alias
  "Checks each alias for blacklisted files."
  (when-let [bl-set (set (cfg/get-config "shapefile.blacklist"))]
    (reduce-kv (fn [c k v]
                (let [bl-alias (set/intersection (set v) bl-set)]
                  (if ((comp not empty?) bl-alias)
                    (assoc c k (str/join ", "bl-alias)))))
               {}
               (vector cfg/aliases))))

(def shapes
  "A set of all available shapefiles. This is the set difference between all files in the directory and the blacklisted files from configuration."
  (do
    (log/info "Available products: " (str/join ", " cfg/products))
    (log/info "Available shapefiles: " (str/join ", " valid-shapes))
    (when ((comp not empty?) (cfg/get-config "shapefile.blacklist"))
      (log/info "Blacklisted shapefiles: " (str/join ", " (cfg/get-config "shapefile.blacklist"))))
    (when-let [aliases blacklist-alias]
      (log/info "Aliases containing blacklisted files: " aliases))
    (set/difference all-shapes (cfg/get-config "shapefile.blacklist"))))

(defn ^:private kilo-to-degree
  "Converts a number from kilometers to degrees. Uses 40076 / 360 as one degree since the circumference of the Earth is 40076 KM."
  [d]
  (let [km-per-deg (/ 40076 360)]
    (double (/ d km-per-deg))))

(def ^:private zoom-levels
  {0 20038
   1 10019
   2 5009
   3 2505
   4 1252
   5 626
   6 313
   7 157
   8 78
   9 39
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

(defn ^:private touches-filter
  "Geotools filter for determining if a point is touched by a LineString"
  [geocode]
  (CQL/toFilter (str "TOUCHES(the_geom, POINT(" 
                     (get-point-str geocode) "))")))

(defn ^:private contains-filter
  "Geotools filter for determining if a point is contained within a polygon"
  [geocode]
  (CQL/toFilter (str "CONTAINS(the_geom, POINT(" 
                     (get-point-str geocode) "))")))

(defn ^:private dwithin-filter
  "Geotools filter for determining if a polygon within a radius of the given point."
  [geocode zoom]
  (CQL/toFilter (str "DWITHIN(the_geom, POINT(" 
                     (get-point-str geocode) "), " 
                     (calc-zoom-level zoom) ", kilometers)")))

(defn ^:private intersection-filter
  "Geotools filter for determining if a polygon is included in the overlay field
  Must ultimately end up with a MultiPolygon made up of Polygon(s) 
  derived from each set of Coordinates, as some polygons have 'holes', ie Arizona's ianaTimeZone"
  [overlay]
  (let [poly (.createMultiPolygon 
              geo-factory 
              (into-array (map (fn [[shell & holes]]
                                 (.createPolygon geo-factory shell (when holes (into-array holes))))
                               (:coordinates overlay))))]
    (CQL/toFilter (str "INTERSECTS(the_geom, " poly 
                       ") AND NOT TOUCHES(the_geom, " poly ")"))))

(defn ^:private get-shapefile-path
  "Returns the shapefile path of the given file. Does not check if that path is valid or exists, merely interpolates the file into the expected shapefile path."
  [file]
  (str (cfg/get-config-first "shapefile.path") "/" file "/" file ".shp"))

(defn ^:private get-shapefile
  "Returns the file path for the given shapefile."
  [shapefile]
  (log/info "getting" shapefile "shapefile")
  (let [file (-> shapefile
                 get-shapefile-path
                 io/file)]
    (when (.exists file)
      (log/debug "found " shapefile)
      file)))

(def get-shapefile
  "Returns the feature source data for the supplied shapefile. This function is memoized due to the small number of possible shapefiles."
  (memo/ttl get-shapefile :ttl/threshold (* (cfg/get-config "default.time.to.live") 1000)))

(defn ^:private get-features
  "Fetches the shapefile feature data using CQL."
  [polygon fltr]
  (when-let [store (some-> polygon get-shapefile FileDataStoreFinder/getDataStore)]
    (try
      [store
       (some-> store
               (.getFeatureSource polygon)
               (.getFeatures fltr)
               .features)]
      
      (catch org.geotools.data.DataSourceException ex
        (log/info "Exception while loading" polygon "features")
        (log/debug ex)))))

(defn ^:private nil-attr
  "Gets the provided attribute from the provided shapefile data. If this yields a nil or empty value, nil is returned."
  [data attr]
  (util/nil-or-empty? (.getAttribute data attr)))

(defn additional-polygon-data
  "Adds any additional data for the polygon from the shapefile specified in the config."
  [polygon data]
  (when-let [additional (cfg/get-config (str "shapefile.additional.attributes." polygon))]
    (map (partial nil-attr data) additional)))

(defn ^:private try-locale-attr
  "Attempts to get localized data from the feature. If this fails, then fall back to the default attribute."
  [data locale]
  (let [loc (or locale default-attr)]
    (nil-> data
           (nil-attr (str/upper-case (str/replace loc #"-" "_")))
           (nil-attr default-attr))))

(defn ^:private sanitize-string
  "Encodes the string to ISO-8859-1"
  [s]
  (when s
    (util/encode-string (str s) "ISO-8859-1")))

(defn ^:private get-default-attribute
  "Fetches only the first attribute data from the given shapefile feature."
  [data]
  (when (.hasNext data)
    (as-> data $
      (.next $)
      (.getAttribute $ default-attr))))

(defn ^:private get-attribute
  "Fetches the attribute data from the given shapefile feature."
  [data store product locale]

  "coll must be mutable due to the way geotools implemented their iterator. This is not a
   shared value and is used to build an immutable vector, which is returned. It was deemed
   an acceptable break from the functional paradigm."

  (try
    (let [coll (transient [])]
      (while (.hasNext data)
        (as-> data $
              (.next $)
              (do (conj! coll (try-locale-attr $ locale))
                  (doall (map (partial conj! coll) (additional-polygon-data product $))))))
      (map sanitize-string (persistent! coll)))

    (catch Exception ex
      (log/error "Exception retrieving data from shapefile.")
      (log/error ex))))

(defn ^:private resolve-indexed-shapefile
  "Given a shapefile (or product) look up the attributes and using the first one,
  determine if this file contains the actual attributes or an index to the next shapefile.
  Returns the name of the shapefile with the actual attributes."
  [shape fltr]
  (if (false? (cfg/is-shapefile-index? shape))
    shape
    (if (or (nil? shape) (nil? fltr))
      shape
      (let [[store data] (get-features shape fltr)
            attr (some-> data get-default-attribute)]
        (close-shape store data)
        (resolve-indexed-shapefile attr fltr)))))

(defn expand-product
  "Retrieves the shapefiles that make up the supplied product."
  [product]
  (if-let [products (cfg/get-config (str "product." product))]
    products
    (when (set/subset? (hash-set product) valid-shapes)
      [product])))

(defn ^:private apply-key-mask
  "For the shapefiles which emit multiple attributes, zipmap them with the provided data."
  [product data]
  (if-let [mask (cfg/get-config (str "shapefile.additional.attributes.mask." product))]
    (map (partial zipmap (map keyword mask)) (partition (count mask) data))
    data))

(defn ^:private get-polygon
  "Given a geocode, returns the requested polygon from the shapefile."
  ([fltr locale product]
   (get-polygon fltr locale product product))

  ([fltr locale product shape]
   (let [[store data] (get-features shape fltr)
         attributes (when data (get-attribute data store product locale))]
     (close-shape store data)
     (apply-key-mask product attributes))))
 

(defn ^:private get-product
  "Returns the product keys for the given filter."
  [fltr locale product]
  (when-let [expanded (expand-product product)]
    (map #(get-polygon fltr locale % (resolve-indexed-shapefile % fltr)) expanded)))

(defn get-polygons
  "Returns a map of polygons of the requested types."
  ([geocode products locale]
   (get-polygons geocode products locale nil))

  ([geocode products locale zoom]
   (try
     (when geocode
       (let [fltr (if-not (nil? zoom) 
                    (dwithin-filter geocode (Integer/parseInt zoom))
                    (contains-filter geocode))
             f (partial get-product fltr locale)]
         (map (comp util/flatten-single-val f) products)))
     
     (catch java.lang.NumberFormatException ex
       (log/info "Non-numeric value passed to get-polygons")
       (log/debug ex)))))
  
(defn ^:private haversine
  "Calculates the distance between to points on a sphere according to the Haversine formula."
  [lon1 lat1 lon2 lat2]
  (let [R 6372.8 ; kilometers
        dlat (Math/toRadians (- lat2 lat1))
        dlon (Math/toRadians (- lon2 lon1))
        lat1 (Math/toRadians lat1)
        lat2 (Math/toRadians lat2)
        a (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2))) (* (Math/sin (/ dlon 2)) (Math/sin (/ dlon 2)) (Math/cos lat1) (Math/cos lat2)))]
    (* R 2 (Math/asin (Math/sqrt a)))))

(defn ^:private calculate-distance
  "Takes a map with a lat long and appends the Haversine formula distance to it."
  [geocode {:keys [latitude longitude] :as obj}]
  (let [[lat lon] (map read-string (geo/split-geocode geocode))
        dist-km (haversine lon lat (read-string longitude) (read-string latitude))]
    (conj obj {:distanceKm (util/round-num dist-km 2)
               :distanceMi (util/round-num (* dist-km 0.621371) 2)})))

(defn get-near
  "Retrieves the nearest products to the given point. Results and radius are specified in configuration."
  [geocode product]
  (let [max (cfg/get-config (str "near." product ".results.max"))
        radius (str (cfg/get-config (str "near." product ".radius")))
        near (flatten (get-polygons geocode [product] "en-US" radius))]
    (log/debug (str "Fetched " (count (keys near)) " " product 
                    "(s) within radius " radius 
                    " of " geocode 
                    ". Required " max))
    (if (some (comp not nil?) near)
      (take max (sort-by :distanceKm (map #(calculate-distance geocode %) near))))))

(defn ^:private is-line-string-geometry?
  "Verifies if this polygon is either a LineString or MultiLineString "
  [polygon-type]
  (contains? #{"LineString" "MultiLineString"} polygon-type))

(defn linestring-coordinates
  "Creates a vector of points from the linestring."
  [line-st]
  (let [geoms (dec (.getNumGeometries line-st))]
    (loop [iter 0
           coll []]
      (if (> iter geoms)
        coll
        (recur (inc iter) (conj coll (.getGeometryN line-st iter)))))))

(defn polygon-coordinates
  "Creates a vector of counter-clockwise outer ring and clockwise inner rings (\"holes\") 
  in the polygon."
  [polygon]
  (let [outer (.getExteriorRing polygon)
        index (dec (.getNumInteriorRing polygon))]
    (loop [iter 0
           coll [outer]]
      (if (> iter index)
        coll
        (recur (inc iter) (conj coll (.getInteriorRingN polygon iter)))))))

(defn get-coordinates
  "Returns a collection of valid LinearRings created from the boundaries passed in."
  [bound polygon-count polygon-type]
  (loop [index (dec polygon-count)
         coll []]
    (if-not (neg? index)
      (let [coordinates-fn 
            (if (is-line-string-geometry? polygon-type) linestring-coordinates polygon-coordinates)]
        (recur (dec index)
               (conj coll (coordinates-fn (.getGeometryN bound index)))))
      (seq coll))))

(defn ^:private get-key-attr
  "Returns the shapefile attribute which serves as the products key."
  [product]
  (or (cfg/get-config-first (str "shapefile.key.attribute." product))
      default-attr))
 
(defn ^:private get-boundary
  "Fetches the boundary coordinates for the given product."
  ([fltr product]
   (get-boundary fltr product product)) 

  ([fltr product shape]
   (let [[store data] (get-features shape fltr)
         coll (transient [])]
     (try
       (while (.hasNext data)
         (let [next (.next data)
               default-geo (.getDefaultGeometry next)
               polygon-count (.getNumGeometries default-geo)
               polygon-type (.getGeometryType default-geo)]
           (conj! coll
                  {:product product
                   :type (if (and (= 1 polygon-count) (= polygon-type "MultiPolygon")) "Polygon" polygon-type)
                   :coordinates (get-coordinates default-geo polygon-count polygon-type)
                   :key (nil-attr next (get-key-attr product))})))
      
       (catch Exception ex
         (log/debug "Exception fetching " product " geometry")
         (log/error ex))
       (finally 
         (close-shape store data)))

     (util/flatten-single-val (persistent! coll)))))

(defn get-geometries
  "Returns a map of polygon boundaries for all polygons in the requested product."
  [geocode product]
  (when-let [expanded (expand-product product)]
    (let [filter-fn (if (cfg/is-line-string-product? product) touches-filter contains-filter)]
      (map #(get-boundary (filter-fn geocode) % (resolve-indexed-shapefile % (filter-fn geocode))) expanded))))

(defn get-intersection
  "Iterates over the collection of filters and uses them to get the product data"
  [expanded fltr]
  (map #(get-product fltr nil %) expanded))

(defn get-intersecs
  "Returns a sequence of the product values contained in the overlay"
  [geocode product overlay]
  (when-let [geometries (seq (filter (complement empty?) (get-geometries geocode overlay)))]
    (let [expanded (expand-product product)
          filters (map #(intersection-filter %) geometries)]
      (map #(get-intersection expanded %) filters))))
