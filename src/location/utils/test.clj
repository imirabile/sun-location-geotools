(ns location.utils.test
  "Useful functions for unit testing."
  (:require [clojure.test          :refer :all]
            [clojure.java.io       :as io]
            [location.utils.common :as util]))

(defn equal?
  "Compares two values for equality."
  [expected actual]
  (is (= expected actual)))

(defn not-nil?
  "Checks input for nil."
  [arg]
  (is (util/not-nil? arg)))

(defn ^:private message-contains
  "Checks the error message for the presence of a phrase."
  [phrase message]
  (re-find phrase message))

(defn in-message?
  "Checks if the phrase is in the given message."
  [phrase message]
  (not-nil? (message-contains phrase message)))

(defn get-key
  "Fetches the product key value from the provided response."
  [key resp]
  (let [k (first (filter #(= (:product %) key) resp))]
    (if (contains? k :keys) 
      (:keys k)
      (:key k))))

(defn create-test-file
  "Creates a temporary file with which to test i/o functions."
  [content ext]
  (let [filename (str (gensym) ext)]
    (spit filename content)
    filename))

(defn remove-test-file
  "Deletes a file created for testing."
  [filename]
  (io/delete-file filename))
