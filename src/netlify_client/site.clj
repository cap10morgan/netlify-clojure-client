(ns netlify-client.site
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [netlify-client.api :as api]
            [netlify-client.deploy-key :as deploy-key]
            [netlify-client.github :as github]
            [netlify-client.specs :as specs]
            [netlify-client.util :as util])
  (:refer-clojure :exclude [get update]))

(def api-path "/sites")

(s/def ::name ::specs/non-blank-string)
(s/def ::ssl boolean?)
(s/def ::force_ssl boolean?)
(s/def ::deploy_hook ::specs/web-url)
(s/def ::prerender ::specs/non-blank-string)
(s/def ::custom_domain ::specs/non-blank-string)

(s/def ::build-params (s/keys :req [::cmd ::dir]
                              :opt [::env]))
(s/def ::deploy_key_id ::specs/non-blank-string)
(s/def ::repo_path (s/and ::specs/non-blank-string))
(s/def ::build-settings (s/and ::build-params
                               (s/keys :opt-un [::deploy_key_id ::repo_path])))

(s/def ::site (s/keys :req [::name]
                      :opt [::ssl ::force_ssl ::deploy_hook ::prerender
                            ::custom_domain]))
(s/def ::team (s/nilable string?))

(defn create
  "Creates a new Netlify site.
  - `access-token` is your auth credential and can be obtained by calling
    `netlify-client.core/access-token` with your Netlify client-id and
    client-secret.
  - `site` is a map of the new site you'd like to create in Netlify. See the
    Netlify API docs for details. Keys should be keywords.
  - `team` (optional) is the Netlify team name you'd like to create the site
    under."
  [access-token site & [team]]
  (let [path (if (str/blank? team)
               api-path
               (str "/" team api-path))]
    (api/request access-token :post path
                 (util/denamespace-top-level-keys site))))

(s/fdef create
  :args (s/cat :access-token ::specs/access-token
               :site         ::site
               :team         ::team)
  :ret  ::site
  ;; TODO: Add more keys to the fn spec
  :fn   #(= (-> % :ret :site (select-keys [:name :ssl :build_settings
                                           :custom_domain]))
            (-> % :args :site (select-keys [:name :ssl :build_settings
                                            :custom_domain]))))

(s/def ::github-params (s/keys :req [::github/user ::github/repo
                                     ::github/repo-id ::github/branch]
                               :opt [::github/private ::github/opts]))

(s/def ::cmd ::specs/non-blank-string)
(s/def ::dir ::specs/non-blank-string)
(s/def ::env (s/map-of ::specs/non-blank-string ::specs/non-blank-string))

(s/def ::api/deploy-key-id ::specs/non-blank-string)

(defn netlify-repo
  [github-params build-params deploy-key-id]
  (-> build-params
      util/denamespace-top-level-keys
      (assoc :provider      "github"
             :id            (::github/repo-id github-params)
             :repo          (str (::github/user github-params) "/"
                                 (::github/repo github-params))
             :private       (boolean (::github/private github-params))
             :branch        (::github/branch github-params)
             :deploy_key_id deploy-key-id)))

(s/def ::api/provider #{"github"})
(s/def ::api/id ::github/repo-id)

;; Ideally this would be composed of the ::github/user and ::github/repo specs,
;; but Alex Miller says spec's support for composing string specs isn't great
;; yet (as of 2018-3-1).
(s/def ::api/repo (s/and string? #(re-matches #"\A.+/.+\z" %)))

(s/def ::api/private boolean?)
(s/def ::api/branch ::specs/non-blank-string)

(s/def ::api/repo-obj (s/keys :req-un [::api/provider ::api/id ::api/repo
                                       ::api/private ::api/branch ::cmd
                                       ::dir ::api/deploy_key_id]))

(s/fdef netlify-repo
  :args (s/cat :github-params ::github-params
               :build-params  ::build-params
               :deploy-key-id ::api/deploy-key-id)
  :ret  ::api/repo-obj
  :fn   (s/and #(= (-> % :ret :id) (-> % :args :github-params ::github/repo-id))
               #(= (-> % :ret :repo)
                   (str (-> % :args :github-params ::github/user) "/"
                        (-> % :args :github-params ::github/repo)))
               #(= (-> % :ret :private)
                   (boolean (-> % :args :github-params ::github/private)))
               #(= (-> % :ret :branch) (-> % :args :github-params ::github/branch))
               #(= (-> % :ret ::build-cmd) (-> % :args :build-params ::build-cmd))
               #(= (-> % :ret ::build-dir) (-> % :args :build-params ::build-dir))
               #(= (-> % :ret :deploy_key_id) (-> % :args :deploy-key-id))))

(defn create-from-github
  "Creates a new Netlify site from a GitHub repo and sets up continuous
  deployment from it. Returns `{:error api-response-map}` if anything goes
  wrong.

  Arguments:
  - `access-token` : Netlify access-token just like other fns in this lib
  - `github-params` :
     #:netlify-client.github{:user    \"github-username\"
                             :repo    \"github-repo-name\"
                             :repo-id numerical-id-of-repo
                             :private bool
                             :branch  \"branch-to-deploy\"
                             :opts {:auth / :oauth / :client-id & :client-token}
                                    ; This map should contain the GitHub
                                    ; authentication method & creds you want to
                                    ; use. See the tentacles library docs for
                                    ; more info.

                             ; and any additional key-values you want passed to
                             ; the GitHub API call (with their namespaces
                             ; stripped off).
                            }
  - `build-params` : #:netlify-client.site{:cmd \"build command\"
                                           :dir \"relative path to publish directory\"
                                           :env {\"ENV_VAR\" \"value\"}}
  - `site`         : Same as you would pass to `netlify-client.site/create`
  - `team`         : Optional string arg if you want to create site under a
                     Netlify team"
  [access-token github-params build-params site & [team]]
  (let [deploy-key (deploy-key/create access-token)
        site-with-repo (assoc site :repo (netlify-repo github-params
                                                       build-params
                                                       (:id deploy-key)))
        github-request (-> github-params
                           util/denamespace-top-level-keys
                           (assoc :key-title (str "Netlify - " (::name site))
                                  :key (:public_key deploy-key))
                           (clojure.core/update :opts util/denamespace-top-level-keys)
                           (clojure.core/update :opts assoc
                                                :read_only true))
        github-response (github/add-deploy-key github-request)]
    (if (contains? github-response :id)
      (let [netlify-response (create access-token site-with-repo team)]
        (if-let [error (:error netlify-response)]
          {:error {::error-source      :netlify
                   ::error-description "Error creating Netlify site"
                   ::api/request   site-with-repo
                   ::api/response  error}}
          netlify-response))
      {:error {::error-source      :github
               ::error-description "Error adding deploy key to GitHub"
               ::github/request    github-request
               ::github/response   github-response}})))

(s/def ::error-source #{:github :netlify})
(s/def ::error-description ::specs/non-blank-string)
(s/def ::error-response (s/keys :req [::error-source ::error-description]))
(s/def ::github/request (s/map-of keyword? any?))
(s/def ::github/response (s/map-of keyword? any?))
(s/def ::github-error-response (s/and ::error-response
                                      (s/keys :req [::github/request
                                                    ::github/response])))
(s/def ::api/request (s/map-of keyword? any?))
(s/def ::netlify-error-response (s/and ::error-response
                                       (s/keys :req [::api/request
                                                     ::api/response])))
(s/def ::error (s/or :github ::github-error-response
                     :netlify ::netlify-error-response))

;; TODO: Move business logic into pure fns, spec those, gen-test them, and
;;       isolate API side effects into `create-from-github`.
(s/fdef create-from-github
  :args (s/cat :access-token ::specs/access-token
               :github-params ::github-params
               :build-params ::build-params
               :site ::site
               :team ::team)
  :ret  (s/or :success ::site
              :error (s/keys :req-un [::error])))

(s/def ::site-id string?)

(defn get [access-token site-id]
  (when (nil? site-id)
    ;; Throw an exception in this case because otherwise it becomes a
    ;; "list all" request and we're all confused.
    (throw (IllegalArgumentException. "Site ID cannot be nil")))
  (api/request access-token :get (str api-path "/" site-id)))

(s/fdef get
  :args (s/cat :access-token ::specs/access-token
               :site-id ::site-id)
  :ret  ::site
  :fn   #(= (-> % :ret :site_id) (-> % :args :site-id)))

(defn all [access-token]
  (api/request access-token :get api-path))

(s/fdef all
  :args (s/cat :access-token ::specs/access-token)
  :ret (s/coll-of ::site))

(defn update [access-token site-id site]
  (api/request access-token :put (str api-path "/" site-id) site))

(s/fdef update
  :args (s/cat :access-token ::specs/access-token
               :site-id ::site-id
               :site ::site)
  :ret  ::site
  :fn   #(= (:ret %) (-> % :args :site)))

(defn delete-github-deploy-key-for-site
  [access-token {build-settings :build_settings} & [github-opts]]
  (when-let [netlify-deploy-key-id (:deploy_key_id build-settings)]
    (let [[user github-repo] (str/split (:repo_path build-settings) #"/")
          github-deploy-key (deploy-key/github-key access-token
                                                   netlify-deploy-key-id
                                                   {:user user
                                                    :repo github-repo
                                                    :opts github-opts})
          github-deploy-key-id (:id github-deploy-key)]
      (github/delete-deploy-key {:user user
                                 :repo github-repo
                                 :id   github-deploy-key-id
                                 :opts github-opts}))))

(s/def ::build_settings ::build-settings)

(s/fdef delete-github-deploy-key-for-site
  :args (s/cat :access-token ::specs/access-token
               :site (s/keys :req-un [::build_settings])
               :github-opts (s/* ::github/opts))
  :ret  ::github/response)

(defn delete [access-token site-id & [github-opts]]
  (let [site (get access-token site-id)]
    (if site
      (do
        (delete-github-deploy-key-for-site access-token site github-opts)
        (api/request access-token :delete (str api-path "/" site-id)))
      {:error {::error-source :netlify
               ::error-description (str "Netlify site " (pr-str site-id)
                                        " not found")
               ::api/request {:site-id site-id}
               ::api/response (:error site)}})))

(s/fdef delete
  :args (s/cat :access-token ::specs/access-token
               :site-id ::site-id
               :github-opts ::github/opts)
  :ret  (s/or :success true?
              :error (s/keys :req-un [::error])))