(ns netlify-client.authentication-test
  (:require [netlify-client.authentication :refer :all]
            [netlify-client.test-utils :refer [defspec-instrument-test]]))

(defspec-instrument-test test-build-access-token-request-spec `build-access-token-request)