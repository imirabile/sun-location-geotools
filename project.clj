(defproject location (or (System/getenv "LOCATION_VERSION") "develop")
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/algo.generic "0.1.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.memoize "0.5.8"]
                 [clojurewerkz/elastisch "3.0.0-beta1"]
                 [clj-time "0.11.0"]
                 [instaparse "1.4.0" :exclusions [org.clojure/clojure]]
                 [compojure "1.3.1"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-codec "1.0.0"]
                 [lein-ring "0.9.6"]
                 [metrics-clojure "2.6.0"]
                 [metrics-clojure-ring "2.0.0"]
                 [crypto-random "1.2.0"]
                 [rm-hull/ring-gzip-middleware "0.1.7"]
                 [org.geotools/gt-main "23.0"]
                 [org.geotools/gt-shapefile "23.0"]
                 [org.geotools/gt-geopkg "23.0"]
                 [org.geotools/gt-cql "23.0"]
                 [org.geotools/gt-data "16.2"]
                 [com.vividsolutions/jts "1.11"]
                 [org.apache.logging.log4j/log4j-core "2.5"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.5"]
                 [cc.qbits/alia "3.2.0"]
                 [http-kit "2.1.18"]
                 [clj-http "3.2.0"]
                 [jline "2.11"]]
  :plugins [[lein-ring "0.8.13"]
            [lein-auto "0.1.2"]
            [lein-codox "0.9.0"]
            [lein-pprint "1.1.1"]
            [io.sarnowski/lein-docker "1.0.0"]]
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (constantly true)}
  ;;:repositories {"osgeo-geotools" "http://download.osgeo.org/webdav/geotools"}
  :repositories {"osgeo-geotools" "https://repo.osgeo.org/repository/release/"}
  :resource-paths ["resources/etc" "resources/mappings"]
  :ring {:handler location.handler/app}
  :main ^:skip-aot location.handler
  :uberjar-merge-with {#"org\.opengis\.referencing\.crs\.*" [slurp str spit]}
  :profiles {:stable   {:dependencies [[org.clojure/clojure "1.8.0"]
                                       [clj-time "0.12.0"]
                                       [instaparse "1.4.2" :exclusions [org.clojure/clojure]]
                                       [compojure "1.5.1"]
                                       [ring/ring-jetty-adapter "1.5.0"]
                                       [ring/ring-codec "1.0.1"]
                                       [lein-ring "0.9.7"]
                                       [metrics-clojure "2.7.0"]
                                       [metrics-clojure-ring "2.7.0"]
                                       [org.geotools/gt-main "23.0"]
                                       [org.geotools/gt-shapefile "23.0"]
                                       [org.geotools/gt-cql "23.0"]
                                       [org.geotools/data "16.2"]
                                       [com.vividsolutions/jts "1.11"]
                                       [org.apache.logging.log4j/log4j-core "2.6.1"]
                                       [org.apache.logging.log4j/log4j-slf4j-impl "2.6.1"]
                                       [javax.servlet/servlet-api "2.5"]
                                       [ring-mock "0.1.5"]]
                        :plugins      [[lein-ring "0.9.7"]
                                       [lein-codox "0.9.5"]]}
             :bleeding {:dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                                       [org.clojure/test.check "0.9.0"]
                                       [ring/ring-jetty-adapter "1.6.0-beta1"]
                                       [clojurewerkz/elastisch "3.0.0-beta1"]]}
             :dev {:dependencies     [[javax.servlet/servlet-api "2.5"]
                                      [ring-mock "0.1.5"]]}
             :uberjar {:aot :all}}
  :docker {:image-name "twc-sun-core-docker-local.jfrog.io/sun-ms-location"
           :build-dir  ""})
