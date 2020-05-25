(ns location.utils.exception
  "Utility functiosn useful for formatting exceptions."
  (:require [location.error-message :as e]
            [clojure.tools.logging  :as log]))

(defn create-error-doc
  "Takes a function which produces an error document and associates it's meta-data status to it."
  [f]
  (let [err (f)]
    (merge {:body err}
           (meta err))))

; defmethods dispatching to classes can't have method descriptions!
(defmulti handle-server-error class)

; Timeout on the provider: Handled as a provider-error.
(defmethod handle-server-error
  org.httpkit.client.TimeoutException [e]
  (log/debug "org.httpkit.client.TimeoutException caught in handle-server-error: " e)
  (create-error-doc (partial e/get-error :unavailable)))

; Generic exception: Handled as a provider-error.
(defmethod handle-server-error
  java.lang.Exception [e]
  (log/debug "java.lang.Exception caught in handle-server-error: " e)
  (create-error-doc (partial e/get-error :provider-error)))

; String message: is also handled as a provider-error.
(defmethod handle-server-error
  java.lang.String [e]
  (log/debug "java.lang.String caught in handle-server-error: " e)
  (create-error-doc (partial e/get-error :provider-error)))

; nil: message is also handled as a provider-error.
(defmethod handle-server-error
  nil [e]
  (log/debug "nil caught in handle-server-error: " e)
  (create-error-doc (partial e/get-error :provider-error)))

; Default handler: a generic message is applied.
(defmethod handle-server-error
  :default [e]
  (log/debug ":default caught in handle-server-error: " e)
  (create-error-doc (partial e/get-error :server-error)))

(defn process-errors
  "Takes a collection of partial error functions and creates error docs for all of them. Merges them together into a single error response."
  [resp]
  (let [docs (if (map? resp) (vector resp) (map create-error-doc resp))
        errors (map :body docs)
        status (first (map :status docs))]
    {:body {:errors errors}
     :status status}))

(defn process-errors-v2
  "Takes a collection of partial error functions and creates error docs for all of them. Merges them together into a single error response."
  [resp]
  (let [docs (if (map? resp) (vector resp) (map create-error-doc resp))
        errors (map (comp :error :body) docs)
        status (first (map :status docs))]
    {:body {:success false 
            :errors errors}
     :status status}))
