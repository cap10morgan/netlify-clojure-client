(ns netlify-client.util
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]))

(defn strip-namespace-from-keyword [v]
  (if (keyword? v)
    (-> v name keyword)
    v))

(s/fdef strip-namespace-from-keyword
  :args (s/cat :v any?)
  :ret  any?
  :fn   #(let [v (-> % :args :v)]
           (if (keyword? v)
             (= (:ret %) (-> v name keyword))
             (= (:ret %) v))))

(defn denamespace-top-level-keys [v]
  "Returns a new map with the same keys and values as `v` but whose top-level
  keyword keys have their namespaces stripped off. Throws an exception if this
  would cause a key collision (e.g. {:foo/bar 1, :baz/bar 2})."
  (set/rename-keys
   v
   (reduce-kv (fn [m k _]
                (let [new-key (strip-namespace-from-keyword k)]
                  (if (contains? (-> m vals set) new-key)
                    (throw (ex-info
                            (str "Key collision: " (pr-str new-key)
                                 " is already present in rename-keys map")
                            m))
                    (assoc m k new-key))))
              {} v)))

(s/def ::map-without-keyword-key-collisions
  #(= (->> %
           (map (fn [[k]]
                  (strip-namespace-from-keyword k)))
           set
           count)
      (-> % keys set count)))

(s/fdef denamespace-top-level-keys
  :args (s/cat :v (s/and
                   (s/map-of any? any?)
                   ::map-without-keyword-key-collisions))
  :ret  map?
  :fn   #(every? (fn [[k v]]
                   (let [new-k (strip-namespace-from-keyword k)]
                     (= v (-> % :ret (get new-k)))))
                 (-> % :args :v)))