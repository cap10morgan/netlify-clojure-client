(ns netlify-client.site
  (:require [netlify-client.api :as api]
            [clojure.spec.alpha :as s])
  (:refer-clojure :exclude [get update]))

(def api-path "/sites")

(s/def ::site-id string?)
(s/def ::site map?)

(defn get [access-token site-id]
  (api/request access-token :get (str api-path "/" site-id)))

(s/fdef get
  :args (s/cat :access-token :netlify-client.core/access-token
               :site-id ::site-id)
  :ret  ::site
  :fn   #(= (-> % :ret :site_id) (-> % :args :site-id)))

(defn update [access-token site-id site]
  (api/request access-token :put (str api-path "/" site-id) site))

(s/fdef update
  :args (s/cat :access-token :netlify-client.core/access-token
               :site-id ::site-id
               :site ::site)
  :ret  ::site
  :fn   #(= (:ret %) (-> % :args :site)))
