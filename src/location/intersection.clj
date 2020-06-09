(ns location.intersection
  (:use [location.macros]
        [location.utils.geometry])
  (:require [location.shapefile    :as shape]
            [location.geohittest   :as gh]))

(defn ^:private format-intersections
  "Formats the response map"
  [res-data product geo o]
  (let [ks (map #(gh/get-keys geo %) (expand-product o))
        expanded (expand-product product)]
    ;; Nested map is needed for in case multiple expanded parameters (ie alerts) get used
    (mapcat
      (fn
        [k data]
        (map #(merge k { :key-type %2 :keys (flatten %) }) data expanded))
      ks res-data)))

(defn get-intersections
  "Gets and formats the values for the products that intersect or are contained within the supplied overlay"
  [geocode product overlay]
  (let [data (seq (filter (complement empty?) (shape/get-intersecs geocode product overlay)))]
    (when (seq (keep identity (flatten data)))
      (format-intersections data product geocode overlay))))

