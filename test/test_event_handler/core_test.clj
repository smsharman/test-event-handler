(ns test-event-handler.core-test
  (:require [clojure.test :refer :all]
            [test-event-handler.core :refer :all]))

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

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
