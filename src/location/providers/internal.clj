(ns location.providers.internal
  (:require [location.geocode             :as geo]
            [location.geocoder.reverse    :as reverse]
            [location.geocoder.forward    :as forward]
            [location.config              :as cfg]
            [clojure.algo.generic.functor :as func]
            [location.utils.common        :as util]
            [location.utils.placeid       :as place]
            [clojure.tools.logging        :as log]))

(defn format-forward-geo
  "Translates the internal format from the forward geocoder to the external v3 format."
  [data]
  (let [lat (util/double-precision (get-in data [:location :lat]) 3)
        lon (util/double-precision (get-in data [:location :lon]) 3)]
    {:address           (str (:placeName data) ", " (:adminDistrictName1 data) ", " (:countryName data))
     :adminDistrict     (:adminDistrictName1 data)
     :adminDistrictCode (:adminDistrictCode1 data)
     :country           (:countryName data)
     :countryCode       (:countryCode data)
     :city              (:placeName data)
     :latitude          lat
     :longitude         lon
     :postalCode        (:postalCode data)
     :neighborhood      (:adminDistrictName2 data)
     :placeId           (place/get-id (clojure.string/join ":" [(str lat "," lon)
                                                                (:id data)
                                                                "internal"]))}))

(defn format-reverse-geo
  "Translates the internal format from the reverse geocoder to the external v3 format."
  [data geocode]
  {:address           (str (:city data) ", " (:adminDistrictCode data) ", " (:countryCode data))
   :adminDistrict     (:adminDistrict data)
   :adminDistrictCode (:adminDistrictCode data)
   :country           (:country data) 
   :countryCode       (:countryCode data)
   :city              (:city data)
   :latitude          (:latitude data)
   :longitude         (:longitude data)
   :postalKey         (:postalKey data)
   :postalCode        (:postalCode data)
   :neighborhood      (:county data)
   :ianaTimeZone      (:ianaTimeZone data)
   :placeId           (place/get-id geocode ":internal")})


(defn get-location
  "Returns a Location record for the given request."
  [query req]
  (when (cfg/get-config "geoLocation.lookup.provider.internal.enable")
    (log/info "Using internal geocoder.")
    
    (cond
     (:locationType req)
     (let [result (forward/get-loc-by-id (:locationType req) req)]
       (if (empty? result)
         result
         (map format-forward-geo result)))
    
     (re-find geo/geocode-pattern query)
     (when-let [result (reverse/reverse-geo query (:language req))]
       (map #(format-reverse-geo % query) result))
     
     :else
     (when-let [result (forward/forward-geo query req true)]
       (if (:status result)
         result
         (map format-forward-geo result))))))

(defn typeahead-tuner
  "Returns raw search results for the typeahead-tuner route."
  [{query :query :as req}]
  (forward/forward-geo query (func/fmap util/parse-number req) false))
