(ns netlify-client.api
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [netlify-client.specs :as specs]
            [clojure.spec.gen.alpha :as sgen]))

(def url "https://api.netlify.com")
(def version "v1")

(s/def ::http-status (s/with-gen
                      (s/int-in 100 599)
                      #(sgen/frequency [[1 (sgen/return 429)]
                                        [9 (s/gen (s/int-in 100 599))]])))

(defn success? [status]
  (<= 200 status 299))

(s/fdef success?
  :args (s/cat :status ::http-status)
  :ret  boolean?
  :fn   #(= (:ret %) (<= 200 (-> % :args :status) 299)))

(defn rate-limited? [status]
  (= 429 status))

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

(s/def ::params (s/nilable (s/map-of keyword? string? :gen-max 3)))
(s/def ::status ::http-status)
(s/def ::headers (s/with-gen
                  (s/map-of ::specs/non-blank-string ::specs/non-blank-string)
                  specs/http-header-gen))
(s/def ::body (s/map-of keyword? any? :gen-max 1))
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
        req-params {params-key params}]
    (merge req-params
           {:url              (endpoint resource)
            :method           verb
            :throw-exceptions false
            :oauth-token      access-token
            :as               :json}
           (when (#{:post :put :patch} verb)
             {:content-type :json}))))

(s/def ::oauth-token ::specs/access-token)
(s/def ::throw-exceptions (s/and boolean? false?))
(s/def ::request-map (s/keys :req-un [::specs/method ::specs/url
                                      ::throw-exceptions ::oauth-token
                                      ::specs/as]
                             :opt-un [::query-params ::form-params]))

(s/fdef build-request
  :args ::request-args
  :ret  ::request-map
  :fn   (s/and #(= (-> % :args :access-token)
                   (-> % :ret :oauth-token))
               #(= (-> % :args :verb) (-> % :ret :method))
               #(str/includes? (-> % :ret :url) (-> % :args :resource))
               #(let [params-key (http-verb->params-key (-> % :ret :method))]
                  (every? (fn [[k v]]
                            (= v (-> % :ret params-key k)))
                          (-> % :args :params)))))

(s/def ::http-response (s/keys :req-un [::body ::status ::headers]))

(defn ensure-non-negative
  "Raises negative `num` args to 0. Returns all other numerical values intact."
  [num]
  (cond
    (neg? num) 0
    (Double/isNaN num) 0 ; 😒
    :else num))

(s/fdef ensure-non-negative
  :args (s/cat :num number?)
  :ret  (s/or :pos pos? :zero zero?)
  :fn   #(cond
           (or (>= 0 (-> % :args :num)) (Double/isNaN (-> % :args :num)))
           (and (= :zero (first (:ret %)))
                (zero? (second (:ret %)))) ; zero? helps catch oddities like -0.0

           :else (= [:pos (-> % :args :num)] (:ret %))))

(defn rate-limit-wait-time
  "Returns the amount of time (in secs) we should wait after a rate-limited
  `response` before retrying the original request. If the `response` was not
  rate limited it returns a wait time of 0. If the response was rate limited
  (i.e. 429 status) but is missing the X-RateLimit-Reset header, it returns a
  wild guess of 5 just to ensure we still slow our roll a little."
  [response]
  (if (-> response :status rate-limited?)
    (if-let [reset-time (some-> response
                                :headers
                                (get "x-ratelimit-reset")
                                Integer/parseInt)]
      (let [current-time (quot (System/currentTimeMillis) 1000)]
        (-> reset-time
            (- current-time)
            (+ (rand-int 5))
            inc
            ensure-non-negative))
      5)
    0))

(s/fdef rate-limit-wait-time
  :args (s/cat :response ::http-response)
  :ret  nat-int?
  :fn   #(let [header (-> % :args :response :headers
                          (get "x-ratelimit-reset"))]
           (if (rate-limited? (-> % :args :response :status))
             (if header
               (let [header-delta (- (Integer/parseInt header)
                                     (quot (System/currentTimeMillis) 1000))]
                 (< header-delta (:ret %)))
               (= 5 (:ret %)))
             (zero? (:ret %)))))

(defn retry-after-rate-limit-reset
  "Waits the amount of time specified by the `X-RateLimit-Reset` header value
  (+ a little more) in the `request` and then tries it again (once). Intended
  to be used after being rate limited (i.e. getting a 429 error response)."
  [request response]
  (let [reset-time (-> response
                       :headers
                       (get "x-ratelimit-reset")
                       Integer/parseInt)
        current-time (quot (System/currentTimeMillis) 1000)
        wait-time (-> reset-time
                      (- current-time)
                      (+ (rand-int 5))
                      inc)]
    (println "Waiting" wait-time "secs for rate limit reset")
    (Thread/sleep (* 1000 wait-time))
    (println "Retrying request")
    (http/request request)))

(s/fdef retry-after-rate-limit-reset
  :args (s/cat :request  ::request-map
               :response ::http-response)
  :ret  ::http-response)

(defn request [access-token verb resource & [params]]
  (let [request (build-request access-token verb resource params)
        {:keys [status body] :as response} (http/request request)]
    (cond
      (success? status) (or body true)
      (rate-limited? status) (retry-after-rate-limit-reset request response)
      :else {:error response})))

(s/def ::response (s/or :success ::body
                        :error   ::error-response))

(s/fdef request
  :args ::request-args
  :ret  ::response)