(ns location.providers.elastic
  (:use [location.geocode]
        [location.utils.common])
  (:require [clojure.string                       :as str]
            [location.config                      :as cfg]
            [clojure.algo.generic.functor         :as func]
            [clojure.core.memoize                 :as memo]
            [clojurewerkz.elastisch.rest          :as es]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index    :as esi]
            [clojurewerkz.elastisch.rest.response :as esr]
            [clojurewerkz.elastisch.query         :as q]
            [location.utils.common                :as util]
            [clojure.tools.logging                :as log]
            [clj-http.conn-mgr                    :as http]
            [ring.util.codec                      :refer [url-decode]])
  (:import [jline.console ConsoleReader]))

;;;; Elasticsearch connection information

(defn create-conn
  "Connect to the given uri. This is a persistent connection, managed by clj-http (apache)."
  ([uri]
   (let [timeout (cfg/get-config "geoLocation.lookup.elastic.timeout")
         threads (cfg/get-config "geoLocation.lookup.elastic.threads")
         conn-per-host (cfg/get-config "geoLocation.lookup.elastic.connperhost")]
     (es/connect uri {:connection-manager (http/make-reusable-conn-manager
                                            {:timeout timeout
                                             :threads threads
                                             :default-per-route conn-per-host})})))
  ([]
   (create-conn (or (System/getenv "ES_URL")
                    (cfg/get-config-first "geoLocation.lookup.elastic.endpoint")
                    "http://localhost:9200"))))


(def ^:private conn (delay (create-conn)))
(defn getconn [] @conn)


;;;; Elasticsearch index data

(defn mappings
  "Specifies document mappings."
  ([name]
   {(keyword name) {:properties {:adminDistrictName1 {:type "string"
                                                      :analyzer "standard"
                                                      :search_analyzer "standard"
                                                      :fields {:raw {:type "string"
                                                                     :analyzer "lowercase_phrase"}
                                                               :us_state_abbrev {:type "string"
                                                                                 :analyzer "us_state"}
                                                               :autocomplete {:type "string"
                                                                              :analyzer "autocomplete"}}}
                                 :adminDistrictName2 {:type "string"
                                                      :fields {:raw {:type "string"
                                                                     :analyzer "lowercase_phrase"}
                                                               :autocomplete {:type "string"
                                                                              :analyzer "autocomplete"}}}
                                 :adminDistrictName3 {:type "string"
                                                      :fields {:raw {:type "string"
                                                                     :analyzer "lowercase_phrase"}
                                                               :autocomplete {:type "string"
                                                                              :analyzer "autocomplete"}}}
                                 :adminDistrictName4 {:type "string"
                                                      :fields {:raw {:type "string"
                                                                     :analyzer "lowercase_phrase"}}}
                                 :adminDistrictName5 {:type "string"
                                                      :fields {:raw {:type "string"
                                                                     :analyzer "lowercase_phrase"}}}
                                 :adminDistrictCode1 {:type "string"}
                                 :adminDistrictCode2 {:type "string"}
                                 :adminDistrictCode3 {:type "string"}
                                 :adminDistrictCode4 {:type "string"}
                                 :adminDistrictCode5 {:type "string"}
                                 :placeName          {:type "string"
                                                      :fields {:raw {:type "string"
                                                                     :analyzer "lowercase_phrase"}
                                                               :word_count {:type "token_count"
                                                                            :store "yes"
                                                                            :analyzer "standard"}
                                                               :shingle {:type "string"
                                                                         :analyzer "shingle_analyzer"}
                                                               :autocomplete {:type "string"
                                                                              :analyzer "autocomplete"}
                                                               :auto_phrase {:type "string"
                                                                             :analyzer "autocomplete_phrase"}}}
                                 :placeType          {:type "string"
                                                      :index "no"}
                                 :continentName      {:type "string"}
                                 :continentCode      {:type "string"
                                                      :index "no"}
                                 :regionName         {:type "string"}
                                 :regionCode         {:type "string"
                                                      :index "no"}
                                 :countryName        {:type "string"}
                                 :countryCode        {:type "string"
                                                      :analyzer "lowercase_phrase"
                                                      :include_in_all false}
                                 :boost              {:type "short"
                                                      :include_in_all false}
                                 :pClass             {:type "short"
                                                      :include_in_all false}
                                 :lastUpdate         {:type "date"
                                                      :index "no"}
                                 :active             {:type "boolean"
                                                      :index "no"}
                                 :locId              {:type "string"
                                                      :index "no"}
                                 :languageCode       {:type "string"
                                                      :analyzer "lowercase_phrase"
                                                      :include_in_all false}
                                 :location           {:type "geo_point"}
                                 :postalCode         {:type "string"
                                                      :index "not_analyzed"}}}})
  ([]
   (mappings (cfg/get-config-first "geoLocation.lookup.elastic.type"))))


(defn index-settings
  "Specifies index settings, including filters and tokenizers"
  [shards replicas mappings]
  {:mappings mappings
   :settings {:number_of_shards shards
              :number_of_replicas replicas
              :analysis {:filter {:autocomplete_filter {:type "edge_ngram"
                                                        :min_gram 1
                                                        :max_gram 20}
                                  :shingle_filter {:type "shingle"
                                                   :min_shingle_size 2
                                                   :max_shingle_size 4}
                                  :us_state_filter {:type "synonym"
                                                    :synonyms ["alabama => al"
                                                               "alaska => ak"
                                                               "arizona => az"
                                                               "arkansas => ar"
                                                               "california => ca"
                                                               "colorado => co"
                                                               "connecticut => ct"
                                                               "delaware => de"
                                                               "florida => fl"
                                                               "georgia => ga"
                                                               "hawaii => hi"
                                                               "idaho => id"
                                                               "illinois => il"
                                                               "indiana => in"
                                                               "iowa => ia"
                                                               "kansas => ks"
                                                               "kentucky => ky"
                                                               "louisiana => la"
                                                               "maine => me"
                                                               "maryland => md"
                                                               "massachusetts => ma"
                                                               "michigan => mi"
                                                               "minnesota => mn"
                                                               "mississippi => ms"
                                                               "missouri => mo"
                                                               "montana => mt"
                                                               "nebraska => ne"
                                                               "nevada => nv"
                                                               "new hampshire => nh"
                                                               "new jersey => nj"
                                                               "new mexico => nm"
                                                               "new york => ny"
                                                               "north carolina => nc"
                                                               "north dakota => nd"
                                                               "ohio => oh"
                                                               "oklahoma => ok"
                                                               "oregon => or"
                                                               "pennsylvania => pa"
                                                               "rhode island => ri"
                                                               "south carolina => sc"
                                                               "south dakota => sd"
                                                               "tennessee => tn"
                                                               "texas => tx"
                                                               "utah => ut"
                                                               "vermont => vt"
                                                               "virginia => va"
                                                               "washington => wa"
                                                               "west virginia => wv"
                                                               "wisconsin => wi"
                                                               "wyoming => wy"]}}

                         :analyzer {:lowercase_phrase {:type "custom"
                                                       :tokenizer "keyword"
                                                       :filter "lowercase"}
                                    :autocomplete {:type "custom"
                                                   :tokenizer "standard"
                                                   :filter ["lowercase",
                                                            "autocomplete_filter"]}
                                    :autocomplete_phrase {:type "custom"
                                                          :tokenizer "keyword"
                                                          :filter ["lowercase",
                                                                   "autocomplete_filter"]}
                                    :us_state {:type "custom"
                                               :tokenizer "standard"
                                               :filter ["lowercase",
                                                        "us_state_filter"]}
                                    :shingle_analyzer {:type "custom"
                                                       :tokenizer "standard"
                                                       :filter ["lowercase",
                                                                "shingle_filter"]}}}}})



;;; Convenience functions for working with indexes and aliases

(defn update-alias
  "Removes the alias \"alias-name\" referring to \"from\" and adds an alias
  with the same name referring to \"to\"."
  [conn alias-name from to]
  (esi/update-aliases conn [{:remove {:index from :alias alias-name}}
                            {:add    {:index to   :alias alias-name}}]))

(defn remove-alias
  "Removes the alias \"alias-name\" referring to \"from\"."
  [conn alias-name index-name]
  (esi/update-aliases conn [{:remove {:index index-name :alias alias-name}}]))

(defn add-alias
  "Adds an alias named \"alias-name\" to refer to index \"index-name\"."
  [conn alias-name index-name]
  (esi/update-aliases conn [{:add {:index index-name :alias alias-name}}]))


(defn create-index
  "Creates an elasticsearch index with the given name and parameters."
  ([conn index type shards replicas]
   (esi/create conn
               index
               (index-settings shards replicas (mappings type))))
  ([conn]
   (create-index conn
                 (cfg/get-config-first "geoLocation.lookup.elastic.index")
                 (cfg/get-config-first "geoLocation.lookup.elastic.type")
                 (cfg/get-config "geoLocation.lookup.elastic.shards")
                 (cfg/get-config "geoLocation.lookup.elastic.replicas"))))


(defn delete-index
  "Deletes an elasticsearch index."
  ([conn index]
   (esi/delete conn index))
  ([conn]
   (esi/delete conn (cfg/get-config-first "geoLocation.lookup.elastic.index"))))


;; Profile functions

(def ^:private default-profile
  "Default weightings for several indexed fields."
  {:adn1 2
   :adn2 1.5
   :adn3 0.5
   :adn4 0.5
   :adn5 0.5
   :stateabbrev 1.2
   :place 1.5
   :placeshingle 2
   :placeauto 3
   :pclass 1
   :boost 1
   :region 1.5})

(defn get*
  "Gets the document specified by \"id\"."
  ([conn index type id]
   (:_source (esd/get conn index type id)))
  ([conn id]
   (get* conn
         (cfg/get-config-first "geoLocation.lookup.elastic.index")
         (cfg/get-config-first "geoLocation.lookup.elastic.type")
         id)))

(defn get-profile
  "Retrieves the profile associated with \"profile-name\" from the elastic
  node specified by \"conn\". If the profile does not exist, return the
  default profile.  If for some reason the default profile can't be
  retrieved from the index, return a local copy."
  ([conn index type profile-name]
   (try
     (if-let [profile (get* conn index type profile-name)]
       (merge default-profile profile)
       default-profile)
     (catch Exception e
       default-profile)))
  ([conn profile-name]
   (get-profile
     conn
     (cfg/get-config-first "geoLocation.lookup.elastic.profile.index")
     (cfg/get-config-first "geoLocation.lookup.elastic.profile.type")
     profile-name))
  ([conn]
   (get-profile
     conn
     (cfg/get-config-first "geoLocation.lookup.elastic.profile.default"))))

(def get-profile
  "Caches configuration settings."
  (memo/ttl
    get-profile
    :ttl/threshold (util/sec->ms (cfg/get-config "geoLocation.lookup.elastic.profile.ttl"))))

;; Boolean construct helpers

(defn ^:private bool
  "Bool specification."
  ([]
   (bool nil))
  ([q]
   (q/bool q)))

(defn ^:private bool-add-clause
  "Adds a clause to the given bool map."
  [bool type clause]
  (if (or (nil? type) (nil? clause))
    bool
    (update-in bool [:bool type] (fnil conj []) clause)))

(defn ^:private bool-or
  "Adds to the \"should\" portion of a boolean query."
  ([bool clause]
   (bool-add-clause bool :should clause))
  ([clause]
   {:should clause}))

(defn ^:private bool-and
  "Adds to the \"must\" portion of a boolean query."
  ([bool clause]
   (bool-add-clause bool :must clause))
  ([clause]
   {:must clause}))

(defn ^:private bool-not
  "Adds to the \"must_not\" portion of a boolean query."
  ([bool clause]
   (bool-add-clause bool :must_not clause))
  ([clause]
   {:must_not clause}))

(defn ^:private filter-clause
  "Adds to the \"filter\" portion of a boolean query."
  ([bool clause]
   (bool-add-clause bool :filter clause))
  ([clause]
   {:filter clause}))


;; General helpers

(defn ^:private multi-match
  "Returns an elasticsearch multi-match query specification."
  ([query fieldv opts]
   {:multi_match (merge {:fields fieldv :query query}
                        opts)}))
(defn ^:private query
  "Query specification."
  ([]
   (query nil))
  ([q]
   {:query q}))

(defn ^:private filter*
  "Filter specification."
  ([]
   (filter* nil))
  ([f]
   {:filter f}))

(defn ^:private search-endpoint-request-body
  "Top-level of search request."
  []
  {:size (cfg/get-config "geoLocation.lookup.elastic.maxResults")
   :query nil})


;; Functions for use with Elastic's "function_score" query type

(defn ^:private function-score
  "Function score query type."
  ([query funcs score-mode boost-mode]
   {:function_score (merge query {:functions (or funcs [])
                                  :score_mode (or score-mode "sum")
                                  :boost_mode (or boost-mode "sum")})})
  ([]
   (function-score (query (q/match-all)) nil nil nil)))

(defn ^:private func-filter-weight
  "Spec for a function_score weight function -- if \"clause\" is a match, a
  \"weight\" is applied to it as determined by the \"score_mode\" function score option."
  [clause weight]
  {:filter clause
   :weight weight})

(defn ^:private func-boost-field
  "Specification for the \"field_value_factor\" function, which uses a field
  in the document to add weight to a query."
  ([field modifier factor]
   {:field_value_factor {:field field
                         :modifier (or modifier "none")
                         :factor (or factor 1)
                         :missing 1}})
  ([field]
   (func-boost-field field nil nil)))

(defn ^:private func-script-score
  "Spec for the function script_score query type."
  [script]
  {:script_score {:script script}})

(defn ^:private default-filter
  "Elasticsearch compatible query for the default yes/no match on a particular query."
  [query]
  (-> (bool)
      (bool-or (q/term "postalCode" query))
      (bool-or (multi-match
                 query
                 ["*.autocomplete"
                  "adminDistrictName1.us_state_abbrev"
                  "adminDistrictName4"
                  "adminDistrictName5"
                  "regionName"]
                 {:type "cross_fields"
                  :analyzer "standard"
                  :operator "and"
                  :minimum_should_match "100%"}))))

(defn ^:private function-score-filter
  "Used in the function-score query as an initial filter--as with all filters, it is not scored."
  [lookup & filters]
  (filter* (-> (reduce #(bool-and %1 %2) (bool) filters)
               (bool-and (default-filter lookup)))))

(defn ^:private function-score-functions
  "Vector of functions that affect the score."
  [query {profile :profile :as req}]
  (let [profile-map (get-profile (getconn) profile)
        {:keys [adn1 adn2 adn3 adn4 adn5 stateabbrev place
                placeshingle placeauto pclass boost region]} (merge profile-map req)]
    [(func-boost-field "pClass" "log1p" pclass)
     (func-boost-field "boost" "ln1p" boost) ;can be: none, log, log1p, log2p, ln, ln1p, ln2p
     (func-filter-weight (q/match "placeName"             query) place)
     (func-filter-weight (q/match "placeName.shingle"     query) placeshingle)
     (func-filter-weight (q/match "placeName.auto_phrase" query {:analyzer "lowercase_phrase"
                                                                 :type "phrase_prefix"}) placeauto)
     (func-filter-weight (q/match "regionName"            query) region)
     (func-filter-weight (q/match "adminDistrictName1"    query) adn1)
     (func-filter-weight (q/match "adminDistrictName1.us_state_stateabbrev" query {:analyzer "standard"}) stateabbrev)
     (func-filter-weight (q/match "adminDistrictName2"    query) adn2)
     (func-filter-weight (q/match "adminDistrictName3"    query) adn3)
     (func-filter-weight (q/match "adminDistrictName4"    query) adn4)
     (func-filter-weight (q/match "adminDistrictName5"    query) adn5)]))


(defn ^:private get-geo-function-score-query
  "Retrieves the request for the default function score query."
  [query- {:keys [language countryCode] :as req}]
  (let [lang-filter (if language (q/term "languageCode" (str/lower-case language)))
        country-filter (if countryCode (q/term "countryCode" (str/lower-case countryCode)))]
    (merge (search-endpoint-request-body)
           (query (function-score
                    (function-score-filter query- lang-filter country-filter)
                    (function-score-functions query- req)
                    "sum"
                    "sum")))))


(declare search)
(defn ^:private typeahead-demo-search
  "Formats the response from the typeahead search tool."
  [conn lookup]
  (let [hits (search conn lookup {:language "en-us"})
        f (fn [m] (let [src (:_source m)
                        score (:_score m)
                        place (:placeName src)
                        ad1 (:adminDistrictName1 src)
                        ad2 (:adminDistrictName2 src)
                        id (:_id m)
                        boost (:boost src)
                        pop (:pClass src)]
                    (format "%-11f %-18s %-18s %-20s %-15s %5d %3d" score place ad1 ad2 id pop boost)))]
    (map f hits)))


(defn typeahead-demo
  "Performs a character-by-character search demo to see scores and other info.  To use,
  load repl with 'lein trampoline repl' and run this function."
  []
  (flush)
  (print "\nSearch text: ")
  (flush)
  (loop [s ""]
    (let [c (.readCharacter (ConsoleReader.))
          s (if (= 127 c) (apply str (butlast s)) (str s (char c)))]
      (if (> (count s) 1)
        (do
          (let [result (typeahead-demo-search (getconn) s)]
            (flush)
            (print (format "\n\n%-11s %-18s %-18s %-20s %-15s %5s %3s\n%-11s %-18s %-18s %-20s %-15s %5s %3s\n"
                           "Score" "Place Name" "Admin Dist 1" "Admin Dist 2" "Doc ID" "Pop" "Bst"
                           "-----" "----------" "------------" "------------" "------" "---" "---"))
            (flush)
            (dorun (map println result))
            (flush))))
      (flush)
      (print "\nSearch text: " s)
      (flush)
      (recur s))))



;; The main entry point into searching via elasticsearch

(defn search-raw
  "Performs a geo search. The \"req\" map specifies options to use such as language, countryCode,
  format, and weightings for various fields."
  ([conn query]
   (search-raw conn query nil))
  ([conn query req]
   (search-raw conn query req
               (cfg/get-config-first "geoLocation.lookup.elastic.index")
               (cfg/get-config-first "geoLocation.lookup.elastic.type")))
  ([conn query req index type]
   (let [es-query (get-geo-function-score-query (str/trim (url-decode query)) req)]
     (log/info (str "\n\n***** Query *****"
                    "\nIndex: " index
                    "\nType: " type "\n\n"
                    (with-out-str (clojure.data.json/pprint es-query))))
     (esd/search conn index type es-query))))

(defn search
  [conn query req]
  (let [result (search-raw conn query req)]
    (when (esr/any-hits? result)
      (map :_source (esr/hits-from result)))))
