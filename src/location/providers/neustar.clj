(ns location.providers.neustar
  "Concrete location provider for Neustar IP location lookup service."
    (:require [clojure.data.json       :as json]
              [location.metrics        :as metrics]
              [location.config         :as cfg]
              [location.providers.base :as base])
    (:import [java.security MessageDigest]
             [java.math BigInteger]))

(defn generate-signature
  "Creates a signature used in calling the NeuStar API."
  []
  (let [md (MessageDigest/getInstance "MD5")
        input (str (cfg/get-config-first "ipaddress.lookup.neustar.apiKey")
                   (cfg/get-config-first "ipaddress.lookup.neustar.sharedSecret")
                   (long (/ (System/currentTimeMillis) 1000)))]
    (.update md (.getBytes input))
    (format "%032x" (BigInteger. 1 (.digest md)))))

(defn get-url
  "Forms a URL for use in querying NeuStar's API."
  [ip-address lang fmt]
  (str (cfg/get-config-first "ipaddress.lookup.neustar.endPoint")
       ip-address
       "?format="
       fmt
       "&apikey="
       (cfg/get-config-first "ipaddress.lookup.neustar.apiKey")
       "&sig="
       (generate-signature)))

(defn get-location-data
  "Fetches the raw data from Neustar."
  [ip-address lang fmt]
  (let [url (get-url ip-address lang fmt)]
    (base/get-location-data url metrics/ip-provider)))

(defn ^:private process-result
  "Formats the raw location data into a location map."
  [data]
  (let [res (select-keys data [:latitude :longitude :continent :msa :dma :region])]
    (merge res {:country_data (:CountryData data)
                :state_data   (:StateData data)
                :city_data    (:CityData data)})))

(defn get-location
  "Returns a location record for the given ip address."
  [ip-address lang fmt]
  (let [data (get-location-data ip-address lang fmt)]

    ;; If the data has a status, that means the provider returned a formatted document,
    ;; which indicates an error of some sort.
    (if (:status data)
      data
      (-> data
          (json/read-str :key-fn keyword)
          (get-in [:ipinfo :Location])
          process-result))))
