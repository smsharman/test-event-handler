(ns test-event-handler.core
(:require [uswitch.lambada.core :refer [deflambdafn]]
  [clojure.data.json :as json]
  [clojure.java.io :as io]
  [cognitect.aws.client.api :as aws]
  [synergy-specs.events :as synspec]
  [synergy-events-stdlib.core :as stdlib]
  [clojure.spec.alpha :as s]
  [taoensso.timbre :as timbre
   :refer [log trace debug info warn error fatal report
           logf tracef debugf infof warnf errorf fatalf reportf
           spy get-env]])
(:gen-class))

;; Declare clients for AWS services required

(def sns (aws/client {:api :sns}))

(def ssm (aws/client {:api :ssm}))

;; Set this on a per-event-handler basis - this is eventAction that this handler handles
(def myEventAction "event1")
(def myEventVersion 1)

(def snsArnPrefix (atom ""))

(def eventStoreTopic (atom ""))

;; Now define specific SNS topics to be used for this handler

(def successQueue "syntest1")
(def failQueue "syntest-sayhello")

;; Test messages to be used during development
;; Valid message
(def testMessage1 {
                   :eventId        "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :parentId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :originId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :userId         "1"
                   :orgId          "1"
                   :eventVersion   1
                   :eventAction    "event1"
                   :eventData      {
                                    :key1 "value1"
                                    :key2 "value2"
                                    }
                   :eventTimestamp "2020-04-17T11:23:10.904Z"
                   })

;; Invalid message - incorrect eventAction
(def testMessage2 {
                   :eventId        "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :parentId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :originId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :userId         "1"
                   :orgId          "1"
                   :eventVersion   1
                   :eventAction    "event2"
                   :eventData      {
                                    :key1 "value1"
                                    :key2 "value2"
                                    }
                   :eventTimestamp "2018-10-09T12:24:03.390+0000"
                   })

;; Invalid message - incorrect eventVersion
(def testMessage3 {
                   :eventId        "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :parentId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :originId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :userId         "1"
                   :orgId          "1"
                   :eventVersion   2
                   :eventAction    "event1"
                   :eventData      {
                                    :key1 "value1"
                                    :key2 "value2"
                                    }
                   :eventTimestamp "2020-04-17T11:23:10.904Z"
                   })

;; Invalid message - missing eventVersion
(def testMessage4 {
                   :eventId        "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :parentId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :originId       "7a5a815b-2e52-4d40-bec8-c3fc06edeb36"
                   :userId         "1"
                   :orgId          "1"
                   :eventAction    "event1"
                   :eventData      {
                                    :key1 "value1"
                                    :key2 "value2"
                                    }
                   :eventTimestamp "2020-04-17T11:23:10.904Z"
                   })

;; Specific handler logic here


(defn process-event [event]
  "Process an inbound event - usually emit a success/failure message at the end"
  (if (empty? @snsArnPrefix)
    (stdlib/set-up-topic-table snsArnPrefix eventStoreTopic ssm))
  (let [validateEvent (stdlib/validate-message event)]
    (if (true? (get validateEvent :status))
        (if (and (= (get event ::synspec/eventAction) myEventAction)
                 (= (get event ::synspec/eventVersion) myEventVersion))
          (stdlib/send-to-topic successQueue event @snsArnPrefix sns "I route this!")
          (stdlib/send-to-topic failQueue event @snsArnPrefix sns "I don't route this!"))
      (stdlib/gen-status-map false "invalid-message-format" (get validateEvent :return-value)))))

;; Note, we have the handler and then the processor in order to allow for testing. Processor takes
;; namespaced event
(defn handle-event
  [event]
  (let [cevent (json/read-str (get (get (first (get event :Records)) :Sns) :Message) :key-fn keyword)
        nsevent (synergy-specs.events/wrap-std-event cevent)]
    (info "Received the raw event : " (print-str event))
    (info "converted event " (print-str cevent))
    (info "Received the following event : " (print-str nsevent))
    (process-event nsevent)))


(deflambdafn test-event-handler.core.Route
             [in out ctx]
             "Takes a JSON event in standard Synergy Event form from the Message field, convert to map and send to routing function"
             (let [event (json/read (io/reader in) :key-fn keyword)
                   res (handle-event event)]
               (with-open [w (io/writer out)]
                 (json/write res w))))
