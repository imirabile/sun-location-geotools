(ns location.geocoder.reverse
  "Provides functions for querying shapefiles based on geo codes"
  (:require [location.shapefile    :as shape]
            [location.geocode      :as geo]
            [location.config       :as cfg]
            [location.utils.common :as util]))

(defn reverse-geo
  "Takes in coordinates and gets the appropriate fields from shapefiles"
  ([geo]
   (reverse-geo geo "en-US"))

  ([geo locale]
   ;  Checks whether there are results for the given geocode
   (when-let [data (shape/get-polygons geo (cfg/get-config "reverse.geocoder.shapefiles") locale)]
     (let [all-fields (util/combine-shapefile-fields-and-values 
                       (map keyword (cfg/get-config "reverse.geocoder.fields")) data)]
       (if (some (complement nil?) (vals all-fields))
         [(merge (geo/geocode-to-map geo) all-fields)])))))
