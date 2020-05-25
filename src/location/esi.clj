(ns location.esi
  "Provides functions for creating esi encoded documents."
  (:require [clojure.string :as str]))

(def ^:private var-start 
  "The start tag for an esi:vars block" 
  "<esi:vars>")

(def ^:private var-end 
  "The ending tag for an esi:vars block" 
  "</esi:vars>")

(def ^:private assign-start 
  "The start tag for an esi:assign block" 
  "<esi:assign")

(def ^:private assign-end 
  "The ending tag for an esi:assign block" 
  " />")

(defn ^:private esi-str-encode
  "Wraps string in the provided quote."
  [s & q]
  (if-let [quo (first (flatten q))]
      (esi-str-encode (str quo s quo) (rest q))
      s))


(defn ^:private esi-coll-encode
  "Transforms a clojure vector into an esi collection string."
  [coll]
  (as-> coll $
      (map #(esi-str-encode % "'''") $)
      (str/join ", " $)
      (str "[" $ "]")))

(defn vars
  "Creates an esi vars tag and wraps the arg in it."
  [args]
  (str var-start (apply str args) var-end))

(defn assign
  "Creates an esi assign tag with the provided key and value wrapped within it. Due to some odd rules for ESI, if val is a string, it is run through the string encoder twice. This is needed to produce the correct output."
  [key val]
  (let [value (esi-str-encode (if (instance? clojure.lang.ISeq val) 
                                (esi-coll-encode (vec val)) 
                                (esi-str-encode val "'''"))
                              \")]
    (str assign-start " name=" (esi-str-encode key \") " value=" value assign-end)))

(defn encode
  "Encodes the map into an esi xml document."
  [data]
  (let [resp (dissoc data :metadata)]
    (vars (reduce-kv (fn [c k v] 
                      (conj c (assign (name k) v)))
                     ()
                     resp))))
