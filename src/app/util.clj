(ns app.util
  (:require
    [com.mitranim.forge :as forge]
    [org.httpkit.client :as http]
    [cheshire.core :as cheshire :refer [generate-string parse-string]]
    [clojure.edn]))

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



(defn err? [value]
  (and (instance? clojure.lang.ILookup value)
       (contains? value :error)))



(defn promise? [value]
  (and
    (instance? clojure.lang.IDeref value)
    (instance? clojure.lang.IBlockingDeref value)
    (instance? clojure.lang.IPending value)))

(defn maybe-force [value] (if (promise? value) @value value))

(defn map-promise
  "Creates a read-only promise that applies the given function to the
  eventual result of the given promise. Also works on futures. Works
  like flatmap: automatically resolves promises produced by the inner function.

  Usage: (map-promise inc (future 10))"
  [fun promise] {:pre [(ifn? fun) (promise? promise)]}
  (let [fun (memoize fun)]
    (reify
      clojure.lang.IDeref
      (deref [_] (maybe-force (fun @promise)))
      clojure.lang.IBlockingDeref
      (deref [_ t v]
        (let [result (deref promise t v)]
          (if (realized? promise)
            (maybe-force (fun @promise))
            result)))
      clojure.lang.IPending
      (isRealized [_] (realized? promise)))))

(defn map-result
  "Same as map-promise, but only when the value is not an error, following the
  conventional {:error ...} format."
  [fun promise]
  (map-promise #(if (err? %) % (fun %)) promise))



(defn- json-type? [type]
  (and (string? type)
       (clojure.string/includes? type "application/json")))

(defn- maybe-encode-http-request-body [params]
  (let [content-type (get-header params "content-type")
        json?        (json-type? content-type)
        encode?      (let [body (:body params)] (and body (not (string? body))))]
    (if encode?
      (update params :body generate-string)
      params)))

(defn- maybe-parse-http-response-body [response]
  (if (and (string? (:body response))
           (json-type? (get-header response "content-type")))
    (update response :body parse-string keyword)
    response))

(defn- throw-on-http-error [{:keys [error opts] :as response}]
  (when (and error opts) (throw (ex-info "Exception in http request" opts error)))
  (when error (throw (new Exception "Exception in http request" error)))
  response)

(defn- status-ok? [status] (and (number? status) (<= 200 status 299)))

(defn- throw-on-http-status [{:keys [opts status body] :as response}]
  (cond (status-ok? status) response
        (map? body) (throw (ex-info "exception in http request" response (ex-info (str status) body)))
        :else (throw (ex-info "exception in http request" response (new Exception (str body))))))

(defn http-req [params]
  (map-promise
    (comp throw-on-http-status throw-on-http-error maybe-parse-http-response-body)
    (http/request (maybe-encode-http-request-body params))))

(defn http-fetch [params]
  (map-result :body (http-req params)))



(def ^java.nio.charset.Charset UTF8 java.nio.charset.StandardCharsets/UTF_8)

(defn to-bytes
  (^bytes [value] (to-bytes value UTF8))
  (^bytes [value ^java.nio.charset.Charset coding]
   (cond (bytes? value) value
         (string? value) (.getBytes ^String value coding)
         :else nil)))

(defn bytes-to-str ^String [value]
  (cond (string? value) value
        (bytes? value) (new String ^bytes value UTF8)
        :else nil))

(defn base64-encode ^bytes [value]
  (when value
    (.encode (java.util.Base64/getUrlEncoder) (to-bytes value))))

(defn base64-decode ^bytes [value]
  (when value
    (.decode (java.util.Base64/getUrlDecoder) (to-bytes value))))

(defn uri-encode ^String [value]
  (when (string? value)
    (java.net.URLEncoder/encode value (.toString UTF8))))

(defn uri-decode ^String [value]
  (when (string? value)
    (java.net.URLDecoder/decode value (.toString UTF8))))

(defn perverse-encode ^String [value]
  (-> value generate-string uri-encode base64-encode bytes-to-str))

(defn perverse-decode ^String [value]
  (-> value base64-decode bytes-to-str uri-decode (parse-string keyword)))
