(defproject cap10morgan/netlify-client "0.1.2-SNAPSHOT"
  :description "Netlify API Client"
  :url "https://github.com/cap10morgan/netlify-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [clj-http "3.8.0"]
                 [cheshire "5.8.0"]
                 [plumula/mimolette "0.2.1"]
                 [com.cemerick/url "0.1.1"]
                 [tentacles "0.5.1"]]

  :monkeypatch-clojure-test false

  :deploy-repositories {"releases" :clojars
                        "snapshots" :clojars})