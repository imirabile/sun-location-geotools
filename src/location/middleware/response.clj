(ns location.middleware.response
  "Ring middleware functions for extracting and adding data to a response map."
  (:require [clojure.string           :as str]
            [clojure.edn              :as edn]
            [location.geocode         :as geo]
            [location.config          :as cfg]
            [location.utils.common    :as util]
            [location.esi             :as esi]
            [location.utils.exception :as ex]
            [location.error-message   :as e]
            [ring.util.response       :refer [content-type]]
            [ring.util.codec          :as codec]
            [ring.middleware.json     :as json]
            [location.kml             :as kml])
  (:import [java.util.concurrent ThreadLocalRandom]
           [java.net URLDecoder]))

;; Helper Functions

(def response-comparator (util/map-comparator (cfg/get-config "geoLocation.lookup.response.keys")))

(defn extract-error-codes
  "Collects the error codes from a response body errors collection into a single collection."
  ([errors]
   (extract-error-codes errors #{}))

  ([errors coll]
   (if (empty? errors)
       coll
       (let [error (first errors)]
         (recur (rest errors) (conj coll (:status (meta error))))))))

(defn process-status
  "Determines what the HTTP status should be based on the response provided."
  [response]
  (if-let [errors (get-in response [:body :errors])]
    (let [code (extract-error-codes errors)]
       (assoc response :status (first code)))
    response))

(defn get-cache-time-in-seconds
  "Gets the cache time to live in seconds from config."
  [status request]
  (let [params (util/nil-query-to-map (:query-string request))
        version (second (util/str-split (:uri request) #"/"))]
    (cond
      (= 200 status)  (cond (:ip params)     (cfg/get-config "default.ip.time.to.live")
                            (= "v2" version) (cfg/get-config "default.time.to.live.v2")
                            :else            (cfg/get-config "default.time.to.live"))
      :else           (cfg/get-config "error.ttl"))))

(defn expand-lookup-value
  "Inspects the response metadata object for lookup value. If the value needs expansion. i.e. a geocode to lat/long, add that to the map and remove the lookup pair."
  [metadata]
  (if-let [geocode (:geocode metadata)]
    (let [geo-map (geo/geocode-to-map geocode)]
      (assoc (dissoc metadata :geocode)
             :latitude (:latitude geo-map)
             :longitude (:longitude geo-map)))
    metadata))

(defn base-response-metadata
  "Generates the base metadata common to all responses"
  [status request]
  {:transaction_id        (get (:headers request) "transaction-id")
   :status_code           status})

(defn decode-metadata
  "Apply url-decode to metadata values which are mapped from querystring"
  [metadata]
  (reduce-kv #(assoc %1 %2 (some-> %3 codec/url-decode)) {} metadata))

(defn success-metadata
  "Creates a metadata map for a successful response."
  [query]
  (expand-lookup-value (util/query-to-map query)))

(defn generate-metadata
  "Creates a metadata map and attaches it to the response map based on success or failure."
  [request response]
  (let [version (second (util/str-split (:uri request) #"/"))
        httpstatus (if (and (= "v2" version) (= 404 (:status response))) 200 (:status response))
        resp (base-response-metadata httpstatus request)
        metadata-keys (map keyword (cfg/get-config "geoLocation.lookup.metadata.keys"))
        str-to-double (comp util/double-value util/parse-number)]
    (condp = httpstatus
      200 (into (sorted-map-by (util/map-comparator metadata-keys))
            (filter (comp some? val)
              (-> resp
                  (assoc :version version)
                  (conj (decode-metadata (success-metadata (:query-string request))))
                  (assoc :generated_time (quot (System/currentTimeMillis) 1000))
                  (assoc :total_cache_time_secs (get-cache-time-in-seconds httpstatus request))
                  (update :latitude str-to-double)
                  (update :longitude str-to-double)
                  (select-keys metadata-keys))))
      resp)))

(defn ^:private compact-response
  "Compresses the body of the response to adhere to the compact format."
  [response]
  (let [body (get-in response [:body :location])]
    (if (or (map? body) (nil? body))
      response
      (assoc-in response [:body :location] (into (sorted-map-by response-comparator)
                                                 (apply merge-with conj
                                                        (zipmap (keys (first body)) (repeat []))
                                                        body))))))

(defn ^:private get-filterable-fields
  "Inspects the query string for value upon which we can filter."
  [query]
  (let [query-map (util/query-to-map query)
        filters (into [] (map keyword (cfg/get-config "search.filter.values")))]
    (select-keys query-map filters)))

(defn ^:private create-filter
  "Take a key/value pair and turns it into a filterable function."
  [pair]
  (fn [record]
    (let [value ((first pair) record)]
      (when value
        (= (str/upper-case (second pair)) (str/upper-case value))))))

(defn ^:private create-filters
  "Take a key/value pair and turns it into a filterable function."
  [query]
  (let [fields (get-filterable-fields query)]
    (when ((comp not empty?) fields)
      (map create-filter fields))))

(defn ^:private filter-data
  "Recurses through all provided filters to deliver the minimal set of data which meets them."
  [filters data]
  (loop [fltrs filters
         result data]
    (if-not fltrs
      result
      (recur (next fltrs)  (filter (first fltrs) result)))))

;; Middleware Wrappers

(defn wrap-headers
  "Sets the headers of the response document."
  [handler]
  (fn [request]
    (as-> (handler request) response
          (assoc-in response [:headers "Cache-Control"]
            (str "max-age=" (get-cache-time-in-seconds (:status response) request)))
          (if (= (:status response) 503)
            (assoc-in response [:headers "Retry-After"] 1)
            response))))

(defn wrap-status
  "Sets the status code of the response based on body."
  [handler]
  (fn [request]
    (let [response (handler request)]
      ;; Status 200 at this point means the provider was able to do something with the
      ;; call. There is no guarantee that is accurate, which is why it's processed here.
      (if (= 200 (:status response))
        (process-status response)
        ;; Else the response has already been formed and should be passed on.
        response))))

(defn wrap-query-params
  "Converts the query string into a clojure map and associates it back to the request."
  [handler]
  (fn [request]
    (let [params (util/nil-query-to-map (:query-string request))
          request (assoc request :params params)]
      (handler request))))

(defn add-metadata
  "Adds metadata to the response."
  [handler]
  (fn [request]
    (let [version (second (util/str-split (:uri request) #"/"))
          response (handler request)]
      (if (and (= "v2" version) (map? (:body response)))
        (update response :body (partial merge {:metadata (generate-metadata request response)}))
        response))))

(defn wrap-format-body
  "Middleware that converts responses to proper format (geojson, kml, json, esi) and associates the correct content-type in response."
  [handler]
  (fn [request]
    (let [response (handler request)
          format (:format (util/nil-query-to-map (:query-string request)))]
      (condp = format
        "json" response
        "geojson" (content-type response "application/geo+json; charset=utf-8")
        "esi" (content-type (update response :body esi/encode) "text/xml; charset=utf-8")
        "kml" (content-type (update response :body kml/make-kml) "text/kml; charset-utf-8")
        response))))

(defn wrap-compact-format
  "Middleware that applies compact format to a response body."
  [handler]
  (fn [request]
    (let [uri (util/str-split (:uri request) #"/")
          version (second uri)
          end-point (last uri)
          response (handler request)]
      (cond
        (= "searchflat" end-point) response
        (= "v3" version)           (compact-response response)
        :else                      response))))

(defn wrap-data-filter
  "Middleware that performs filtering on the response body."
  [handler]
  (fn [request]
    (let [response (handler request)
          filters (create-filters (:query-string request))
          body (get-in response [:body :location])
          locations (filter-data filters body)]
      (cond
        (and (:body response) (nil? body)) response
        (not= 200 (:status response)) response
        (empty? locations) (dissoc response :body)
        locations           (assoc-in response
                                      [:body :location]
                                      locations)
        :else               response))))

(defn wrap-error-handler
  "Inspects the response and creates error documents as needed."
  [handler]
  (fn [request]
    (let [response (handler request)
          body (:body response)
          version (second (util/str-split (:uri request) #"/"))]
      (cond
        (and (= "v2" version) (nil? body)) (assoc response :body {:addresses []})
        (nil? body) (ex/process-errors [(partial e/get-error :not-found)])
        :else response))))
