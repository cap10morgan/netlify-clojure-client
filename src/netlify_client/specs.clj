(ns netlify-client.specs
  (:require [cemerick.url :as url]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]))

(s/def ::non-blank-string (s/and string? #(not= % "")))

(s/def ::access-token ::non-blank-string)

(s/def ::http-verb #{:get :put :post :delete :patch})
(s/def ::method ::http-verb)

(s/def ::as #{:json}) ; for the clj-http request map key

;; Stolen from https://gist.github.com/conan/8f0c879c47d14d5713f7a0986f81285d

(defn non-empty-string-alphanumeric
  []
  (sgen/such-that #(not= "" %)
                  (sgen/string-alphanumeric)))

(defn url-gen
  "Generator for generating URLs; note that it may generate
  http URLs on port 443 and https URLs on port 80, and only
  uses alphanumerics"
  []
  (sgen/fmap
   (partial apply (comp str url/->URL))
   (sgen/tuple
    ;; protocol
    (sgen/elements #{"http" "https"})
    ;; username
    (sgen/string-alphanumeric)
    ;; password
    (sgen/string-alphanumeric)
    ;; host
    (non-empty-string-alphanumeric)
    ;; port
    (sgen/choose 1 65535)
    ;; path
    (sgen/fmap #(->> %
                     (interleave (repeat "/"))
                     (apply str))
               (sgen/not-empty
                (sgen/vector
                 (non-empty-string-alphanumeric))))
    ;; query
    (sgen/map
     (non-empty-string-alphanumeric)
     (non-empty-string-alphanumeric)
     {:max-elements 2})
    ;; anchor
    (sgen/string-alphanumeric))))

(s/def ::web-url (s/with-gen
                  (s/and string?
                         #(try
                            (url/url %)
                            (catch Throwable _ false)))
                  url-gen))

(s/def ::url ::web-url)