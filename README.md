# netlify-client

A Clojure library for using the [Netlify API](https://www.netlify.com/docs/api/).

Currently only implements site get and update functions.

## Usage

Dependency: `[cap10morgan/netlify-client "0.1.0"]`

```clojure
(ns my-code
  (:require [netlify-client.core :as netlify]
            [netlify-client.site :as site]))
            
(defn get-my-site [site-id]
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
    (site/get access-token site-id)))
    
(defn update-my-site [site-id new-site-params]
  (let [access-token (netlify/access-token {:client-id "your-client-id"
                                            :client-secret "your-client-secret"})]
      (site/update access-token site-id new-site-params)))
```

## License

Copyright © 2018 Wes Morgan

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
