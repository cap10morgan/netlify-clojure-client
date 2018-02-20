(ns netlify-client.api
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [netlify-client.specs :as specs]))

(def url "https://api.netlify.com")
(def version "v1")

(s/def ::http-status (s/int-in 100 599))

(defn success? [status]
  (<= 200 status 299))

(s/fdef success?
  :args (s/cat :status ::http-status)
  :ret  boolean?
  :fn   #(= (:ret %) (<= 200 (-> % :args :status) 299)))

(s/def ::resource
  (s/with-gen
   (s/and string?
          #(re-matches #"^/\w+[\w/]*$" %))
   #(gen/fmap (fn [s] (str "/" s))
              (gen/such-that (fn [s] (not= s ""))
                             (gen/string-alphanumeric)))))

(defn endpoint [resource]
  (str url "/api/" version resource))

(s/fdef endpoint
  :args (s/cat :resource ::resource)
  :ret  (s/and string?
               #(str/includes? % url)
               #(str/includes? % version))
  :fn #(str/includes? (:ret %) (-> % :args :resource)))

(s/def ::params (s/nilable (s/map-of keyword? string?)))
(s/def ::status ::http-status)
(s/def ::headers (s/map-of ::specs/non-blank-string ::specs/non-blank-string))
(s/def ::body (s/map-of keyword? any?))
(s/def ::error (s/keys :req-un [::status ::headers ::body]))
(s/def ::error-response (s/keys :req-un [::error]))
(s/def ::request-args (s/cat :access-token ::specs/access-token
                             :verb         ::specs/http-verb
                             :resource     ::resource
                             :params       ::params))

(defn http-verb->params-key [verb]
  (case verb
    (:get :delete)      :query-params
    (:post :put :patch) :form-params))

(s/fdef http-verb->params-key
  :args (s/cat :verb ::specs/http-verb)
  :ret  #{:query-params :form-params})

(defn build-request [access-token verb resource params]
  (let [params-key (http-verb->params-key verb)
        auth-params {:access_token access-token}
        req-params {params-key (merge auth-params params)}]
    (merge req-params {:url              (endpoint resource)
                       :method           verb
                       :throw-exceptions false
                       :as               :json})))

(s/def ::api-params (s/keys :req-un [::access_token]))
(s/def ::api-query-params ::api-params)
(s/def ::api-form-params ::api-params)
(s/def ::query-params ::api-query-params)
(s/def ::form-params ::api-form-params)
(s/def ::throw-exceptions (s/and boolean? false?))
(s/def ::access_token ::specs/access-token)
(s/def ::request-map (s/keys :req-un [::specs/method ::specs/url
                                      ::throw-exceptions ::specs/as]
                             :opt-un [::query-params ::form-params]))

(s/fdef build-request
  :args ::request-args
  :ret  ::request-map
  :fn   (s/and #(let [params-key (http-verb->params-key (-> % :ret :method))]
                  (= (-> % :args :access-token)
                     (-> % :ret params-key :access_token)))
               #(= (-> % :args :verb) (-> % :ret :method))
               #(str/includes? (-> % :ret :url) (-> % :args :resource))
               #(let [params-key (http-verb->params-key (-> % :ret :method))]
                  (every? (fn [[k v]]
                            (= v (-> % :ret params-key k)))
                          (-> % :args :params)))))

(defn request [access-token verb resource & [params]]
  (let [request (build-request access-token verb resource params)
        {:keys [status body] :as response} (http/request request)]
    (if (success? status)
      body
      {:error response})))

(s/def ::response (s/or :success ::body
                        :error   ::error-response))

(s/fdef request
  :args ::request-args
  :ret  ::response)