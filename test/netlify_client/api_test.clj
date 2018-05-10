(ns netlify-client.api-test
  (:require [netlify-client.api :refer :all]
            [netlify-client.test-utils :refer [defspec-instrument-test]]))

(defspec-instrument-test test-success?-spec `success?)

(defspec-instrument-test test-endpoint-spec `endpoint)

(defspec-instrument-test test-http-verb->params-key-spec `http-verb->params-key)

(defspec-instrument-test test-build-request-spec `build-request)

(defspec-instrument-test test-ensure-non-negative-spec `ensure-non-negative)

(defspec-instrument-test test-rate-limit-wait-time-spec `rate-limit-wait-time)