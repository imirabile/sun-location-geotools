(ns location.catalog
  (:require [location.config       :as cfg]
            [location.utils.common :as util]
            [clojure.string        :as str]))

(def ^:private items {:product cfg/products
                      :type cfg/product-key-maps
                      :overlay cfg/products})

(defn get-item
  "Takes in a keyword as an argument, keyword either comes from the parameter or the key from items map"
  [item-kw]
  (when-let [result (item-kw items)]
    {:item (name item-kw) :values result}))

(defn get-catalog
  "Returns a map of accepted values for product and type."
  [item]
  ;; Turns string argument (ie "product" or "type" to a collection for proper formatting of response array, also allowing for comma separated fields
  {:items (if item
            ;; Keep filters out nil results from incorrect values passed in (splits the string in order to make it a seq, also allows for semi-colon separated params if desired)
            (keep (comp get-item keyword) (util/str-split item #";"))
            (map get-item (keys items)))})


