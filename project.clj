(defproject com.opengrail/kafka-sse-clj "0.1.0-SNAPSHOT"
  :description "Provide HTML5 Server Sent Events for any Kafka topic"
  :url "https://github.com/raymcdermott/kafka-sse-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [aleph "0.4.1"]
                 [compojure "1.5.1"]
                 [environ "1.1.0"]
                 [org.apache.kafka/kafka_2.10 "0.10.0.1" :exclusions [log4j org.slf4j/slf4j-log4j12 org.slf4j/slf4j-api org.scala-lang/scala-library]]
                 [org.apache.kafka/kafka-clients "0.10.0.1" :exclusions [org.slf4j/slf4j-log4j12 org.slf4j/slf4j-api]]]
  :pedantic? :warn)