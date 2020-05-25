(ns location.providers.mapbox
  "Concrete mapping provider implementation for utilizing Mapbox Geocode Lookup API."
  (:use [location.geocode]
        [location.utils.common])
  (:require [clojure.string          :as str]
            [location.utils.common   :as util]
            [location.utils.placeid  :as place]
            [location.metrics        :as metrics]
            [location.providers.base :as base]
            [location.config         :as cfg]
            [clojure.set             :as set]
            [clojure.tools.logging   :as log]
            [location.shapefile      :as shape]
            [location.point-map      :as pm]))

(def ^:private reverse-server-prefix (cfg/get-config-first "geoLocation.lookup.mapbox.reversegeo.prefix"))
(def ^:private forward-server-prefix (cfg/get-config-first "geoLocation.lookup.mapbox.forwardgeo.prefix"))
(def ^:private access-token (cfg/get-config-first "geoLocation.lookup.mapbox.apiKey"))

(defn get-admin-district-code
  "Pulls the admin district code from a MapBox response and checks if it lies within the configured countries."
  [code]
  (when code
    (let [countries (str/join "|" (map str/upper-case (cfg/get-config "search.filter.adminDistrictCode.countries")))
          regex (re-pattern (str "^(" countries ")-(\\S{2})$"))]
      (last (re-find regex code)))))

(defn ^:private add-limit
  "Adds limit parameter to the provided url. Due to the behavior of Mapbox, limit is omitted from reverse geocoding urls."
  [url lookup]
  (if (= lookup "reverse-geo")
    url
    (str url
         "&limit="
         (cfg/get-config "geoLocation.lookup.mapbox.maxResults"))))

(defn ^:private add-language-mode
  "Adds languageMode parameter to the provided url when configured."
  [url req]
  (str url (when-let [mode (util/nil-or-empty? (cfg/get-language-mode))]
             (when (cfg/can-use-language-mode? req)
               (str "&languageMode=" mode)))))

(defn ^:private add-filter
  "Adds types paramter to the provided url. Filters may resolve to single or multiples (csv)"
  [url fltr]
  (if-let [f (cfg/expand-filter fltr)]
    (str url "&types=" (str/join "," f))
    (str url "&types=" (str/join "," (cfg/get-config "geoLocation.lookup.mapbox.default.types")))))

(defn ^:private add-country-filter
  "Adds country code to the request that gets sent out to the provider"
  [url countryCode]
  (if countryCode
    (str url "&country=" (str/lower-case countryCode))
    url))

(defn ^:private add-limit-and-filter
  "Due to an oddity in the Mapbox reverse geocoder, limit is only allowed if there is a single type parameter provided. 
  This function orchestrates adding the correct combination of parameters to the url."
  [url lookup fltr]
  (if (and (= lookup "reverse-geo") (empty? fltr))
    (add-filter url fltr)
    (-> url
        (add-limit lookup)
        (add-filter fltr))))

(defn ^:private add-common-params
  "Adds all key/values to the url which are common to both geocode and address lookups."
  [url lang]
  (str url
       ".json?access_token=" access-token
       "&language=" lang))

(defn ^:private get-reverse-geocode-url
  "Creates a reverse geocoding url."
  [lookup]
  (let [longitude (int (read-string (second (str/split lookup #","))))
        prefix (or (cfg/get-config-first (str "geoLocation.lookup.mapbox.longitude.key." longitude)) reverse-server-prefix)]
    (->> lookup
         reverse-geocode
         (str prefix))))

(defn ^:private get-forward-geocode-url
  "Creates a forward geocoding url."
  [lookup]
  (str forward-server-prefix lookup))

(defn get-url
  [query req]
  (let [lu (if (re-find geocode-pattern query)
             "reverse-geo"
             "forward-geo")
        url (if (= "reverse-geo" lu)
              (get-reverse-geocode-url query)
              (get-forward-geocode-url query))]
    (-> url
        (add-common-params (:language req))
        (add-limit-and-filter lu (:locationType req))
        (add-language-mode req)
        (add-country-filter (:countryCode req)))))

(defn extract-location-data
  "Extracts the requested keys from a MapBox location data json object."
  [location data-keys]
  (let [ks (vec data-keys)]
    (->> location
         clojurize-json
         :features
         (map #(select-keys % ks)))))

(defn flatten-context
  "Flattens a mapbox context object into a single map."
  [context]
  (let [ctx (if (map? context) (vector context) context)]
    (reduce-kv
      (fn [c k v]
        (assoc c (:id v) (dissoc v :id)))
      {}
      ctx)))

(defn split-id
  "Splits mapbox ids of the forms 'name.id' and drops the id."
  [feature]
  (keyword (first (util/str-split feature #"\."))))

(defn sanitize-location-data
  "Converts the seq of pairs into a finished map."
  [loc]
  (zipmap (map split-id (keys loc))
          (vals loc)))

(defn sanitize-context
  "Transforms the context into a single clojure map."
  [context]
  (-> context
      flatten-context
      sanitize-location-data))

(defn get-place-type
  "Retrieves the place-type from Mapbox response. If :place_type absent (mostly due caching) fallback to :id"
  [data]
  (if-let [place-type (:place_type data)]
    (keyword (first place-type))
    (split-id (:id data))))

(defn ^:private process-result
  "Transforms the raw MapBox location object into a location map."
  [data {:keys [language lookup]}]
  (let [lang (util/extract-language language)
        context (sanitize-context (merge (:context data) {:id (:id data)
                                                          :text (:text data)
                                                          :short_code (get-in data [:properties :short_code])}))
        {[lon lat] :center} data
        latitude  (read-string (format "%.3f" (float lat)))
        longitude (read-string (format "%.3f" (float lon)))
        place-type (get-place-type data)
        place-id (place/get-id (str lat "," lon) place-type)
        locale1 (get-in context [:place :text])
        locale2 (get-in context [:locality :text]) 
        locale3 (get-in context [:district :text])
        locale4 (get-in context [:neighborhood :text])
        country (when-let [cc (get-in context [:country :short_code])]
                  (str/upper-case cc))
        city (get-in context [:place :text])
        postalCode (get-in context [:postcode :text])
        postalPlace (pm/get-postal-place (str postalCode ":" lang ":" country))]

    {:address (:place_name data)
     :adminDistrict (get-in context [:region :text])
     :adminDistrictCode (when-let [rc (get-admin-district-code (get-in context [:region :short_code]))]
                          (str/upper-case rc))
     :country (get-in context [:country :text])
     :countryCode country
     :city (or city postalPlace)
     :locale {:locale1 locale1
              :locale2 locale2 
              :locale3 locale3
              :locale4 locale4}
     :latitude latitude
     :longitude longitude
     :postalCode postalCode
     :postalKey nil    ;; TODO: placeholder; source needs to be resolved 
     :ianaTimeZone nil ;; TODO: same as postakKey 
     :neighborhood (get-in context [:neighborhood :text])
     :feature (split-id (:id data))
     :placeId place-id
     :displayName (if (cfg/override-display-name? lookup country)                    
                    (or postalPlace (or locale2 locale1 (:text data)))
                    (:text data))}))

(defn process-result-v2
  "Formats the response for v2-specific fields, whether the provider is mapbox or bing."
  [data params]
  (let [response-keys (map keyword (cfg/get-config "geoLocation.lookup.response.keys.v2"))]
    (into (sorted-map-by (util/map-comparator response-keys))
      (as-> (process-result data params) $
            (assoc $ :admin_district (or (:adminDistrictCode $)
                                         (:adminDistrict $)))
            (assoc $ :admin_district_code (:adminDistrictCode $))
            (assoc $ :admin_district_name (:adminDistrict $))
            (assoc $ :display_name (:displayName $))
            (clojure.set/rename-keys $ {:city        :locality
                                        :postalCode  :postal_code
                                        :countryCode :country_code
                                        :placeId     :place_id})
            (select-keys $ response-keys)))))

(defn nil-vals
  "Takes in a map and returns a seq of any keywords (as strings) with nil values"
  [coll]
  (seq (keys (filter
               (comp not some? val)
               coll))))

(defn find-missing-fields
  "Uses the required fields from the ls.conf file and merges any that are not included in the current result"
  [{:keys [:latitude :longitude] :as data} lang required]
  (let [nil-in-response (nil-vals data)
        to-add (set/intersection
                 (set required)
                 (set (map name nil-in-response)))]
    (when-not (empty? to-add)
      (zipmap (map keyword to-add)
              (keep identity (shape/get-polygons (str latitude "," longitude) to-add lang))))))

(defn add-missing-fields
  "Adds any of the fields required in the response that are missing from initial provider result set.  
  Pulls data from local shapefiles to fill in the gaps"
  [{:keys [feature] :as data} lang required]
  (if (set/subset? (hash-set (name feature)) (set (cfg/get-config "geoLocation.lookup.response.addRequired")))
      (merge data (find-missing-fields data lang required))
      data))

(defn get-location-data
  "Fetches the raw data from Mapbox."
  [query req]
  (-> (get-url query req)
      (base/get-location-data metrics/map-provider)))

(defn get-location-v2
  "Returns a Location record for the given request."
  [query req]
  (let [data (get-location-data query req)]
    (if (:status data)
      ;; If the status exists, it means there was an error from the provider. it should be returned as is.
      data
      ;; Otherwise the provider returned some data and it needs to be processed.
      (as-> data $
        (extract-location-data $ (cfg/get-locationdata-keys "mapbox"))
        (map #(process-result-v2 % {:language (:language req) :lookup (:lookup req)}) $)
        (map #(add-missing-fields % (:language req) (cfg/get-config "geoLocation.lookup.response.v2.required")) $)
        (map #(into {} (clojure.core/filter (comp some? val) %)) $)
        (dedupe $)))))

(defn get-location
  "Returns a Location record for the given request."
  [query req]
  (let [data (get-location-data query req)]
    (if (:status data)
      ;; If the status exists, it means there was an error from the provider. it should be returned as is.
      data
      ;; Otherwise the provider returned some data and it needs to be processed.
      (map #(process-result % {:language (:language req) :lookup (:lookup req)})
           (extract-location-data data (cfg/get-locationdata-keys "mapbox"))))))
