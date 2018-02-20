(ns netlify-client.test-utils
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.test :refer [deftest]]
            [plumula.mimolette.alpha :as mimolette]
            [plumula.mimolette.impl :as mimolette.impl]))

(defmacro defspec-instrument-test
  ([name sym-or-syms]
   `(defspec-instrument-test ~name ~sym-or-syms nil))
  ([name sym-or-syms opts]
   `(deftest ~name
      (stest/instrument ~sym-or-syms)
      (mimolette.impl/spec-test mimolette/check mimolette/spec-shim
                                ~sym-or-syms ~opts)
      (stest/unstrument ~sym-or-syms))))

