(ns location.metrics
  "Provides utilities to measure application performance. Def'ing a value for each route seems tedious and error prone. When time allows, rework so routes are automatically picked up, if possible."
  (:require [metrics.core     :refer [new-registry]]
            [metrics.counters :refer [counter defcounter inc! dec! value]]
            [metrics.timers   :as t]
            [metrics.meters   :as m]))

(def ^:private registry 
  "The clj-metrics registry all metrics data is associated with."
  (new-registry))

(def reverse-geo
  "Meter and time for the geocoding/reverse end point."
  {:timer (t/timer registry "reverse-geo-timer")
   :meter (m/meter registry "reverse-geo-meter")})

(def forward-geo
  "Meter and time for the geocoding/forward end point."
  {:timer (t/timer registry "forward-geo-timer")
   :meter (m/meter registry "forward-geo-meter")})

(def ip
  "Meter and time for the iplookup end point."
  {:timer (t/timer registry "ip-timer")
   :meter (m/meter registry "ip-meter")})

(def map-provider
  "Meter and time for calls to the configured mapping provider."
  {:timer (t/timer registry "map-provider-timer")
   :meter (m/meter registry "map-provider-meter")})

(def ip-provider
  "Meter and time for calls to the ip lookup provider."
  {:timer (t/timer registry "ip-provider-timer")
   :meter (m/meter registry "ip-provider-meter")})

(def hit-test
  "Meter and time for the geohittest end point."
  {:timer (t/timer registry "hit-test-timer")
   :meter (m/meter registry "hit-test-meter")})

(def point-map
  "Meter and time for the pointmap end point."
  {:timer (t/timer registry "point-map-timer")
   :meter (m/meter registry "point-map-meter")})

(def resolve
  "Meter and time for the resolve end point."
  {:timer (t/timer registry "resolve-timer")
   :meter (m/meter registry "resolve-meter")})

(def map-point
  "Meter and time for the geo-lookup end point."
  {:timer (t/timer registry "map-point-timer")
   :meter (m/meter registry "map-point-meter")})

(def search
  "Meter and time for the search end point."
  {:timer (t/timer registry "search-timer")
   :meter (m/meter registry "search-meter")})

(def boundary
  "Meter and time for the boundary end point."
  {:timer (t/timer registry "boundary-timer")
   :meter (m/meter registry "boundary-meter")})

(def intersection
  "Meter and time for the intersection end point."
  {:timer (t/timer registry "intersection-timer")
   :meter (m/meter registry "intersection-meter")})

(def catalog
  "Meter and time for the catalog end point."
  {:timer (t/timer registry "catalog-timer")
   :meter (m/meter registry "catalog-meter")})

(def near
  "Meter and time for the near end point."
  {:timer (t/timer registry "near-timer")
   :meter (m/meter registry "near-meter")})

(def typeaheadtuner
  {:timer (t/timer registry "typeaheadtuner-timer")
   :meter (m/meter registry "typeaheadtuner-meter")})

(defn ^:private nano-to-milli
  "Converts a value in milliseconds given nanoseconds."
  [nano]
  (double (/ nano 1000000)))

(defn time-fn 
  "Wraps the given function f in a metrics-clojure timer."
  [metric f args]
  (m/mark! (:meter metric))
  (t/time! (:timer metric) (f args)))

(defn get-metric 
  "Retrieves the metric information stored in this registry for timer t."
  [metric]
  (let [timer (:timer metric)
        meter (:meter metric)
        percents (t/percentiles timer [0.50 0.75 0.99 0.999])
        rates (m/rates meter)]

    {:count (t/number-recorded timer)
     :max (nano-to-milli (t/largest timer))
     :mean (nano-to-milli (t/mean timer))
     :min (nano-to-milli (t/smallest timer))
     :p50 (nano-to-milli (get percents 0.50))
     :p75 (nano-to-milli (get percents 0.75))
     :p99 (nano-to-milli (get percents 0.99))
     :p999 (nano-to-milli (get percents 0.999))
     :stddev (nano-to-milli (t/std-dev timer))
     :m15_rate (get rates 15)
     :m1_rate (get rates 1)
     :m5_rate (get rates 5)
     :mean_rate (m/rate-mean meter)
     :duration_units "milliseconds"
     :rate_units "calls/second"}))

(defn get-metrics
  "Returns a map of all configured metrics with their associated data."
  []
  {:geocoding (get-metric search)
   :ip (get-metric ip)
   :hit-test (get-metric hit-test)
   :point-map (get-metric point-map)
   :resolve (get-metric resolve)
   :map-point (get-metric map-point)
   :boundary (get-metric boundary)
   :intersection (get-metric intersection)
   :catalog (get-metric catalog)
   :near (get-metric near)
   :provider {:map (get-metric map-provider)
              :ip (get-metric ip-provider)}
   :typeaheadtuner (get-metric typeaheadtuner)})
