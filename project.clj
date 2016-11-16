(defproject com.opengrail/kafka-sse-clj "0.1.3"
  :description "Provide HTML5 Server Sent Events for any Kafka topic"
  :url "https://github.com/raymcdermott/kafka-sse-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["clojars" {:url "http://clojars.org/repo/"
                             :sign-releases false}]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [environ "1.1.0"]
                 [org.apache.kafka/kafka-clients "0.10.0.1"]]
  :pedantic? :abort)