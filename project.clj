(defproject app "0.0.0"
  :dependencies
  [
   [org.clojure/clojure "1.10.1"]
   [com.stuartsierra/component "1.0.0"]
   [com.mitranim/forge "0.1.4"]
   [http-kit
    ;; "2.3.0" produces
    ;; IllegalStateException: Client/Server mode has not yet been set.
    "2.4.0-alpha6"]
   [cheshire "5.10.0"]
   [hiccup "1.0.5"]
   [compojure "1.6.1"]
   [ring/ring-defaults "0.3.2"]
   ]

  :main app.core

  :repl-options {:skip-default-init true
                 :init-ns repl
                 :init (app.core/-main-dev)}

  :profiles {:uberjar {:aot :all :omit-source true}}
)
