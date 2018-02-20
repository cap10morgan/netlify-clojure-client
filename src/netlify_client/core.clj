(ns netlify-client.core
  (:require [clojure.spec.alpha :as s]
            [netlify-client.api :as api]
            [netlify-client.authentication :as auth]))

(s/def ::client-id string?)
(s/def ::client-secret string?)
(s/def ::creds (s/keys :req-un [::client-id ::client-secret]))

(defn access-token [creds]
  (auth/access-token api/url creds))

(s/fdef access-token
  :args (s/cat :creds ::creds)
  :ret  ::api/access-token)