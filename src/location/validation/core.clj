(ns location.validation.core
  "Provides functions for validating user input."
  (:use [location.macros])
  (:require [location.utils.common  :as util]
            [location.config        :as cfg]
            [location.error-message :as e]
            [location.shapefile     :as shape]
            [location.geocode       :as geo]
            [clojure.string         :as str]
            [clojure.set            :as set]))

(defn validate
  "Validates a request against the given validation function. If the validation returns any issues, these will be passed to the error function for logging, formatting, etc."
  [validation error request]
  (some->> request
           validation
           (partial error)))

;; Value Validations

(defn null-values?
  "Returns a seq of null parameter names, or nil if there are no nil values"
  [{:keys [params]}]
  (seq
    (keys
      (filter #(nil? (val %)) params))))

(defn null-vals-to-string
  "Takes a sequence of keywords returned from null-values? and concatenates to a string for error message"
  [nulls]
  (when nulls
    (str/join ", " (map name nulls))))

(defn unsupported-value
  "Returns a vector pair of [param unsupported-value] as determined by the provided supported seq. Nil is returned for supported values. This is done so valid requests terminate the validation chain early."
  [supported param values]
  (let [val-set ((comp set flatten vector) values)
        unsupported (set/difference val-set (set (map name supported)))]
   (when ((comp not empty?) unsupported)
     param)))

(def unsupported-product-values
  "Partially applies products to unsupported-values"
  (partial unsupported-value (map keyword shape/all-shapes) "product"))

(def unsupported-format-values
  "Partially applies format to unsupported-values"
  (partial unsupported-value (map keyword cfg/formats) "format"))

(def unsupported-locationtype-values
  "Partially applies locationtype to unsupported-values"
  (partial unsupported-value (map keyword cfg/location-types) "locationType"))

(def unsupported-type-values
  "Partially applies types to unsupported-values"
  (partial unsupported-value (conj (map keyword cfg/product-key-maps) :placeid) "type"))

(def unsupported-field-values
  "Partially applies lookup types to unsupported-values"
  (partial unsupported-value (set/union (set 
                                          (flatten 
                                            (conj
                                              (map keyword
                                                   (cfg/get-config
                                                     "geoLocation.lookup.fields"))
                                              (map keyword cfg/aliases))))
                                        shape/shapes)
                             "field"))

(def unsupported-near-values
  "Partially applies products to unsupported-values"
  (partial unsupported-value (map keyword (cfg/get-config "near.products")) "near product"))

(defn validate-null-values
  "Validates the provided parameters do not contain any null values.  If they do, includes a string of null parameters in the error message"
  [request]
  (when-let [nulls (null-values? request)]
    (partial e/get-error :null-parameter (null-vals-to-string nulls))))

(defn validate-geocode-value
  "Validates the provided types against the allowed values."
  [request]
  (when-let [geocode (or (get-in request [:params :geocode])
                         (if (= "geocode" (get-in request [:params :type]))
                           (get-in request [:params :id])))]
    (when (false? (geo/validate-geocode geocode))
      (partial e/get-error :unsupported-values "geocode"))))

(defn validate-format-values
  "Validates the provided format against the allowed values."
  [request]
  (when-let [format (get-in request [:params :format])]
    (validate unsupported-format-values
              (partial e/get-error :unsupported-values)
              format)))

(defn validate-locationtype-values
  "Validates the provided locationType against the allowed values."
  [request]
  (when-let [loctype (get-in request [:params :locationType])]
    (validate unsupported-locationtype-values
              (partial e/get-error :unsupported-values)
              loctype)))

(defn validate-type-values
  "Validates the provided types against the allowed values."
  [request]
  (when-let [type (get-in request [:params :type])]
    (when (unsupported-type-values type)
      (partial e/get-error :not-found))))

(defn validate-product-values
  "Validates the provided products against the allowed values."
  [request]
  (when-let [product (get-in request [:params :product])]
    (let [f (condp = (last (util/str-split (:uri request) #"/")) 
              "near" unsupported-near-values
              unsupported-product-values)]
      (when (f product)
        (partial e/get-error :not-found)))))

(defn validate-overlay-values
  "Validates the provided overlay against the allowed values. Same values are valid for overlay that are valid for product."
  [request]
  (when-let [product (get-in request [:params :overlay])]
    (when (unsupported-product-values product)
      (partial e/get-error :not-found))))

(defn validate-field-values                                  
  "Validates the provided shapefile lookup fields."         
  [request]
  (when-let [fields (get-in request [:params :fields])]
    (when (unsupported-field-values fields)
      (partial e/get-error :not-found))))

(defn validate-zoom-value
  "Validates the provided zoom value is a number between 0 and 14."
  [request]
  (when-let [zoom (util/parse-number (get-in request [:params :zoom]))]
    (when-not (<= 0 zoom 14)
      (partial e/get-error :unsupported-values "zoom"))))

(defn validate-required-fields
  "Verifies that all required fields are provided for the respective api. Currently validating
   /geohittest /pointmap and /resolve which are not handled at the controller level"
  [{:keys [lookup params]}]
  (when-let [required (map keyword (cfg/get-config (str "requiredFields." lookup)))]
    (when-not (set/subset? required params)
      (partial e/get-error :missing-field (str/join ", " (map name required))))))

(defn validate-item-value
  "Validates the item parameter passed in to the catalog route"
  [request]
  (when-let [item (get-in request [:params :item])]
    (when-not (set/subset? (set (util/str-split item #";")) (set (cfg/get-config "catalog.items")))
      (partial e/get-error :unsupported-values "item"))))

(defn validate-values
  "Threads the request through all possible value validations and returns a collection of any violations. Nil returned if no value violations were encountered."
  [request]
  (seq
    (keep identity
      (conj-> request []
              validate-null-values
              validate-geocode-value
              validate-zoom-value
              validate-type-values
              validate-product-values
              validate-overlay-values
              validate-format-values
              validate-locationtype-values
              validate-field-values
              validate-item-value
              validate-required-fields))))
