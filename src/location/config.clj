(ns location.config
  "Reads configuration settings from a file and loads them into variables."
  (:require [clojure.data.json     :as json]
            [clojure.core.memoize  :as memo]
            [clojure.edn           :as edn]
            [clojure.tools.logging :as log]
            [clojure.string        :as str]
            [clojure.java.io       :as io]
            [clojure.set           :as set]
            [location.geocode      :as geo]
            [location.utils.common :as util]))

(defn ^:private prep-conf-for-read
  "Performs the given io function on the file and reads the contents. This is needed due to certain files being prepared by io/resource and others by io/file."
  [file f]
  (try
    (slurp (f file))

    (catch java.io.FileNotFoundException ex
      (log/info "Exception reading " file)
      (log/debug ex))))

(defn ^:private split-conf-line
  "Splits the line of configuration into two parts on the first equal sign"
  [line]
  (when-let [sign ((fnil str/index-of "") line "=")]
    [(subs line 0 sign)
     (subs line (+ 1 sign))]))

(defn ^:private split-config
  "Performs splits on the config file on newlines and equals signs. Produces a collection of kev value pairs."
  [file]
  (as-> file $
    (util/str-split $ #"\n")
    (filter #(not (.startsWith % "#")) $)
    (map #(split-conf-line %) $)))

(defn parse-conf
  "Parses the given .conf file into a clojure map."
  [file f]
  (log/info "Reading config from" file)
  (some->>  (prep-conf-for-read file f)
            split-config
            (into [])
            (reduce-kv (fn [c k v]
                         (if (= (count v) 2)
                           (merge (hash-map (edn/read-string (first v)) (util/read-config-value (second v))) c)
                           c))
                       {})))

(defn get-config-files
  "Checks the config for the location where dev ops config files may be. Returns a list of any config files found there."
  [conf-path]
  (let [dir (io/file conf-path)]
    (log/info "Checking" conf-path "for config files")
    (when dir
      (->> dir
           file-seq
          (map str)
          (filter #(re-find #"(\w+\.conf)$" %))))))

(defn load-config
  "Loads the default configuration and any dev ops supplied configuration."
  []
  (let [conf (parse-conf "ls.conf" io/resource)
        devops-conf (or (System/getProperty "config.path") (first (get conf 'devops.config.path)))]
    (merge conf
           (reduce merge (map #(parse-conf % io/file) (get-config-files devops-conf))))))

(def ^:private config (load-config))

(defn ^:private get-conf
  "Fetches the requested key from the config map."
  [conf]
  (if-let [env (System/getenv conf)]
    (util/read-config-value env)
    (get config (symbol conf))))

(def get-config
  "Caches configuration settings."
  (memo/ttl get-conf :ttl/threshold (util/sec->ms (get-conf "default.time.to.live"))))

(defn get-config-first
  "Takes the first item of the returned config sequence."
  [s]
  (-> s
      get-config
      first))

(defn ^:private get-config-group
  "Fetches all config with a similar group name. Example is default would return default.cache.xxx and default.xxx"
  [group]
  (let [conf-keys (keys config)
        group-re (re-pattern group)]
    (log/info "Fetching all" group "configuration.")
    (filter #(re-find group-re (str %)) conf-keys)))

(def get-config-group
  "Caches the results of fetching configuration groups."
  (memo/ttl get-config-group :ttl/threshold (* (get-config "default.time.to.live") 1000)))

(def aliases
  "Fetches the aliases from the config map. Necessary due to using the conf file format of ALIAS_name. There is no tree structure to rely on."
  (->> "alias"
        get-config-group
       (map #(util/str-split (str %) #"\."))
       flatten
       (filter #(not= "alias" %))))

(def formats
  "Fetches the formats from the config map. Necessary due to using the conf file format of ALIAS_name. There is no tree structure to rely on."
  (set (get-config "supportedFormats")))

(def location-types
  "Fetches the valid locationTypes from the config map."
  (->> "supportedLocationTypes" 
       get-config-group
       (map #(get-conf (name %))) 
       flatten
       set))

(def products
  "Fetches the products from the config map. Necessary due to using the conf file format of product.name. There is no tree structure to rely on."
  (->> "product"
        get-config-group
       (map #(util/str-split (str %) #"\."))
       flatten
       (filter #(not= "product" %))
       (map str/lower-case)
       set
       (set/union (set (get-config "geohittest.identity.values")))))

(defn config-to-keyword
  "Turns a config variable title into a keyword for the config map.  Also takes an optional accessor function, ie second or first.
  Example- used to parse the ERROR_CODE namespace in config.  ERRORCODE_timeout_message becomes :timeout (with second as f) or :message (with last as f)."
  ([val]
   (-> (name val)
       keyword))

  ([val f]
   (-> (name val)
       (util/str-split #"\.")
       f
       keyword)))

(defn expand-err-entries
  "Maps over the error entries from error-codes and turns them into error map with :message :code and :status entries."
  [entry]
  (into {} (map (juxt #(config-to-keyword % last)
                      #(-> % name get-config vector util/flatten-single-val))
                entry)))

(def error-codes
  "Fetches the error codes and creates a map with error codes and their contents"
  (as->
    "error-code" $
    (get-config-group $)
    (group-by #(config-to-keyword % second) $)
    (reduce-kv (fn [c k v] (update c k expand-err-entries)) $ $)))

(def ^:private provider-base
  "Specifies the namespace root of all providers."
  "location.providers.")

(def ^:private mapping-provider-name
  "Specifies the name of the configured mapping provider"
  (get-config-first "geoLocation.lookup.provider.name"))

(def ^:private mapping-provider-name-internal
  "Specifies the name of the configured mapping provider"
  (get-config-first "geoLocation.lookup.provider.name.internal"))

(def ^:private mapping-provider-name-v2
  "Specifies the name of the configured mapping provider"
  (get-config-first "geoLocation.lookup.provider.name.v2"))

(def ^:private ip-provider-name
  "Specifies the name of the ip lookup provider"
  (get-config-first "ipaddress.lookup.provider.name"))

(defn get-provider
  "The namespace of the mapping provider to be used. In the form of 'location.providers.xyz'"
  [provider]
  (str provider-base (condp = provider
                       "ip"                ip-provider-name
                       "geocoder-v2"       mapping-provider-name-v2
                       "geocoder-internal" mapping-provider-name-internal
                                           mapping-provider-name)))

(defn ^:private resolve-fn
  "Resolves the given function to the loaded provider."
  [provider func]
  (log/info "Loading " provider)
  (load (str "/" (str/replace provider "." "/")))
  (load-string (str provider "/" func)))

(defn ^:private loc-fn
  "The get-location function from the passed provider."
  [provider]
  (resolve-fn (get-provider provider) "get-location"))

(def loc-fn
  "Caches the result of loc-fn as this won't change during server execution."
  (memo/ttl loc-fn :ttl/threshold (* (get-config "default.time.to.live") 1000)))

(defn ^:private loc-fn-v2
  "The get-location function from the passed provider."
  [provider]
  (resolve-fn (get-provider provider) "get-location-v2"))

(def loc-fn-v2
  "Caches the result of loc-fn as this won't change during server execution."
  (memo/ttl loc-fn-v2 :ttl/threshold (* (get-config "default.time.to.live") 1000)))

;; Fetches all available product mappings from the configured directory.
(defonce product-key-maps
  (let [mapping-path (get-config-first "mapping.path")
        file-ext ".txt"]
    (log/debug "Searching for product mappings in" mapping-path)
    (into (set (get-config "pointmap.identity.values"))
          (comp (map #(.getName %))
                (filter #(str/ends-with? % file-ext))
                (map #(subs % 0 (str/last-index-of % file-ext))))
          (file-seq (io/file mapping-path)))))


(defn expand-filter
  "Returns a map of filter attributes for the (default) provider using the fltr attribute"
  ([fltr]
   (expand-filter mapping-provider-name fltr))
  
  ([provider fltr]
   (get-config (str "geoLocation.lookup." provider ".filter." fltr))))

(defn get-locationdata-keys
  "Fetches keys used to filter out Mapbox response content."
  ([]
   (get-locationdata-keys mapping-provider-name))
  
  ([provider]
   (map keyword (get-config (str "geoLocation.lookup." provider ".data.keys")))))

(defn override-display-name?
  "Verify if we should override the displayName of the record given a route and a country"
  ([route country]
   (override-display-name? route country mapping-provider-name))
  
  ([route country provider]
   (let [r (set (get-config (str "geoLocation.lookup." provider ".displayName.override.route")))
         c (set (get-config (str "geoLocation.lookup." provider ".displayName.override.country")))]
     (and (contains? r route) (contains? c country)))))

(defn get-language-mode
  "Returns a language mode when configured."
  ([]
   (get-language-mode mapping-provider-name))
  
  ([provider]
   (first (get-config (str "geoLocation.lookup." provider ".filter.languageMode")))))

(defn can-use-language-mode?
  "Verify if we can set the languageMode=strict given all the following variables:"
  ([req]
   (can-use-language-mode? req mapping-provider-name))
  
  ([req provider]
   (let [{:keys [query lookup language address locationType]} req
         routes (set (get-config (str "geoLocation.lookup." provider ".filter.languageMode.routes")))
         address? (or (some? address) (= locationType "address"))
         geocode? (and (some? query) (geo/validate-geocode query))
         lang (util/extract-language language)]
     (and
      (true? (contains? routes lookup))
      (false? geocode?)
      (false? address?) 
      (false? (= "en" lang))))))

(defn is-near-product?
  "Returns true if the product is in the list of products which have geo-point in their shapefile "
  [product]
  (contains? (set (get-config "near.products")) product))

(defn is-line-string-product?
  "Returns true if the product is in the list of products which have [Multi]LineString in their shapefile "
  [product]
  (contains? (set (get-config "linestring.products")) product))

(defn is-shapefile-index?
  "Verifies if this shapefile is setup as an index for other shapefiles such as the adminDistrict3."
  [shape]
  (contains? (set (get-config "multiple.shapefiles.products")) shape))
