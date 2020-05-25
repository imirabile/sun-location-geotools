(ns location.providers.bing
  "Concrete map provider implementation for utilizing Bing Locations API."
  (:use     [location.geocode]
            [location.utils.common])
  (:require [clojure.string           :as str]
            [location.providers.base  :as base]
            [location.metrics         :as metrics]
            [location.geocode         :as geo]
            [location.config          :as cfg]
            [location.utils.common    :as util]))

(def ^:private forward-server-prefix (cfg/get-config-first "geoLocation.lookup.bing.forwardgeo.prefix"))
(def ^:private reverse-server-prefix (cfg/get-config-first "geoLocation.lookup.bing.reversegeo.prefix"))
(def ^:private access-token (cfg/get-config-first "geoLocation.lookup.bing.apiKey"))
(def ^:private max-results (cfg/get-config "geoLocation.lookup.bing.maxResults"))

(defn ^:private get-reverse-url
  "Creates a URL for reverse geocoding."
  [geocode lang form]
  (let [latlon ((comp str (partial str/join ",") geo/split-geocode) geocode)]
    (str reverse-server-prefix latlon "?o=json&incl=ciso2&maxResults=" max-results "&c=" (url-encode lang) "&key=" access-token)))

(defn ^:private get-forward-url
  "Creates a url for forward geocoding."
  [address lang form]
  (str forward-server-prefix "?q=" (url-encode address) "&o=json&incl=ciso2&maxResults=" max-results "&c=" (url-encode lang) "&key=" access-token))

(defn get-url
  "Returns the provider specific URL to call based on the lookup data."
  [lookup lang form]
  (cond
    (re-find geocode-pattern lookup) (get-reverse-url lookup lang form)
    :else (get-forward-url lookup lang form)))

(defn extract-location-data
  "Extracts the requested keys from the location data json object"
  [location data-keys]
  (let [ks (vec data-keys)]
    (as-> location $
      (clojurize-json $)
      (get-in $ [:resourceSets 0 :resources])
      (map #(select-keys % ks) $))))

(defn filterAddressLine
  "Filters out an address value that would be considered not useful.
  This is ported from the v2 scala code, used for legacy purposes."
  [addr]
  (let [badvalues (cfg/get-config "geoLocation.lookup.bing.addressFilter")]
    (if-not (some #{addr} badvalues) addr)))

(defn process-result
  "Processes the raw location object, returning the location data"
  [{:keys [address] {[lat lon] :coordinates} :point}]

  (into (sorted-map-by (util/map-comparator (map keyword (cfg/get-config "geoLocation.lookup.response.keys.v2"))))
        (filter (comp some? val)
          {:latitude       (if (number? lat) (* 1.0 lat) lat)
           :longitude      (if (number? lon) (* 1.0 lon) lon)
           :address        (filterAddressLine (:addressLine address))
           :locality       (:locality address)
           :admin_district (:adminDistrict address)
           :postal_code    (:postalCode address)
           :country        (:countryRegion address)
           :country_code   (:countryRegionIso2 address)})))

(defn get-location-data
  "Fetches the raw data from Bing."
  [lookup lang form filter]
  (-> (get-url lookup lang form)
      (base/get-location-data metrics/map-provider)))

(defn get-location 
  "Returns a vector of locations for the given request"
  [query {:keys [language format filter]}]
  (let [data (get-location-data query language format filter)]

    ;; If the status exists, it means there was an error from the provider. it 
    ;; should be returned as is.
    (if (:status data)
      data
    ;; Otherwise the provider returned some data and it needs to be processed.
      (as-> data $
            (extract-location-data $ (cfg/get-locationdata-keys "bing"))
            (map process-result $)
            (dedupe $)))))

(defn get-location-v2 [query params] (get-location query params))
