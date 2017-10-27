(ns app.util
  (:require
    [com.mitranim.forge :as forge]
    [org.httpkit.client :as http]
    [cheshire.core :as cheshire]
    [clojure.edn]
    ))

(set! *warn-on-reflection* true)


(when-not (.exists (clojure.java.io/file ".env.properties"))
  (println "Env file not found."
           "Don't forget to copy/rename .env.properties.example to .env.properties"
           "and fill out your Auth0 keys."))



(def env (merge {} (System/getenv) (forge/read-props ".env.properties")))

(defmacro getenv [key] (forge/get-strict env key))



(defn trim-leading-slashes [path]
  (if (clojure.string/starts-with? path "/")
    (recur (clojure.string/replace-first path "/" ""))
    path))

(defn get-header [{headers :headers} header-name]
  (when (seq headers)
    (loop [[[key val] & rest] (seq headers)]
      (cond (.equalsIgnoreCase ^String header-name (name key)) val
            rest (recur rest)))))

(defn https-req? [req] (= (get-header req "x-forwarded-proto") "https"))

(defn req-scheme [req] (if (https-req? req) "https" (:scheme req)))

(defn qualify-relative-path [req path]
  (str (name (req-scheme req)) "://" (get-header req "host") "/" (trim-leading-slashes path)))

(defn- json-type? [type]
  (and (string? type)
       (clojure.string/includes? type "application/json")))

(defn- maybe-encode-http-request-body [params]
  (let [content-type (get-header params "Content-Type")
        json?        (json-type? content-type)
        encode?      (let [body (:body params)] (and body (not (string? body))))]
    (if encode?
      (update params :body cheshire/generate-string)
      params)))

(defn- maybe-parse-http-response-body [response]
  (if (and (string? (:body response))
           (json-type? (get-header response "content-type")))
    (update response :body cheshire/parse-string keyword)
    response))

(defn- throw-on-http-error [{:keys [error opts] :as response}]
  (when (and error opts) (throw (ex-info "Exception in http request" opts error)))
  (when error (throw (new Exception "Exception in http request" error)))
  response)

(defn- status-ok? [status] (and (number? status) (<= 200 status 299)))

(defn- throw-on-http-status [{:keys [opts status body] :as response}]
  (cond (status-ok? status) response
        (map? body)         (throw (ex-info "Exception in http request" response
                                            (ex-info (str status) body)))
        :else               (throw (ex-info "Exception in http request" response
                                            (new Exception (str body))))))

(defn http-req [params]
  (-> params
      maybe-encode-http-request-body
      http/request
      deref
      maybe-parse-http-response-body
      throw-on-http-error
      throw-on-http-status))

(def http-fetch (comp :body http-req))



(def ^java.nio.charset.Charset utf8 java.nio.charset.StandardCharsets/UTF_8)

(defn base64-encode [^String value]
  (when value
    (-> (java.util.Base64/getUrlEncoder)
        (.encode (.getBytes value utf8))
        (String. utf8))))

(defn base64-decode [^String value]
  (when value
    (-> (java.util.Base64/getUrlDecoder)
        (.decode (.getBytes value utf8))
        (String. utf8))))

(def base64-pr (comp base64-encode pr-str))

(def base64-read (comp clojure.edn/read-string base64-decode))

(defn uri-encode [value]
  (when (string? value)
    (java.net.URLEncoder/encode value (.toString utf8))))

(defn uri-decode [value]
  (when (string? value)
    (java.net.URLDecoder/decode value (.toString utf8))))

(defn uri-decode-full [value]
  (let [decoded (uri-decode value)]
    (if (= decoded value) decoded (recur decoded))))

(defn perverse-encode [value]
  (-> value cheshire/generate-string uri-encode base64-encode))

(defn perverse-decode [value]
  (-> value base64-decode uri-decode (cheshire/parse-string keyword)))
