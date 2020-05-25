(ns location.geocode
  "Provides functions for parsing and processing geocodes."
  (:require [clojure.string :as str]))

(def geocode-pattern 
  "A regex for matching geocodes."
  #"^(-?\d{1,2}\.\d*|-?\d{1,2}|-?\.\d+),(-?\d{1,3}\.\d*|-?\d{1,3}|-?\.\d+)$")

(defn validate-geocode
  "Validates that geocode matches the geocode regex pattern. re-matches return is similar to re-find,
   the distinction is that re-find tries to find _any part_ of the string that matches the pattern, and
   re-matches only matches if the _entire_ string matches the pattern."
  [arg]
  (== 2 (count (rest (re-matches geocode-pattern arg)))))

(defn split-geocode
  "Returns lat long pair if the argument matches the geocode regex pattern."
  [arg]
  (rest (re-find geocode-pattern arg)))

(defn get-latitude
  "Returns the latitude from a geocode"
  [geocode] 
  (-> geocode split-geocode first))

(defn get-longitude
  "Returns the longitude from a geocode"
  [geocode] 
  (-> geocode split-geocode second))

(defn reverse-geocode
  "Reverses the order of a lat-long geocode to a long-lat."
  [geo]
  (->> geo
    split-geocode
    reverse
    (str/join ",")))

(defn geocode-to-map
  "Converts a geocode string of [latitude longitude] to a map."
  [geocode]
  (let [geo (split-geocode geocode)]
    {:latitude (first geo)
     :longitude (second geo)}))
