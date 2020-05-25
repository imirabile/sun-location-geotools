(ns location.error-message
  "Provides functions which format and yield error messages based on the data given. Each
  function should return a map containing a top level error key with a map as its value.
  This map should contain the SUN error code and a message describing the error.
  Additionally, each map should have associated meta data with the HTTP status code
  to return with this response. All no-arg functions are functions instead of defs due to the
  need of other functions to accept an error function."
  (:require [clojure.string        :as str]
            [location.config       :as cfg]
            [location.utils.common :as common]
            [clojure.data.json     :as json]
            [clojure.tools.logging :as log])
  (:use     [clojure.walk          :as wlk :only (keywordize-keys)]))

(def default-error
  "Returns a generic error document indicating an error on the server side."
  {:code "LOC:RUE-0001" 
   :message "There was an error processing your request. Please try again later."
   :status 500})

(defn get-error-or-default
  "Checks the error-codes file for the given error. Returns the default error value when it does not exist."
  [error]
  (get-in cfg/error-codes [(keyword error)] default-error))

(defn get-error
  "Returns an error document for the error key provided optionally appending text to message. default-error is used"
  ([error]
   (get-error error ""))
  ([error text]
   (let [e (get-error-or-default (keyword error))
         m (assoc e :message (-> e :message (str " " text) str/trimr))]
     (with-meta
       {:error (dissoc m :status)}
       {:status (:status m)}))))
