(ns repl
  (:use clojure.repl)
  (:require
    [com.mitranim.forge :as forge]
    [app.core :as core]))

(set! *warn-on-reflection* true)
