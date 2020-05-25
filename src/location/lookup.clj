(ns location.lookup
  "Coordinates lookups from disparate data sources for lookup routes. Currently, lookups are done from location providers and shapefiles."
  (:require [clojure.set                  :as set]
            [clojure.core.memoize         :as memo]
            [location.geohittest          :as ght]
            [clojure.tools.logging        :as log]
            [location.point-map           :as pm]
            [location.geocode             :as geo]
            [location.shapefile           :as shape]
            [location.utils.common        :as util]
            [location.utils.placeid       :as place]
            [location.geoboundary         :as gb]
            [location.config              :as cfg]
            [location.intersection        :as inter]
            [location.utils.common        :as util]
            [location.catalog             :as catalog]))

(def response-comparator (util/map-comparator (map keyword (cfg/get-config "geoLocation.lookup.response.keys"))))

(defn ^:private type-fields
  "Returns the intersection of the field list and accepted fields provider."
  [allowed fields]
  (set/intersection fields allowed))

(defn ^:private shapefile-fields
  "Returns a set of fields within the request that are found in shapefiles."
  [fields]
  (when-let [types (seq
                     (keep #(when ((comp not empty?) %) %)
                           (type-fields fields shape/shapes)))]
    (set types)))

(defn ^:private provider-fields
  "Returns a set of fields within the request that are found in mapping provider calls."
  [fields]
  (when-let [types (seq
                     (keep #(when ((comp not empty?) %) %)
                           (type-fields fields (set (cfg/get-config "geoLocation.lookup.fields")))))]
    (set types)))

(defn ^:private expand-alias
  "Checks if the provided argument is an alias in the optional alias map. If so, return the aliased values. If not, return the argument."
  [a]
  (if-let [expanded (cfg/get-config (str "alias." a))]
    expanded
    a))

(def ^:private expand-alias
  "Memoized version of expand-alias"
  (memo/ttl expand-alias :ttl/threshold (* (cfg/get-config "default.time.to.live") 1000)))

(defn ^:private process-types
  "Checks for and resolves aliases in the input collection and returns a set of fields to lookup."
  [types]
  (set (flatten (map expand-alias types))))

(defn ^:private get-data-keys
  "Fetches the requested keys for each geocode provided."
  [locale geocode]
  (if geocode
    (when-let [fields (cfg/get-config "geoLocation.lookup.fields.added")]
      (let [[lat lon] (geo/split-geocode geocode)
            values (shape/get-polygons (str lat "," lon) fields locale)]
        (util/combine-shapefile-fields-and-values (map keyword fields) values)))))

(defn geocoder
  "Requests data from the configured mapping provider by the given query value."
  ([query geo-fns]
   (geocoder query {:language "en-us" :format "json"} geo-fns))
  ([query params geo-fns]
   (let [geocoders (map cfg/loc-fn (or (not-empty geo-fns)
                                       (cfg/get-config "provider.priority")))]
     (loop [[geo-fn & next-geo-fns] geocoders]
       (if geo-fn
         (let [result (not-empty (geo-fn query params))
               status (:status result)]
           (if (or (empty? result)
                   (and status (not= 200 status)))
             (if next-geo-fns
               (recur next-geo-fns)
               (if (and status (not= 200 status))
                 (do
                   (log/warn "Http status not Ok status: '" status "' result: " result)
                   result)))
             {:location (map #(dissoc % :feature) result)})))))))

(defn geocoder-v2
  ([query _]
   (geocoder-v2 query {:language "en-us" :format "json"} nil))
  ([query params _]
   (let [loc ((cfg/loc-fn-v2 "geocoder-v2") query params)]
     (when-not (empty? loc)
       {:addresses (map #(dissoc % :feature) loc)}))))

(defn search
  "Requests data from the configured mapping provider by the given query value."
  ([req geo-fn]
   (geo-fn (:query req) req nil))
  ([req]
   (search req geocoder)))

(defn search-v2
  [req]
  (search req geocoder-v2))

(defn ip
  "Requests data from the configured ip provider."
  [{:keys [ip language format] :as req}]
  (let [data ((cfg/loc-fn "ip") ip language format)
        {:keys [latitude longitude status]} data]
    (if (and status (not= 200 status))
      data
      (geocoder (str latitude "," longitude) (assoc req :locationType "city") nil))))

(defn polygon
  "Parses the lookup types and prepares calls to the various data sources."
  [{:keys [geocode language format fields zoom] :as req}]
  (let [fields (process-types (util/str-split fields #";"))
        shapes (shapefile-fields fields)
        p-fields (provider-fields (set/difference fields shapes))]
  
    (cond->> {}
      shapes (merge {:polygons (shape/get-polygons geocode shapes language zoom)}))))
  
(defn geo-hit-test
  "Looks up the provided products in shapefiles."
  [{:keys [geocode product]}]
  (when (util/nil-or-empty? geocode)
    (ght/get-keys geocode product)))

(defn point-map
  "Fetches a lat,lon pair given a product key; eg. county AKC013"
  [{:keys [type id]}]
  (pm/get-point type id))

(defn legacy-loc
  "Handles requests for the legacy location service. Routes differently based on whether a geocode or address is received."
  [{:keys [geocode address] :as req}]
  (geocoder (or geocode address) req nil))

(defn legacy-loc-v2
  [{:keys [geocode address] :as req}]
  (geocoder-v2 (or geocode address) req nil))

(defn get-conditional-field-data
  "Checks the config to see if the parameter type warrents adding additional data using subsets.  Returns the appropriately formatted data"
  [type geo data fld]
  ;;  trigger-types refers to the values retrieved from the config that declare the field should be included
  (let [trigger-types (set (cfg/get-config (str "geoLocation.lookup.fields." fld)))
        curr-type (hash-set type)]
    (if (set/subset? curr-type trigger-types)
      ;;  Adds fields to the data object if the parameter (type) entered is part of the set of trigger types
      (merge data (select-keys (util/flatten-single-val (shape/get-polygons geo (hash-set fld) nil)) (map keyword (cfg/get-config (str "geoLocation.lookup.fields." fld ".keys")))))
      data)))

(defn add-conditional-point-data
  "Adds additional data for results based on parameters used.  For example, if iataCode or icaoCode is used as a param, we also include airport name in the result"
  [type geo]
  (let [cond-fields (cfg/get-config "geoLocation.lookup.fields.conditional")]
    ;;  Partially applies get-conditional-field-data and uses it as the reduce function, returning a map of any additional data needed for the response
    (reduce (partial get-conditional-field-data type geo) {} cond-fields)))

(defn ^:private format-point
  "Prepares the raw data from a map point request"
  [geo type id data]
  {:location
   (into (sorted-map-by response-comparator)
         (dissoc (merge (add-conditional-point-data type geo)
                        (when (or (= type "icaoCode") (= type "iataCode"))
                              {(keyword type) id})
                        data)
                 :address))})

(defn ^:private process-place
  "Processes a point request for a place id."
  [place geo-fn {:keys [language] :as req}]
  (let [[geocode feature origination] (util/str-split (place/get-place place) #":")
        loc (geo-fn geocode
                    (assoc req :locationType feature)
                    (if origination
                      ["geocoder-internal"]
                      ["geocoder"]))]
    (if (:body loc)
      loc
      (merge (first (or (:location loc)
                        (:addresses loc)))
             (get-data-keys language geocode)))))

(defn ^:private get-typeid-from-simplified
  "Extracts the :type and :id from requests where the type and id are in the simplified form. In the example below:
  [/v3/location/boundary?iataCode=ATL] is simplified form of
  [/v3/location/boundary?type=iataCode&id=ATL]"
  [req]
  (let [vt (map #(-> % identity keyword) cfg/product-key-maps)
        k (first (filter #(some? (% req)) vt))
        n (name k)
        v (k req)]
    (merge {:type {k v} :id v} (point-map {:type n :id v}))))


(defn ^:private get-typeid 
  "Extracts the :type and :id from requests; when not available, calls get-typeid-from-simplified
  [/v3/location/near?type=iataCode&id=ATL]
  [/v3/location/boundary?type=iataCode&id=ATL]"
  [{:keys [type id] :as req}]
  (if (and type id) 
    (merge {:type {type id} :id id} (point-map {:type type :id id}))
    (get-typeid-from-simplified req)))

(defn point
  "Fetches location data given a product/key pair. For example; icaoCode = KATL."
  ([req geo-fn]
   (let [{:keys [type id geocode]} (get-typeid-from-simplified req)]
     (when geocode
       (let [loc (if (:placeid req)
                   (process-place id geo-fn req)
                   (when-let [location (geo-fn geocode req nil)]
                       (merge (first (or (:location location)
                                         (:addresses location)))
                              (get-data-keys (:language req) geocode))))]
         (format-point (first (util/str-split geocode #":")) type id loc)))))
  ([req]
   (point req geocoder)))

(defn point-v2
  [req]
  (let [resp (into {} (filter (comp some? val) (:location (point req geocoder-v2))))
        resp (into {} (filter (comp util/nil-or-empty? val) resp))]
    {:addresses [(clojure.set/rename-keys resp {:ianaTimeZone :iana_time_zone
                                                :dstStart     :dst_start
                                                :dstEnd       :dst_end
                                                :postalCode   :postal_code
                                                :postalKey    :postal_key
                                                :airportName  :airport_name})]}))

(defn geo-boundary
  "Returns the boundary geometries of the requested product/geocode pairs."
  [{:keys [product] :as req}]
  (when-let [geo (:geocode (get-typeid req))]
    (when-let [bounds (gb/get-boundaries geo product)]
      (merge {:type "FeatureCollection"} bounds))))

(defn intersection
  "Returns the products that are included in the specified overlay parameter"
  [{:keys [product overlay] :as req}]
  (let [geo (:geocode (point-map req))]
    (inter/get-intersections geo product overlay)))

(defn catalog
  "Returns a map with product and type as keys, and a vector of valid values for each key. For example; { product [\"pollen\" \"alerts\"]"
  [{:keys [item]}]
  (catalog/get-catalog item))

(defn near
  "Returns a seq of maps with data about the n closest products."
  [{:keys [product] :as req}]
  (when-let [geo (:geocode (get-typeid req))]
    (when-let [locs (shape/get-near geo product)]
      {:location locs})))

(defn resolve-near
  "Used to resolve products through near due to shapefile designed with geo-point. Uses the near'est match"
  [{:keys [product type id] :as req}]
  (when-let [hits (near req)]
    (let [k (keyword (cfg/get-config-first (str "shapefile.key." product)))]
      {:type type
       :id id
       :product product
       :key (-> hits :location first k)})))
 
(defn resolve-hit
  "Resolves a type and id to a product. 
  This function uses the type and id to call point-map, 
  Then uses the result to call geo-hit-test with the product."
  [{:keys [product type id] :as req}]
  (when-let [geo (point-map req)]
    (when-let [hit (geo-hit-test (merge req (select-keys geo [:geocode])))]
      (dissoc (merge geo hit) :geocode))))

(defn resolve-fn
  "Need to decide if product will be resolved using regular geo-hit-test or near."
  [{:keys [product] :as req}]
  (if (cfg/is-near-product? product)
    (resolve-near req)
    (resolve-hit req)))
