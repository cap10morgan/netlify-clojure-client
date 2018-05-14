# netlify-client

Build status: [![CircleCI](https://circleci.com/gh/cap10morgan/netlify-clojure-client.svg?style=svg)](https://circleci.com/gh/cap10morgan/netlify-clojure-client)

Latest release: [![Clojars Project](https://img.shields.io/clojars/v/cap10morgan/netlify-client.svg)](https://clojars.org/cap10morgan/netlify-client)

A Clojure library for using the [Netlify API](https://www.netlify.com/docs/api/).

Currently only implements site create, get, update, and delete functions.
Can optionally link sites to a GitHub repo for continuous deployment using the
`netlify-client.site/create-from-github` function. 

## Usage

```clojure
(ns my-code
  (:require [netlify-client.github :as github]
            [netlify-client.core :as netlify]
            [netlify-client.site :as site]))
            
(defn create-my-site []
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
    (site/create access-token
                 #::site{:name "My Cool Weblog"
                         :force_ssl true
                         :custom_domain "coolweblog.example.com"})))
                  
(defn create-my-site-from-a-github-repo []
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
    (site/create-from-github access-token
                             #::github{:user "your-github-user-or-org"
                                       :repo "name-of-your-github-repo"
                                       :repo-id "id-number-of-your-repo" ; for now you have to pull this out of the HTML source on GitHub; search for "repository_id"
                                       :private true
                                       :branch "master"
                                       :opts {:auth "github-auth-token"}}
                             #::site{:env {"ENV_VAR" "value"}
                                     :cmd "build/command"
                                     :dir "dir/to/publish"}
                             #::site{:name "My Cool Weblog"
                                     :force_ssl true
                                     :custom_domain "coolweblog.example.com"})))
            
(defn get-my-site [site-id]
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
    (site/get access-token site-id)))
    
(defn update-my-site [site-id new-site-params]
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
    (site/update access-token site-id new-site-params)))
    
(defn delete-my-site [site-id]
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
    (site/delete access-token site-id)))
```

## License

Copyright © 2018 Wes Morgan

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
