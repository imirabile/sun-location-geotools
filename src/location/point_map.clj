(ns location.point-map
  "Provides functions for resolving a type and id to a geocode representing that polygon."
  (:require [clojure.java.io          :as io]
            [clojure.string           :as str]
            [clojure.core.memoize     :as memo]
            [clojure.tools.logging    :as log]
            [location.utils.placeid   :as place]
            [location.config          :as cfg]
            [location.utils.common    :as util]))

(def mappings 
  "A mapping of all available mappings."
  (atom {}))

(defn ^:private parse-records
  "Splits records which are delimited by double bars '||'"
  [rec]
  (let [record (util/str-split rec #"\|\|")]
    {(str/join ":" (butlast record)) (last record)}))

(defn ^:private load-mappings
  "Retrieves the text file containing the mapping data."
  [file]
  (log/info "Loading mapping file: " file)
  (try
    (as-> file $
      (str (cfg/get-config-first "mapping.path") "/" $ ".txt")
      (util/read-file $ io/file)
      (str/split-lines $)
      (map #(parse-records %) $)
      (apply merge $)
      (swap! mappings assoc (keyword file) $))
    
    (catch Exception ex
      (log/debug "Exception loading mapping file: " file)
      (log/error ex))))

(defn ^:private get-identity
  "The identity value for a point map request is the geocode type. If a request for a geocode comes in, that value should just be returned."
  [type id]
  (let [identity (set (cfg/get-config "pointmap.identity.values"))]
   (when (contains? identity type)
     id)))

(defn ^:private format-response
  "Formats the response data into the expected format."
  [type id geocode]
  {:type type
   :id id
   :geocode geocode})

(defn ^:private get-mapping
  "Checks the mappings atom for the provided file. If it doesn't exist, Load the mappings into the atom."
  [file lookup]
  (if-let [mapping ((keyword file) @mappings)]
    (get mapping lookup)
    (get ((keyword file) (load-mappings file)) lookup)))

(defn get-point
  "Fetches the geocode given a product key"
  [type id]
  (when-not (= "postalPlace" type) ;; postalPlace doesn't yield geocode; use get-postal-place to resolve this type
    (let [geocode (cond 
                    (= "placeid" type)     (first (util/str-split (place/get-place id) #":"))
                    (get-identity type id) id
                    :else                  (get-mapping type id))]
      (when geocode
        (format-response type id geocode)))))

(defn pre-load-mappings
  "Calls load-mappings for each mapping file configured to be pre-loaded to optimize searches."
  []
  (doseq [t (seq (cfg/get-config "pointmap.preloaded.mappings"))]
    (log/info (str "Preloading mapping: " t))
    (future (load-mappings t))))

(defn get-postal-place
  "Retrieves the postalPlace (or cityName) from the postalPlace mapping.
  This is a special mapping file which does not resolve to geocode."
  [key]
  (if-let [place (get-mapping "postalPlace" key)]
    place
    ;; trying to get simplified postal key 'G1L:fr:CA' instead of extended 'G1L 2R4:fr:CA' 
    (let [s (str/split key #":")
          f (first s)
          r (rest s)
          k (-> f (str/split #" ") first (cons r))]
      (get-mapping "postalPlace" (str/join ":" k)))))
