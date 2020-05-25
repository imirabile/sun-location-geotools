(ns location.stats
  "Provides functions for statistic monitoring."
  (:require [location.utils.common :as utils]
            [clojure.string        :as str]))

(def ^:private stats (atom {}))

(defn status-codes
  "If the value has a map of status codes, produce a map of those values."
  [record]
  {(first record) (:status (second record))})

(defn add-status!
  "Increments the status count for the given status code."
  [route s]
  (swap! stats #(update-in % [(keyword (str route)) :status s] utils/nillable-inc)))

(defn get-stats
  "Fetches the information from the stats atom."
  []
  (into {} (map status-codes (dissoc @stats :/v2/metrics/status :/v2/metrics))))
