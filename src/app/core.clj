(ns app.core
  (:gen-class)
  (:require
    [com.mitranim.forge :as forge]
    [app.util :as util :refer [getenv]]
    [app.srv]))

(set! *warn-on-reflection* true)

(defn create-system [_]
  (new app.srv.Srv nil))

(defn -main []
  (println "Starting system on thread" (str (Thread/currentThread)) "...")
  (forge/reset-system! create-system))

(defn -main-dev []
  (forge/start-development! {:system-symbol `create-system})
  (forge/reset-system! create-system)
  (println "Started server on" (str "http://localhost:" (getenv "LOCAL_PORT"))))
