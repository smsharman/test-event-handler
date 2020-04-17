(ns test-event-handler.core
(:require [uswitch.lambada.core :refer [deflambdafn]]
  [clojure.data.json :as json]
  [clojure.java.io :as io]
  [cognitect.aws.client.api :as aws]
  [synergy-specs.events :as synspec]
  [clojure.spec.alpha :as s]
  [taoensso.timbre :as timbre
   :refer [log trace debug info warn error fatal report
           logf tracef debugf infof warnf errorf fatalf reportf
           spy get-env]])
(:gen-class))

;; Declare clients for AWS services required

(def sns (aws/client {:api :sns}))

(def ssm (aws/client {:api :ssm}))

(def routeTableParameters {
                           :arn-prefix "synergyDispatchTopicArnRoot"
                           :event-store-topic "synergyEventStoreTopic"
                           })

;; Set this on a per-event-handler basis - this is eventAction that this handler handles
(def myEventAction "event1")
(def myEventVersion 1)

(def snsArnPrefix (atom ""))

(def eventStoreTopic (atom ""))

(defn getRouteTableParametersFromSSM []
  "Look up values in the SSM parameter store to be later used by the routing table"
  (let [snsPrefix (get-in (aws/invoke ssm {:op :GetParameter
                                           :request {:Name (get routeTableParameters :arn-prefix)}})
                          [:Parameter :Value])
        evStoreTopic (get-in (aws/invoke ssm {:op :GetParameter
                                              :request {:Name (get routeTableParameters :event-store-topic)}})
                             [:Parameter :Value])
        ]
    ;; //TODO: add error handling so if for any reason we can't get the values, this is noted
    {:snsPrefix snsPrefix :eventStoreTopic evStoreTopic}))

(defn setEventStoreTopic [parameter-map]
  "Set the eventStoreTopic atom with the required value"
  (swap! eventStoreTopic str (get parameter-map :eventStoreTopic)))

(defn setArnPrefix [parameter-map]
  "Set the snsArnPrefix atom with the required value"
  (swap! snsArnPrefix str (get parameter-map :snsPrefix)))

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

(defn gen-status-map
  "Generate a status map from the values provided"
  [status-code status-message return-value]
  (let [return-status-map {:status status-code :description status-message :return-value return-value}]
    return-status-map))

(defn generate-lambda-return [statuscode message]
  "Generate a simple Lambda status return"
  {:status statuscode :message message})

(defn validate-message [inbound-message]
  (if (s/valid? ::synspec/synergyEvent inbound-message)
    (gen-status-map true "valid-inbound-message" {})
    (gen-status-map false "invalid-inbound-message" (s/explain-data ::synspec/synergyEvent inbound-message))))

(defn set-up-route-table []
  (reset! snsArnPrefix "")
  (reset! eventStoreTopic "")
  (info "Routing table not found - setting up (probably first run for this Lambda instance")
  (let [route-paraneters (getRouteTableParametersFromSSM)]
    (setArnPrefix route-paraneters)
    (setEventStoreTopic route-paraneters)))

(defn send-to-topic
  ([thisTopic thisEvent]
   (send-to-topic thisTopic thisEvent ""))
  ([topic event note]
   (let [thisEventId (get event ::synspec/eventId)
         jsonEvent (json/write-str event)
         eventSNS (str @snsArnPrefix topic)
         snsSendResult (aws/invoke sns {:op :Publish :request {:TopicArn eventSNS
                                                               :Message  jsonEvent}})]
     (if (nil? (get snsSendResult :MessageId))
       (do
         (info "Error dispatching event to topic : " topic " (" note ") : " event)
         (gen-status-map false "error-dispatching-to-topic" {:eventId thisEventId
                                                             :error snsSendResult}))
       (do
         (info "Dispatching event to topic : " eventSNS " (" note ") : " event)
         (gen-status-map true "dispatched-to-topic" {:eventId   thisEventId
                                                     :messageId (get snsSendResult :MessageId)}))))))

;; Specific handler logic here


(defn process-event [event]
  "Route an inbound event to a given set of destination topics"
  (if (empty? @snsArnPrefix)
    (set-up-route-table))
  (let [validateEvent (validate-message event)]
    (if (true? (get validateEvent :status))
        (if (and (= (get event ::synspec/eventAction) myEventAction)
                 (= (get event ::synspec/eventVersion) myEventVersion))
          (send-to-topic successQueue event "I route this!")
          (send-to-topic failQueue event "I don't route this!"))
      (gen-status-map false "invalid-message-format" (get validateEvent :return-value)))))

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
