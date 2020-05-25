(ns location.geocoder.forward
  (:require [location.providers.elastic :as elastic]
            [clojure.tools.logging      :as log]
            [location.config            :as cfg]
            [location.utils.common      :as util]
            [location.utils.exception   :refer [create-error-doc]]))


(defn get-loc-by-id
  [id req]
  (when-let [doc (elastic/get* (elastic/getconn) id)]
    [doc]))

(defn forward-geo
  "Performs forward geocoding using search term(s) specified in 'lookup'."
  [query req hits-only]
  (try
    (when (cfg/get-config "geoLocation.lookup.elastic.enable")
      (let [req (update req :language util/extract-language)]
        (if hits-only
          (elastic/search (elastic/getconn) query req)
          (elastic/search-raw (elastic/getconn) query req))))

    (catch java.lang.Exception e
      (condp = (type e)
        org.elasticsearch.client.transport.NoNodeAvailableException
        (log/error "Exception running forward geocoder: using native protocol, and unable to connect to the host." e)

        java.net.ConnectException
        (log/error "Exception running forward geocoder using REST protocol.  The host was found, but "
                   "location services couldn't actually connect.  It is likely that the host is running, "
                   "but elasticsearch (or other provider) is not running, or is running on a port other than "
                   "the one location services is trying to connect to." e)

        java.net.UnknownHostException
        (log/error "Exception running forward geocoder using REST protocol. The host running the forward "
                   "geocoder (likely elasticsearch) could not be found." e)
    
        clojure.lang.ExceptionInfo
        (let [info (.getData e)
              status (:status info)
              reason (:reason-phrase info)]
          (log/error (str "\n\nException running forward geocoder:\n"
                          (if (= 403 status)
                            "The status returned was 403, which very likely means that there is a problem with AWS authentication.\n")
                          reason "\n"
                          "Status: " status "\n"
                          info "\n\n")
                     e))

        (log/error "Exception running forward geocoder using REST protocol. Presumably location services "
                   "connected to the host and found an elasticsearch node running, and was able to authenticate, "
                   "but some other error occurred." e))
      (create-error-doc (partial location.error-message/get-error :default)))))
