(ns netlify-client.authentication
  (:require [clj-http.client :as http]
            [clojure.spec.alpha :as s]
            [netlify-client.specs :as specs]))

(def oauth-token-path
  "/oauth/token")

(s/def ::api-url ::specs/web-url)
(s/def ::client-id ::specs/non-blank-string)
(s/def ::client-secret ::specs/non-blank-string)

(defn build-access-token-request [api-url {:keys [client-id client-secret]}]
  {:method      :post
   :url         api-url
   :basic-auth  [client-id client-secret]
   :form-params {:grant_type "client_credentials"
                 :client_id client-id}
   :as          :json})

(s/def ::basic-auth (s/cat :client-id ::client-id
                           :client-secret ::client-secret))

(s/def ::grant_type #{"client_credentials"})
(s/def ::client_id ::client-id)
(s/def ::form-params (s/keys :req-un [::grant_type ::client_id]))

(s/fdef build-access-token-request
  :args (s/cat :api-url ::api-url
               :creds   (s/keys :req-un [::client-id ::client-secret]))
  :ret  (s/keys :req-un [::specs/method ::specs/url ::basic-auth ::form-params
                         ::as])
  :fn   (s/and #(= (-> % :args :api-url) (-> % :ret :url))
               #(= (-> % :args :creds :client-id)
                   (-> % :ret :form-params :client_id))
               #(= (-> % :args :creds :client-id)
                   (-> % :ret :basic-auth :client-id))
               #(= (-> % :args :creds :client-secret)
                   (-> % :ret :basic-auth :client-secret))))

(defn access-token [api-url creds]
  (let [request (build-access-token-request api-url creds)
        {:keys [body]} (http/request request)]
    (:access_token body)))

(s/fdef access-token
  :args (s/cat :api-url ::api-url
               :creds   :netlify-client.core/creds)
  :ret  ::specs/access-token)