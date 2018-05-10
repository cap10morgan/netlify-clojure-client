(ns netlify-client.site-test
  (:require [netlify-client.site :refer :all]
            [netlify-client.test-utils :refer [defspec-instrument-test]])
  (:refer-clojure :exclude [get update]))

(defspec-instrument-test test-netlify-repo-spec `netlify-repo)