(ns netlify-client.deploy-key
  (:require [clojure.spec.alpha :as s]
            [netlify-client.api :as api]
            [netlify-client.github :as github]
            [netlify-client.specs :as specs])
  (:refer-clojure :exclude [get]))

(def api-path "/deploy_keys")

(defn create [access-token]
  (api/request access-token :post api-path))

(s/def ::id ::specs/non-blank-string)
(s/def ::public_key ::specs/non-blank-string)
(s/def ::deploy-key (s/keys :req-un [::id ::public_key]))

(s/fdef create
  :args (s/cat :access-token ::specs/access-token)
  :ret  ::deploy-key)

(defn get [access-token id]
  (api/request access-token :get (str api-path "/" id)))

(s/fdef get
  :args (s/cat :access-token ::specs/access-token
               :id           ::specs/deploy-key-id)
  :ret  ::deploy-key)

(defn github-key
  "Returns the corresponding GitHub deploy key for the Netlify deploy key
  identified by `id`, if it exists. Returns nil if it doesn't.
  Takes a `github-params` map like `{:user \"foo\", :repo \"bar\"}`."
  [access-token id github-params]
  (let [netlify-key (get access-token id)
        public-key (:public_key netlify-key)
        github-keys (github/all-deploy-keys github-params)]
    (some #(when (= (:key %) public-key) %) github-keys)))