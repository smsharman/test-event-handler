(ns test-event-handler.core
(:require [uswitch.lambada.core :refer [deflambdafn]]
          [cheshire.core :as json]
          [clojure.java.io :as io]
          [cognitect.aws.client.api :as aws]
          [synergy-specs.events :as synspec]
          [synergy-events-stdlib.core :as stdlib]
          [taoensso.timbre
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

(defn process-event
  "Process an inbound event - usually emit a success/failure message at the end"
  [event]
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
  (let [deduced-type (stdlib/check-event-type event)
        event-content (stdlib/get-event-data event deduced-type) ;; If this is always going to be SNS message then could use :sns
        cevent (json/parse-string event-content true)
        nsevent (synergy-specs.events/wrap-std-event cevent)]
    (info "Received the raw event : " (print-str event))
    (info "Converted event " (print-str cevent))
    (info "Received the following event : " (print-str nsevent))
    (process-event nsevent)))


(deflambdafn test-event-handler.core.Route
             [in out ctx]
             "Takes a JSON event in standard Synergy Event form from the Message field, convert to map and send to routing function"
             (let [event (json/parse-stream (io/reader in) true)
                   res (handle-event event)]
               (with-open [w (io/writer out)]
                 (json/generate-stream res w {:pretty true}))))
