(ns netlify-client.github
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [netlify-client.specs :as specs]
            [tentacles.repos :as repos]))

(s/def ::user ::specs/non-blank-string)
(s/def ::repo ::specs/non-blank-string)
(s/def ::repo-id pos-int?)
(s/def ::branch ::specs/non-blank-string)
(s/def ::private (s/nilable boolean?))
(s/def ::key-title ::specs/non-blank-string)
(s/def ::key ::specs/non-blank-string)
(s/def ::auth (s/with-gen
                (s/and string? #(re-matches #"\A.+:.+\z" %))
                #(gen/fmap (fn [[username password]]
                             (str username ":" password))
                           (gen/tuple (gen/string-alphanumeric)
                                      (gen/string-alphanumeric)))))
(s/def ::opts (s/keys :opt-un [::auth]))

(defn add-deploy-key [{:keys [user repo key-title key opts]}]
  (repos/create-key user repo key-title key opts))

(s/fdef add-deploy-key
  :args (s/cat :arg (s/keys :req-un [::user ::repo ::key-title ::key]
                            :opt-un [::opts]))
  :ret  map?)

(s/def ::id pos-int?)

(defn delete-deploy-key [{:keys [user repo id opts]}]
  (repos/delete-key user repo id opts))

(s/fdef delete-deploy-key
  :args (s/cat :arg (s/keys :req-un [::user ::repo ::id]
                            :opt-un [::opts]))
  :ret  map?)

(defn all-deploy-keys
  "Note: This will only return the first 100 keys if you have more than that.
  TODO: Figure out how to get additional pages beyond the first 100."
  [{:keys [user repo opts]}]
  (repos/keys user repo (merge {:per-page 100} opts)))

(s/fdef all-deploy-keys
  :args (s/cat :arg (s/keys :req-un [::user ::repo]
                            :opt-un [::opts]))
  :ret  map?)