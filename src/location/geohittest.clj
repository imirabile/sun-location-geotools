(ns location.geohittest
  (:use [location.macros]
        [location.utils.geometry]
        [location.utils.common])
  (:require [location.config :as cfg]
            [location.shapefile :as shape]
            [location.geopackage :as geopkg]))

(defn ^:private get-identity
  "The identity value for a geo hit test request is the geocode itself. If a request for a geocode comes in, that value should just be returned."
  [product geocode]
  (let [id (set (cfg/get-config "geohittest.identity.values"))]
    (when (contains? id product)
      geocode)))

(defn get-polygon
  "Fetches the key from the provide product polygon."
  [geocode product language]
  {:product product
   ;; use config to determine if going through shapefile or geopackage
   ;;:data    (shape/get-polygons geocode (conj #{} product) language nil)})
   :data    (geopkg/get-polygons geocode (conj #{} product) language nil)})

(defn ^:private get-key
  "Returns the product key for the provided data. Checks if there is a configured key, if not, returns the provided value."
  [{:keys [product data]}]
  (if-let [key (keyword (cfg/get-config-first (str "shapefile.key." product)))]
    (if (seq? data)
      (map #(get % key) (flatten data))
      (get data key))
    data))

(defn get-keys
  "Looks up the provided products in shapefiles."
  ([geocode product]
   (get-keys geocode product nil))

  ([geocode product language]
   (if-let [id (get-identity product geocode)]
     {:product product
      :key     id}
     (let [id (map #(get-polygon geocode % language) (expand-product product))
           ks (seq (keep identity (mapcat get-key id)))]
       (when ks
         (conj {:product product} {(pluralize-key ks) (flatten-single-val ks)}))))))
