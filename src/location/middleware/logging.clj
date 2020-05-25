(ns location.middleware.logging
  "Ring middleware functions relating to logging request information."
  (:require [clojure.tools.logging :as log]
            [location.stats        :as stats]))

(defn log-request
  "Middleware to log the incoming request."
  [handler]
  (fn [request]
    (log/info request)
    (handler request)))

(defn log-stats
  "Adds pertinent information from the request/response to the stats atom."
  [handler]
  (fn [request]
    (let [response (handler request)
          route (:uri request)]
      (stats/add-status! route (:status response))
      response)))
