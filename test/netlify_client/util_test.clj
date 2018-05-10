(ns netlify-client.util-test
  (:require [netlify-client.util :refer :all]
            [netlify-client.test-utils :refer [defspec-instrument-test]]))

;; Spec-gen'd tests are commented out in here until the following issue is
;; addressed: https://dev.clojure.org/jira/browse/CLJ-2054
;; Until then, they fail randomly when the `double` generator spits out a NaN
;; at an arbitrary level of nesting in a data structure. NaNs are never equal
;; to anything else, including other NaNs, so that throws off the fn specs.

#_(defspec-instrument-test test-strip-namespace-from-keyword-spec
    `strip-namespace-from-keyword)

#_(defspec-instrument-test test-denamespace-top-level-keys-spec
    `denamespace-top-level-keys {:opts {:num-tests 100}}) ; 1000 tests takes ~20 minutes to run