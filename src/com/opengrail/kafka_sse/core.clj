(ns com.opengrail.kafka-sse.core
  (:require [clojure.core.async :as async :refer [>! <! go-loop chan close! timeout]]
            [clojure.string :as str]
            [environ.core :refer [env]])
  (:import (org.apache.kafka.common.serialization StringDeserializer)
           (org.apache.kafka.clients.consumer KafkaConsumer)
           (org.apache.kafka.common TopicPartition)
           (java.util UUID)
           (org.apache.kafka.clients CommonClientConfigs)))

(def CONSUME_LATEST -1)

(defn- env-or-default [env-var-name default]
  (if-let [env-var (env env-var-name)] env-var default))

(def ^:private poll-timeout-millis (env-or-default :sse-proxy-poll-timeout-millis 100))

(def ^:private buffer-size (env-or-default :sse-proxy-buffer-size 512))

(def ^:private keep-alive-millis (env-or-default :sse-proxy-keep-alive-millis (* 5 1000)))

(def ^:private local-brokers {CommonClientConfigs/BOOTSTRAP_SERVERS_CONFIG "localhost:9092"})

(def ^:private brokers-from-env (if-let [u (env :sse-proxy-kafka-broker-url)]
                                  {CommonClientConfigs/BOOTSTRAP_SERVERS_CONFIG u}))

(def ^:private kafka-brokers (or brokers-from-env local-brokers))

(def ^:private marshalling-config {"key.deserializer"   (.getCanonicalName StringDeserializer)
                                   "value.deserializer" (.getCanonicalName StringDeserializer)})

(def ^:private autocommit-config {"enable.auto.commit" "false"})

(def ^:private proxy-group (str "kafka-proxy-" (UUID/randomUUID)))

(defn sse-consumer
  "Obtain an appropriately positioned kafka consumer that is ready to be polled"
  ([topic-name offset]
   (sse-consumer topic-name offset kafka-brokers))

  ([topic-name offset brokers]
   (sse-consumer topic-name offset brokers autocommit-config))

  ([topic-name offset brokers options]
   (sse-consumer topic-name offset brokers options marshalling-config))

  ([topic-name offset brokers options marshallers]
   {:pre [(or (= offset CONSUME_LATEST) (>= offset 0))]}
   (let [consumer-group {"group.id" (str proxy-group "-" (rand))}
         merged-options (merge options autocommit-config consumer-group)
         consumer (KafkaConsumer. (merge marshallers merged-options brokers))]

     (if (= offset CONSUME_LATEST)
       (.subscribe consumer [topic-name])
       (let [partition (TopicPartition. topic-name 0)]
         (.assign consumer [partition])
         (.seek consumer partition offset)))

     consumer)))

(defn consumer-record->sse
  "Convert a Kakfa Java API ConsumerRecord to the HTML5 EventSource format"
  [consumer-record]
  (str "id: " (.offset consumer-record) "\n"
       "event: " (or (.key consumer-record) "") "\n"
       "data: " (or (.value consumer-record) "") "\n\n"))

(defn name-matches?
  "Match name with the regexes in a comma separated string"
  [regex-str name]
  (let [rxs (map #(re-pattern %) (str/split regex-str #","))
        found (filter #(re-find % name) rxs)]
    (> (count found) 0)))

(defn kafka-consumer->sse-ch
  "Creates a channel with the transducer to read from the consumer."

  ([consumer transducer]
   (let [kafka-ch (chan buffer-size transducer)]

     (go-loop []
       (if-let [records (.poll consumer poll-timeout-millis)]
         (doseq [record records]
           (>! kafka-ch record)))
       (recur))

     kafka-ch))

  ([consumer transducer keep-alive?]
   "Optionally creates an additional channel to emit SSE comments to keep the connection open."
   (if (not keep-alive?)
     (kafka-consumer->sse-ch consumer transducer)
     (let [keep-alive-ch (chan)
           kafka-ch (kafka-consumer->sse-ch consumer transducer)]

       (go-loop []
         (let [_ (<! (timeout keep-alive-millis))]
           (>! keep-alive-ch ":\n")
           (recur)))

       (async/merge [kafka-ch keep-alive-ch])))))

(defn kafka->sse-ch
  "Creates a channel that filters and maps data from a Kafka topic to the HTML5 EventSource format"
  ([topic-name]
   (kafka->sse-ch topic-name CONSUME_LATEST))
  ([topic-name offset]
   (kafka->sse-ch topic-name offset ".*"))
  ([topic-name offset event-filter-regex]
   (kafka->sse-ch topic-name offset event-filter-regex true))
  ([topic-name offset event-filter-regex keep-alive?]
   (let [consumer (sse-consumer topic-name offset)
         transducer (comp (filter #(name-matches? event-filter-regex (.key %)))
                          (map consumer-record->sse))]
     (kafka-consumer->sse-ch consumer transducer keep-alive?))))


