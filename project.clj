(defproject app "0.0.0"
  :dependencies [
    [org.clojure/clojure "1.8.0"]
    [com.stuartsierra/component "0.3.2"]
    [com.mitranim/forge "0.1.0-SNAPSHOT"]
    [buddy/buddy-sign "2.2.0"]
    [http-kit "2.2.0"]
    [cheshire "5.8.0"]
    [hiccup "1.0.5"]
    [compojure "1.6.0"]
    [ring/ring-defaults "0.3.1"]
  ]

  :main app.core/main

  :repl-options {:skip-default-init true
                 :init-ns user
                 :init (app.core/main-dev)}
)
