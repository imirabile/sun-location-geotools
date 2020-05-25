(ns location.handler
  "Handles routing and processing of incoming requests."
  (:use org.httpkit.server)
  (:require [clojure.tools.logging        :as log]
            [clojure.walk                 :as walk]
            [compojure.core               :refer :all]
            [compojure.route              :as route]
            [ring.middleware.json         :as json]
            [ring.middleware.params        :as param]
            [ring.util.response           :as ring]
            [ring.middleware.gzip         :as gzip]
            [location.esi                 :as esi]
            [location.metrics             :as metrics]
            [location.stats               :as stat]
            [location.lookup              :as lookup]
            [location.middleware.response :as resp]
            [location.middleware.logging  :as mlog]
            [location.error-message       :as e]
            [location.validation.core     :as v]
            [location.utils.exception     :as ex]
            [location.config              :as cfg]
            [location.point-map           :as pm]
            [location.providers.internal  :as internal])
  (:gen-class))

(defonce ^:private server (atom nil))

(defn ^:private format-response
  "Converts raw data into an HTTP response"
  [data]
  (if (:status data)
    data
    (ring/response data)))

(defn ^:private get-metric
  "Fetches the correct metric object based on the requests lookup property. If/when time allows, it'd be wise to replace this with a smarter mechanism that automatically determines the call type."
  [lookup]
  (let [met (str "location.metrics/" lookup)]
    (eval (symbol met))))

(defn ^:private dispatch-request-v2
  "Dispatches the function for execution and routes response appropriately."
  [f req]
  (let [metric (get-metric (:lookup req))
        res (metrics/time-fn metric f (:params req))
        resp (or (:location res) (:addresses res) res)]
    (if (:status resp) (ex/process-errors-v2 resp) res)))

(defn ^:private dispatch-request
  "Dispatches the function for execution and routes response appropriately."
  [f req]
  (let [metric (get-metric (:lookup req))
        res (metrics/time-fn metric f (assoc (:params req) :lookup (:lookup req)))
        resp (or (:location res) (:addresses res) res)]
    (if (:status resp) (ex/process-errors resp) res)))

(defn ^:private process-request-v2
  "Handles input validation, request execution, and response formatting."
  [f req]
  (let [req (walk/keywordize-keys req)
        resp (v/validate-values req)]
    (if (empty? resp)
      (format-response (dispatch-request-v2 f req))
      (format-response (ex/process-errors-v2 resp)))))

(defn ^:private process-request
  "Handles input validation, request execution, and response formatting."
  [f req]
  (let [req (walk/keywordize-keys req)
        resp (v/validate-values req)]
    (if (empty? resp)
      (format-response (dispatch-request f req))
      (format-response (ex/process-errors resp)))))

(defn ^:private get-lookup-type
  "Determines what type of lookup is being requested. This is around to support the legacy endpoint which handles both forward and reverse geocoding. New endpoints should all be single purposed."
  [query]
  (let [params (:params query)]
    (cond
      (contains? params :geocode) "reverse-geo"
      (contains? params :address) "forward-geo")))

(defroutes app-routes

  ;; example URI - /heartbeat
  ;; Used for intermittent heartbeat checks to the service. Returns 200 OK
  (GET "/heartbeat" [] "OK")

  (context "/metrics" []

    ;; example URI - /metrics
    ;; Gathers statistical data about the running service.
    (GET "/" [] (format-response (metrics/get-metrics)))

    ;; example URI - /metrics/status
    ;; Gathers status data about the running service.
    (GET "/status" [] (format-response (stat/get-stats))))

  (context "/v2" []

    (context "/location" []

      (GET "/" req (process-request-v2 lookup/legacy-loc-v2 (assoc req :lookup (get-lookup-type req))))

      (GET "/point" req (process-request-v2 lookup/point-v2 (assoc req :lookup "map-point")))

      (GET "/search" req (process-request-v2 lookup/search-v2 (assoc req :lookup "search")))))

  ;; Matches routes beginning with "/v3/"
  (context "/v3" []

    (context "/location" []

      ;; example URI - /v3/location?address=30339
      ;; Handles requests for location data based on geocode or location.
      ;; This supports legacy users.
      (GET "/" req (process-request lookup/legacy-loc (assoc req :lookup (get-lookup-type req))))

      ;; Handles requests for location data based on geocode or location
      (GET "/search" req (process-request lookup/search (assoc req :lookup "search")))

      ;; Handles requests for location data based on geocode or location
      (GET "/searchflat" req (process-request lookup/search (assoc req :lookup "search")))

      ;; Handles requests for location data based on geocode or location
      (GET "/iplookup" req (process-request lookup/ip (assoc req :lookup "ip")))

      ;; Handles request for shapefile product lookups.
      (GET "/geohittest" req (process-request lookup/geo-hit-test (assoc req :lookup "hit-test")))

      ;; example URI - /v3/location/pointmap?type=icaoCode&id=KATL:US
      ;; Handles requests for a single geocode given a type and a polygon ID.
      (GET "/pointmap" req (process-request lookup/point-map (assoc req :lookup "point-map")))

      ;; example URI - /v3/location/resolve?type=icaoCode&id=KATL:US&product=alert
      ;; Handles requests for a product key given a type and a polygon ID.
      (GET "/resolve" req (process-request lookup/resolve-fn (assoc req :lookup "resolve")))

      ;; example URI - /v3/location/mappoint?icaoCode=KATL:US
      ;; Handles requests for a product key given a type and a polygon ID.
      (GET "/point" req (process-request lookup/point (assoc req :lookup "map-point")))

      ;; example URI - /v3/location/boundary?geocode=33.89,-84.46&product=pollen
      ;; Handles requests for polygon geometries for the given geocode and shapefile.
      (GET "/boundary" req (process-request lookup/geo-boundary (assoc req :lookup "boundary")))

      ;; exmaple URI - /v3/location/intersection?geocode=33.89,-84.46&product=postalKey&overlay=pollen
      ;; Handles requests for products that are available within the specified overlay
      (GET "/intersection" req (process-request lookup/intersection (assoc req :lookup "intersection")))

      ;; example URI - /v3/location/catalog
      ;; Returns a list of accepted values for products and types
      (GET "/catalog" req (process-request lookup/catalog (assoc req :lookup "catalog")))
      
      (GET "/near" req (process-request lookup/near (assoc req :lookup "near")))

      (GET "/typeaheadtuner" req (process-request internal/typeahead-tuner (assoc req :lookup "typeaheadtuner")))))

;; If no route matches, return 404 error.
  (route/not-found (ring/response (:error (e/get-error :not-found)))))

(def app
  "A series of middleware functions to call on the request/response."
  (-> app-routes
      json/wrap-json-body
      mlog/log-request
      resp/wrap-query-params
      resp/wrap-status
      resp/wrap-data-filter
      resp/wrap-error-handler
      resp/wrap-compact-format
      resp/add-metadata
      resp/wrap-headers
      mlog/log-stats
      resp/wrap-format-body
      json/wrap-json-response
      gzip/wrap-gzip))

(defn ^:private stop
  "Called when the server is stopped. Waits 500ms for queued requests then resets the server atom to nil."
  []
  (when-not (nil? @server)
    (log/info "Server stopping...")
    (@server :timeout 500)
    (reset! server nil)))

(defn ^:private start
  "Called when the server starts up."
  []
  (log/info (str "Server starting on port: " (cfg/get-config "port")))
  (log/log-capture! "location")
  (pm/pre-load-mappings)
  (reset! server (run-server #'app {:port (cfg/get-config "port")
                                    :thread (cfg/get-config "server.threads")
                                    :queue-size (cfg/get-config "server.queue.size")
                                    :worker-name-prefix (cfg/get-config-first "server.worker.name.prefix")
                                    :destroy stop})))

(defn -main []
  (start))
