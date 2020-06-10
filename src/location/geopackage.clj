(ns location.geopackage
  "Provides functions for processing data within geopackages."
  (:use [location.macros]
        [location.utils.geometry]
        [location.utils.common])
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [location.config :as cfg]
            [clojure.java.io :as io])
  (:import [org.geotools.geopkg GeoPackage]
           (org.geotools.jdbc JDBCDataStore)
           (org.geotools.data FileDataStoreFinder DataSourceException)))


(def gt-path (str (cfg/get-config-first "geopackage.path") "/lsd_prod_all.gpkg"))

(defonce geopackage (io/file gt-path))

(def ^:private default-attr "PRESENT_NM")

(defn ^:private get-features
  "Fetches the geopackage feature data using CQL."
  [polygon fltr]
  (when-let [store (FileDataStoreFinder/getDataStore geopackage)]
    (try
      [store
       (some-> store
               (.getFeatureSource polygon)
               (.getFeatures fltr)
               .features)]

      (catch DataSourceException ex
        (log/info "Exception while loading" polygon "features")
        (log/debug ex)))))

(defn ^:private nil-attr
  "Gets the provided attribute from the provided geopackage data. If this yields a nil or empty value, nil is returned."
  [data attr]
  (nil-or-empty? (.getAttribute data attr)))

(defn additional-polygon-data
  "Adds any additional data for the polygon from the geopackage specified in the config."
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

(defn ^:private get-default-attribute
  "Fetches only the first attribute data from the given geopackage feature."
  [data]
  (when (.hasNext data)
    (as-> data $
          (.next $)
          (.getAttribute $ default-attr))))

(defn ^:private get-attribute
  "Fetches the attribute data from the given geopackage feature."
  [data product locale]

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
      (map iso-8859-1-encoding (persistent! coll)))

    (catch Exception ex
      (log/error "Exception retrieving data from geopackage.")
      (log/error ex))))

(defn ^:private apply-key-mask
  "For the geopackages which emit multiple attributes, zipmap them with the provided data."
  [product data]
  (if-let [mask (cfg/get-config (str "shapefile.additional.attributes.mask." product))]
    (map (partial zipmap (map keyword mask)) (partition (count mask) data))
    data))

(defn ^:private get-polygon
  "Given a geocode, returns the requested polygon from the geopackage."
  ([fltr locale product]
   (get-polygon fltr locale product product))

  ([fltr locale product shape]
   (let [data (get-features shape fltr)
         attributes (when data (get-attribute data product locale))]
     ;;(close-shape store data)
     (apply-key-mask product attributes))))

(defn ^:private get-product
  "Returns the product keys for the given filter."
  [fltr locale product]
  (when-let [expanded (expand-product product)]
    (map #(get-polygon fltr locale %) expanded)))

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
         (map (comp flatten-single-val f) products)))

     (catch java.lang.NumberFormatException ex
       (log/info "Non-numeric value passed to get-polygons")
       (log/debug ex)))))

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
  "Returns the geopackage attribute which serves as the products key."
  [product]
  (or (cfg/get-config-first (str "geopackage.key.attribute." product))
      default-attr))

(defn ^:private get-boundary
  "Fetches the boundary coordinates for the given product."
  ([fltr product]
   (get-boundary fltr product product))

  ([fltr product shape]
   (let [data (get-features shape fltr)
         coll (transient [])]
     (try
       (while (.hasNext data)
         (let [next (.next data)
               default-geo (.getDefaultGeometry next)
               polygon-count (.getNumGeometries default-geo)
               polygon-type (.getGeometryType default-geo)]
           (conj! coll
                  {:product     product
                   :type        (if (and (= 1 polygon-count) (= polygon-type "MultiPolygon")) "Polygon" polygon-type)
                   :coordinates (get-coordinates default-geo polygon-count polygon-type)
                   :key         (nil-attr next (get-key-attr product))})))

       (catch Exception ex
         (log/debug "Exception fetching " product " geometry")
         (log/error ex))
       ;;(finally
       ;;  (close-shape store data)
       ;;  )
       )

     (flatten-single-val (persistent! coll)))))

(defn get-geometries
  "Returns a map of polygon boundaries for all polygons in the requested product."
  [geocode product]
  (when-let [expanded (expand-product product)]
    (let [filter-fn (if (cfg/is-line-string-product? product) touches-filter contains-filter)]
      (map #(get-boundary (filter-fn geocode) %) expanded))))

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
