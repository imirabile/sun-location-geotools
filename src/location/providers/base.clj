(ns location.providers.base
  "High level location provider functionality. All concrete providers should use these functions
  whenever possible."
  (:require [org.httpkit.client       :as http]
            [clojure.tools.logging    :as log]
            [location.metrics         :as metric]
            [location.config          :as cfg]
            [location.error-message   :as err]
            [location.stats           :as stat]
            [location.utils.exception :as e]))

(defn call-client 
  "Requests data from the provided url. Due to the nature of http-kit, this returns a promise."
  [url]
  (log/info "Calling provider with url:" url)
  (http/get url {:timeout (cfg/get-config "http.client.timeout")}))

(defn get-location-data 
  "Returns the location retrieved from the provider. If there was an error at the provider return an error document."
  [url metric] 
  (let [p (call-client url)
        {:keys [status body headers error]} (metric/time-fn metric #(deref %) p)
        error (if error error "Error message not available")]
    (stat/add-status! "provider" (if status status 408))
    (when (not= 200 status) (log/debug "Error fetching provider data:" status body headers error))
    (condp = status
      200   body 
      400   (e/create-error-doc (partial err/get-error :bad-ip-address))
      404   (e/create-error-doc (partial err/get-error :not-found))
      (e/handle-server-error error))))
