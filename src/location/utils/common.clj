(ns location.utils.common
  "A library of useful utility functions."
  (:import [java.net URLEncoder])
  (:require [clojure.string        :as str]
            [clojure.walk          :as walk]
            [clojure.tools.logging :as log]
            [clojure.data.json     :as json]
            [clojure.java.io       :as io]
            [clj-time.core         :as t]))

(def str-split 
  "Applies an empty string when calling split on a nil value."
  (fnil str/split ""))

(def sec->ms
  "Converts seconds to milliseconds."
  (partial * 1000))

(defn ^:private split-line
  "Splits the line of configuration into two parts on the first equal sign"                                                                               
  [line]
  (when-not (empty? line)
    (let [sign (str/index-of line "=")]
      [(subs line 0 sign)
       (subs line (+ 1 sign))])))

(defn url-encode
  "URL encodes the provided value. Replaces spaces in the original request with '+'."
  [value]
  (-> value
     (URLEncoder/encode "UTF-8")
     (str/replace "%2520" "+")))

(defn query-to-value-pairs
  "Splits a query string into a vector of vector pairs."
  [query]
  (when-not (str/blank? query)
    (reduce-kv (fn [c k v]
                 (let [pair (split-line v)]
                   (conj c pair)))
               [] (str-split query #"&"))))

(defn apply-nils
  "Fills in missing values with nils"
  [rec]
  (let [key (first rec)
        value (second rec)]
    (vector key value)))

(defn query-to-map
  "Takes a query string from a url and returns a map of keys and values."
  [query]
  (let [split (query-to-value-pairs query)
        pairs (map apply-nils split)]
      (->> pairs
           flatten
          (apply hash-map)
          walk/keywordize-keys)))

(def nil-query-to-map
  "Handles the case when there is no query string passed in; E.G. when the heartbeat route
  is requested."
  (fnil query-to-map ""))

(defn clojurize-json
  "Transforms a json string into a clojure map."
  [input]
  (try
    (-> input
        json/read-str
        walk/keywordize-keys)

    (catch Exception ex
      (log/info "Exception parsing " input)
      (log/debug ex))))

(def nillable-inc
  "Provides an implementation of inc which can be applied to nil values. Used for
  updating a key in a map that doesn't exist."
  (fnil inc 0))

(defn not-nil?
  "Composition of not and nil functions. Returns true if the argument is not a nil value."
  [arg]
  ((comp not nil?) arg))

(defn read-file
  "Attempts to read a file from the file system."
  ([file]
   (read-file file io/resource))

  ([file f]
   (try
     (some-> file
             f
             slurp)

     (catch java.io.FileNotFoundException ex
       (log/info "Exception reading " file)
       (log/debug ex)))))

(def ^:private pat (re-pattern "\\\""))
(def ^:private lang-code-pattern
  "Regex pattern for matching language codes."
  (re-pattern #"(\w+-?\w*):\s+(\w+-\w*)"))

(defn load-languages
  "Parses the provided language file and returns a mapping from locales to language codes."
  [lang]
  (log/info (str "Loading languages from " lang))

  (when-let [langs (read-file lang)]
    (as-> langs $
          (clojure.string/replace $ pat "")
          (str-split $ #"\n")
          (into [] (map #(rest (re-find lang-code-pattern %)) $))
          (reduce-kv (fn [c k v]
                       (if (empty? v)
                         c
                         (assoc c (keyword (str/lower-case (first v))) (name (second v)))))
                     {}
                     $))))

(def default-languages
  "A map of allowed languages and default mappings if the specific culture isn't supported."
  (load-languages "default-languages.json"))

(defn get-language
  "Checks the default-languages file for the given language. If it exists, return the value it's associated with. If not, check the first two letters for a default mapping."
  [lang]
  (if-let [l ((keyword lang) default-languages)]
    l
    ((keyword (second (re-find #"(\w+)-\w+" lang))) default-languages)))

(defn encode-string
  "Converts the provided string into an array of Bytes in the given encoding."
  [s encoding]
  (try
    (String. (.getBytes (str s) encoding))

    (catch java.io.UnsupportedEncodingException ex
      (log/info "Unsupported character set" encoding)
      (log/debug ex))))

(defn extract-language
  "Extracts the language value from a full culture code."
  [culture]
  (first (str-split culture #"[-_]")))

(defn nil-or-empty?
  "Returns nil if the provided data is nil, empty, or blank; returns the argument otherwise."
  [arg]
  (when-not (or (nil? arg)
                (and (coll? arg) (empty? arg))
                (and (string? arg) (str/blank? arg)))
    arg))

(defn ^:private char-range
  "Returns a sequence of characters in the given range (inclusive of start and end)."
  [start end]
  (map char (range (int start) (inc (int end)))))

(def num-char-range
  "The valid numberic characters."
  (char-range \0 \9))

(defn parse-number
  "If the given string can be parsed successfully as a number, return the new number.
  In all other cases, return the original parameter."
  [s]
  (if (and (string? s)
           (every? (into #{\- \. \+} num-char-range) s))
    (try
      (Long/parseLong s)
      (catch Exception e
        (try
          (Double/parseDouble s)
          (catch Exception ex
            s))))
    s))

(defn split-strip-conf
  "Splits the given string by ',' and removes extra \" and white-space from any value."
  [s]
  (as-> s $
        (str/replace $ #"\"" "")
        (str-split $ #",")
        (map str/trim $)
        (filter some? $)))

(defn read-config-value
  "If the value is a number, parse it. If not, split it into a seq."
  [[first-char :as s]]
  (let [items (split-strip-conf s)]
    (cond
     (or (= "true" s) (= "false" s)) (read-string s)
     (= \" first-char) items
     :else (let [[first-parsed :as parsed] (map parse-number items)]
             (if (and (= 1 (count parsed))
                      (number? first-parsed))
               first-parsed
               parsed)))))

(defn flatten-single-val
  "If the collection contains a single item, strip away the outer collection."
  [coll]
  (let [c (flatten coll)]
    (if (next c)
      c
      (first c))))

(defn pluralize-key
  "For the controller integration to work properly, some routes need to return a :key keyword that is plural when a seq is returned."
  [ks]
  (keyword (if (< 1 (count ks)) "keys" "key")))

(defn fpred
  "Returns a variable-arity function which will apply 'f' to its arguments if 'pred' is true
  for all its arguments, or 'result' otherwise.  Example:
  => (def *nil (fpred * number? nil))
  => (*nil 1 2 3)  ; => 6
  => (*nil 2 3 \"notnum\")  ; => nil "
  ([f pred result]
   (fn [& args]
     (if (every? pred args)
       (apply f args)
       result)))
  ([f pred]
   (fpred f pred nil)))

(defn map-comparator
  "Returns a comparator function that will order based on the order of the
  keys in sequential 's'. Any keys not in 's' will be at the end of the map."
  [s]
  (let [z (zipmap s (range (count s)))]
    (fn [a b]
      (let [ai (or (a z) (Integer/MAX_VALUE))
            bi (or (b z) (Integer/MAX_VALUE))]
        (if (= ai bi) (compare a b) (- ai bi))))))

(defn ->camel
  "Converts a string to camel case (\"thisIsCamelCase\").  Can be given a list
  of characters which should be used as delimeters; otherwise, it will by
  default use the hyphen and underscore as delimeters. Examples:

  (->camel \"soon_to_be_camel_case\")      ; => soonToBeCamelCase
  (->camel \"using.dot.as.delimeter\" \\.)  ; => usingDotAsDelimeter
  (->camel \"my^custom^case\" \\^)          ; => myCustomCase"
  [s & delims]
  (let [delims (or (not-empty delims) '(\- \_))
        regex (re-pattern (str "[" (apply str (map (partial str "\\") delims)) "]"))
        [h & t] (filter not-empty (str-split s regex))]
    (if (nil? t) h
        (str (str/lower-case h) (apply str (map str/capitalize t))))))


(defn camel->
  "Converts a string from camel case (\"thisIsACamelCaseExample\"). When a
  capital letter is encountered, the given delimeter will be placed before it.
  All letters in the result are lower case.  Examples:

  (camel-> \"thisWillSoonBeSnakeCase\" \\_)   ; => this_will_soon_be_snake_case
  (camel-> \"myStrangeCase\" \\~)             ; => my~strange~case
  (camel-> \"multipleCharsAllowed\" \"***\")   ; => multiple***chars***allowed  "
  [s delim]
  (if-let [delim (not-empty (str delim))]
    (let [st (-> (str/replace s #"([A-Z])" (str delim "$1"))
                 str/lower-case)]
      (if (re-find #"^[A-Z]" s)    ; if the original string started with [A-Z], then we have
          (subs st (count delim))  ; replaced it with 'delim[A-Z]', and need to strip off delim
          st))
    s))


(defn snake-to-camel
  "Converts a string from snake_case to camelCase.  See doc for ->camel."
  [s]
  (->camel s \_))

(defn kebab-to-camel
  "Converts a string from kebab-case to camel_case.  See doc for ->camel."
  [s]
  (->camel s \-))

(defn camel-to-snake
  "Converts a string from camelCase to snake_case.  See doc for camel-> for more details."
  [s]
  (camel-> s "_"))

(defn camel-to-kebab
  "Converts a string from camelCase to kebab-case.  See doc for camel-> for more details."
  [s]
  (camel-> s "-"))

(defn snake-to-kebab
  "Converts a string from snake_case to kebab-case.  Replaces any consecutive underscores
  with a single hyphen and removes leading and trailing hyphens."
  [s]
  (-> (str/replace s #"_+" "-")
      (str/replace #"^-|-$" "")
      str/lower-case))

(defn kebab-to-snake
  "Converts a string from kebab-case to snake_case.  Replaces any consecutive hyphens
  with a single underscore and removes leading and trailing underscores."  [s]
  (-> (str/replace s #"-+" "_")
      (str/replace #"^_|_$" "")
      str/lower-case))

(defn pascal->
  "Identical to camel->."
  [s delim]
  (camel-> s delim))

(defn ->pascal
  "Converts a string to PascalCase, which is identical to camelCase, except that the
  first letter is capitzlied.  See the doc for ->camel."
  [s & delims]
  (let [[h & t :as new-s] (if delims (->camel s delims) (->camel s))]
    (if (= new-s s) s
        (str (str/upper-case h) (apply str t)))))


(defn pascal-to-snake "Identical to camel-to-snake." [s] (camel-to-snake s))
(defn pascal-to-kebab "Identical to camel-to-kebab." [s] (camel-to-kebab s))
(defn snake-to-pascal "Identical to snake-to-camel, with first letter capitalized." [s] (->pascal s \_))
(defn kebab-to-pascal "Identical to kebab-to-camel, with first letter capitalized." [s] (->pascal s \-))

(defn round-num
  "Round a number to the given precision (number of significant digits).
  Note that if num is a Long (integer), the result will be a Double."
  [num precision]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* num factor)) factor)))

(defn combine-shapefile-fields-and-values
  [fields values]
  (reduce (fn [res [k v]]
            (merge res (if (map? v) v {k v})))
          {}
          (zipmap fields values)))

(defn unchunked-seq
  "Even though we process lazy sequences in clojure, by default the processing is done in chunks
  of 32 at a time, for performance reasons.  So, if you have an expensive function (especially
  something i/o bound like a network request) or a sequence that must be processed one at a time
  (perhaps you are checking if the first evaluates to nil before processing the next and you must
  guarantee that the second will not run if the first evaluates to nil), truly lazily, apply this
  function to the sequence first and then map over it."
  [s]
  (lazy-seq
    (when-let [[x] (seq s)]
      (cons x (unchunked-seq (rest s))))))

(defn abs
  "Computes the absolute value of a number."
  [n]
  (if (pos? n)
    n
    (* -1 n)))

(defn pow
  "Raise a to the power of b."
  [a b]
  (let [x (reduce * 1 (repeat (abs b) a))]
    (if (pos? b) x (/ 1 x))))

(defn double-precision
  "Given a number \"n\", return a number that is truncated at \"precision\" decimal places."
  [n precision]
  (when (number? n)
    (let [n (bigdec n)
          p (pow 10 precision)
          base (quot n 1)
          frac (-> n
                   (rem 1)
                   (* p)
                   (quot 1)
                   (/ p))]
      (double (+ base frac)))))

(defn double-value
  [n]
  ((fpred double number?) n))
