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

(defn random-header-gen
  "Baseline non-empty random string -> non-empty random string map generator
  for HTTP headers. See `http-header-gen` for how to inject specific headers."
  []
  (sgen/not-empty (sgen/map (s/gen ::non-blank-string)
                            (s/gen ::non-blank-string))))

(defn x-ratelimit-reset-header-gen
  "Generator for valid X-RateLimit-Reset HTTP headers"
  []
  (sgen/hash-map "x-ratelimit-reset"
                 (sgen/fmap str (let [current-time (quot
                                                    (System/currentTimeMillis)
                                                    1000)]
                                  (sgen/large-integer*
                                   {:min current-time
                                    :max (+ 3600 current-time)})))))

(defn http-header-gen
  "HTTP header map generator. Baseline is random non-blank string keys to
  random non-blank string values. On top of that it adds X-RateLimit-Reset
  headers w/ valid UNIX timestamp string values about half the time. If you
  want to add additional specific headers, add them to the frequency vector
  at whatever frequency is appropriate (and feel free to rebalance the others
  if it's helpful)."
  []
  (sgen/frequency [[5 (sgen/fmap
                       (fn [[m1 m2]] (merge m1 m2))
                       (sgen/tuple (random-header-gen)
                                   (x-ratelimit-reset-header-gen)))]
                   [5 (random-header-gen)]]))

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