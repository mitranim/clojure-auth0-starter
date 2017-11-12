(ns app.auth
  (:require
    [com.mitranim.forge :as forge]
    [cheshire.core :as cheshire]
    [app.util :as util :refer [getenv]])
  (:import
    [java.security Signature]
    [java.security.cert Certificate CertificateFactory]))

(set! *warn-on-reflection* true)



(def ID_COOKIE :app-id-token)



(defn auth0-fetch [url params]
  (-> {:url (str "https://" (getenv "AUTH0_DOMAIN") "/" url)
       :headers {"content-type" "application/json"
                 "authorization" (str "Bearer " (getenv "AUTH0_API_KEY"))}}
      (merge params)
      util/http-fetch))



(defn exchange-code-for-tokens [code {callback-uri :callback-uri}]
  (util/http-fetch
    {:url (str "https://" (getenv "AUTH0_DOMAIN") "/oauth/token")
     :method :post
     :headers {"content-type" "application/json"}
     :body {:code          code
            :grant_type    :authorization_code
            :client_id     (getenv "AUTH0_CLIENT_ID")
            :client_secret (getenv "AUTH0_CLIENT_SECRET")
            :redirect_uri  callback-uri}}))



(def user-fields #{:user_id :email :email_verified :name :picture})



(defn fetch-user [user-id]
  (auth0-fetch
    (str "api/v2/users/" (util/uri-encode user-id))
    {:query-params {:fields (clojure.string/join "," (map name user-fields))}}))



; Auth0 default = RS256
(def ^:private ^Signature sign (Signature/getInstance "SHA256withRSA"))
(def ^:private ^CertificateFactory cf (CertificateFactory/getInstance "X.509"))

(defn ^Certificate to-cert [secret]
  (if (instance? Certificate secret)
    secret
    (.generateCertificate cf (clojure.java.io/input-stream (util/to-bytes secret)))))

(def ^:private ^Certificate cert (to-cert (slurp ".auth0.pem")))



(defn verify-signature [payload signature secret]
  (let [payload (util/to-bytes payload)
        signature (util/to-bytes signature)]
    (.initVerify sign (to-cert secret))
    (.update sign payload)
    (.verify sign signature)))

(defn- json-decode [value]
  (cheshire/parse-string (util/bytes-to-str (util/base64-decode value)) keyword))

(defn jwt-decode [jwt]
  (let [[header payload signature] (clojure.string/split jwt #"\.")]
    {:header (json-decode header)
     :payload (json-decode payload)}))

(defn jwt-decode-verify [jwt secret]
  (let [[header payload signature] (clojure.string/split jwt #"\.")
        signature (util/base64-decode signature)]
    (when-not (verify-signature (str header "." payload) signature secret)
      (throw (ex-info "signature verification failed" {:type ::validation :code ::verification})))
    (let [header (json-decode header)
          payload (json-decode payload)
          exp (:exp payload)]
      (when-not (and (integer? exp) (> (* exp 1000) (System/currentTimeMillis)))
        (throw (ex-info "token expired" {:type ::validation :code ::expiration})))
      {:header header
       :payload payload})))

(defn jwt-decode-or-report [jwt secret]
  (try (jwt-decode-verify jwt secret)
    (catch clojure.lang.ExceptionInfo err
      (if (= (:type (ex-data err)) ::validation)
        (binding [*out* *err*]
          (println "Token validation failed:")
          (prn err)
          nil)
        (throw err)))))


(defn login-callback [req]
  (let [code         (-> req :query-params (get "code"))
        state        (-> req :query-params (get "state") util/perverse-decode)
        return-uri   (not-empty (:return-uri state))
        access-info  @(exchange-code-for-tokens code state)
        access-token (:access_token access-info)
        id-token     (:id_token access-info)
        {user-id :sub expiration-secs :exp} (:payload (jwt-decode-verify id-token cert))
        valid-for    (- expiration-secs (quot (System/currentTimeMillis) 1000))]

    {:status 303
     :headers {"location" (or return-uri "/")}
     :cookies {ID_COOKIE
               {:value id-token
                :http-only true
                :secure (util/https-req? req)
                :path "/"
                :max-age valid-for}}}))



(defn logout-callback [req]
  (let [return-uri (not-empty (-> req :query-params (get "return-uri")))]
    {:status 303
     :headers {"location" (or return-uri "/")}
     :cookies {ID_COOKIE {:value "" :path "/" :max-age 0}}}))



(defn wrap-authenticate [handler]
  (fn [request]
    (let [id-token (-> request :cookies (get (name ID_COOKIE)) :value)
          user-meta (when id-token (:payload (jwt-decode-or-report id-token cert)))
          user-id (when id-token (:sub user-meta))
          ; This must be replaced by database fetch.
          user (when user-id @(fetch-user user-id))]
      (handler (merge request {:user-meta user-meta :user user})))))
