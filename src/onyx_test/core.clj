(ns onyx-test.core
  (:require
   [clojure.core.async :as >]
   [onyx.api :as onyx]
   [onyx.plugin.core-async :refer [take-segments!]]))

(def outer-workflow
  [[:outer-in :body]
   [:body :outer-out]])

(def inner-workflow
  [[:inner-in :a]
   [:inner-in :b]
   [:inner-in :c]
   [:a :inner-out]
   [:b :inner-out]
   [:c :inner-out]])

(def capacity 1000)
(def batch-size 1000)

(def channels
  {:outer
   {:input (>/chan capacity)
    :output (>/chan capacity)}
   :inner
   {:input (>/chan capacity)
    :output (>/chan capacity)}})

(defn inject-channel
  [channel event lifecycle]
  {:core.async/chan channel})

(def outer-in-calls
  {:lifecycle/before-task-start
   (partial inject-channel (get-in channels [:outer :input]))})

(def outer-out-calls
  {:lifecycle/before-task-start
   (partial inject-channel (get-in channels [:outer :output]))})

(def inner-in-calls
  {:lifecycle/before-task-start
   (partial inject-channel (get-in channels [:inner :input]))})

(def inner-out-calls
  {:lifecycle/before-task-start
   (partial inject-channel (get-in channels [:inner :output]))})

(def outer-lifecycles
  [{:lifecycle/task :outer-in
    :lifecycle/calls :onyx-test.core/outer-in-calls}
   {:lifecycle/task :outer-in
    :lifecycle/calls :onyx.plugin.core-async/reader-calls}
   {:lifecycle/task :outer-out
    :lifecycle/calls :onyx-test.core/outer-out-calls}
   {:lifecycle/task :outer-out
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}])

(def inner-lifecycles
  [{:lifecycle/task :inner-in
    :lifecycle/calls :onyx-test.core/inner-in-calls}
   {:lifecycle/task :inner-in
    :lifecycle/calls :onyx.plugin.core-async/reader-calls}
   {:lifecycle/task :inner-out
    :lifecycle/calls :onyx-test.core/inner-out-calls}
   {:lifecycle/task :inner-out
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}])

(def outer-catalog
  [{:onyx/name :outer-in
    :onyx/type :input
    :onyx/batch-size batch-size
    :onyx/plugin :onyx.plugin.core-async/input
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/pending-timeout 60000}
   {:onyx/name :body
    :onyx/type :function
    :onyx/batch-size batch-size
    :onyx/fn :onyx-test.core/body}
   {:onyx/name :outer-out
    :onyx/type :output
    :onyx/batch-size batch-size
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/medium :core.async
    :onyx/max-peers 1}])

(def inner-catalog
  [{:onyx/name :inner-in
    :onyx/type :input
    :onyx/batch-size batch-size
    :onyx/plugin :onyx.plugin.core-async/input
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/pending-timeout 60000}
   {:onyx/name :a
    :onyx/type :function
    :onyx/batch-size batch-size
    :onyx/fn :onyx-test.core/a}
   {:onyx/name :b
    :onyx/type :function
    :onyx/batch-size batch-size
    :onyx/fn :onyx-test.core/b}
   {:onyx/name :c
    :onyx/type :function
    :onyx/batch-size batch-size
    :onyx/fn :onyx-test.core/c}
   {:onyx/name :inner-out
    :onyx/type :output
    :onyx/batch-size batch-size
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/medium :core.async
    :onyx/max-peers 1}])

(def outer-job
  {:catalog outer-catalog
   :workflow outer-workflow
   :lifecycles outer-lifecycles
   :task-scheduler :onyx.task-scheduler/balanced})

(def inner-job
  {:catalog inner-catalog
   :workflow inner-workflow
   :lifecycles inner-lifecycles
   :task-scheduler :onyx.task-scheduler/balanced})

(def onyx-id (java.util.UUID/randomUUID))

(def env-config
  {:zookeeper/address "127.0.0.1:2181"
   :zookeeper/server? false
   :zookeeper.server/port 2181
   :onyx/id onyx-id})

(def peer-config
  {:zookeeper/address "127.0.0.1:2181"
   :onyx/id onyx-id
   :onyx.peer/job-scheduler :onyx.job-scheduler/balanced
   :onyx.messaging/impl :aeron
   :onyx.messaging.aeron/embedded-driver? true
   :onyx.messaging/peer-port-range [40600 40800]
   :onyx.messaging/bind-addr "localhost"})

(def n-peers 15)

(defn start-onyx
  []
  (let [env (onyx.api/start-env env-config)
        peer-group (onyx.api/start-peer-group peer-config)
        v-peers (onyx.api/start-peers n-peers peer-group)]
    (fn shutdown-onyx
      []
      (doseq [v-peer v-peers]
        (onyx.api/shutdown-peer v-peer))
      (onyx.api/shutdown-peer-group peer-group)
      (onyx.api/shutdown-env env)
      (shutdown-agents))))

(def inner-segments
  [{:inner "what"}
   {:inner "context"}
   {:inner "is"}
   {:inner "this?"}])

(defn body
  [segment]
  (let [{:keys [inner]} channels]
    (println "OUTER!" segment)
    (onyx/submit-job peer-config inner-job)
    (println "SUBMITTING INNER")
    (>/go
      (doseq [inner-segment inner-segments]
        (println "INPUT INNER SEGMENT" inner-segment)
        (>/>! (:input inner) inner-segment))
      (>/>! (:input inner) :done))
    (let [results (take-segments! (:output inner))]
      (println "INNER SEGMENTS COMPLETE!")
      (clojure.pprint/pprint results)
      (-> segment
          (update :outer (partial str "prefix-"))
          (assoc :inner results)))))

(defn a
  [segment]
  (println "AAAAAAAAAAAAA")
  (Thread/sleep (rand-int 1000))
  (update segment :inner (partial str "a-")))

(defn b
  [segment]
  (println "BBBBBBBBBBBBB")
  (Thread/sleep (rand-int 1000))
  (update segment :inner (partial str "b-")))

(defn c
  [segment]
  (println "CCCCCCCCCCCCC")
  (Thread/sleep (rand-int 1000))
  (update segment :inner (partial str "c-")))

(defn launch
  []
  (let [{:keys [outer inner]} channels]
    (println "SUBMITTING OUTER")
    (onyx/submit-job peer-config outer-job)
    (>/go
      (println "INPUT OUTER SEGMENTS")
      (>/>! (:input outer) {:outer "suffix"})
      (>/>! (:input outer) :done))
    (let [results (take-segments! (:output outer))]
      (println "OUTER SEGMENTS COMPLETE!")
      results)))

(defn run-jobs
  []
  (let [stop-onyx (start-onyx)
        results (launch)]
    (stop-onyx)
    results))

(defn -main
  []
  (clojure.pprint/pprint (run-jobs)))
