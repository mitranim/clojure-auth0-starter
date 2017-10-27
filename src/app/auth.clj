(ns app.auth
  (:require
    [com.mitranim.forge :as forge]
    [clojure.pprint :refer [pprint]]
    [buddy.sign.jwt :as jwt]
    [app.util :as util :refer [getenv]]
  ))

(set! *warn-on-reflection* true)



(def SIGNING_ALGORITHM :hs256)

(def ID_COOKIE :app-id-token)



(defn auth0-fetch [url params]
  (-> {:url (str "https://" (getenv "AUTH0_DOMAIN") "/" url)
       :headers {"Content-Type" "application/json"
                 "Authorization" (str "Bearer " (getenv "AUTH0_API_KEY"))}}
      (merge params)
      util/http-fetch))



(defn exchange-code-for-tokens [code {callback-uri :callback-uri}]
  (util/http-fetch
    {:url (str "https://" (getenv "AUTH0_DOMAIN") "/oauth/token")
     :method :post
     :headers {"Content-Type" "application/json"}
     :body {:code          code
            :grant_type    :authorization_code
            :client_id     (getenv "AUTH0_CLIENT_ID")
            :client_secret (getenv "AUTH0_CLIENT_SECRET")
            :redirect_uri  callback-uri}}))



(def user-fields #{:user_id :email :email_verified :name :picture})



(defn fetch-user [user-id]
  (auth0-fetch (str "api/v2/users/" (util/uri-encode user-id))
               {:query-params {:fields (clojure.string/join "," (map name user-fields))}}))



(defn decode-verify [token]
  (jwt/unsign token (getenv "AUTH0_CLIENT_SECRET") {:alg SIGNING_ALGORITHM}))

(defn decode-or-report [token]
  (try (decode-verify token)
    (catch clojure.lang.ExceptionInfo err
      (when-not (= :validation (:type (ex-data err)))
        (throw err))
      (binding [*out* *err*]
        (println "Token validation failed:")
        (prn err)))))


(defn login-callback [req]
  (let [code         (-> req :query-params (get "code"))
        state        (-> req :query-params (get "state") util/perverse-decode)
        return-uri   (not-empty (:return-uri state))
        access-info  (exchange-code-for-tokens code state)
        access-token (:access_token access-info)
        id-token     (:id_token access-info)
        {user-id :sub expiration-secs :exp} (decode-verify id-token)
        valid-for    (- expiration-secs (quot (System/currentTimeMillis) 1000))]

    {:status 303
     :headers {"Location" (or return-uri "/")}
     :cookies {ID_COOKIE
               {:value id-token
                :http-only true
                :secure (util/https-req? req)
                :path "/"
                :max-age valid-for}}}))


(defn logout-callback [req]
  (let [return-uri (not-empty (-> req :query-params (get "return-uri")))]
    {:status 303
     :headers {"Location" (or return-uri "/")}
     :cookies {ID_COOKIE {:value "" :path "/" :max-age 0}}}))

(defn get-id-token [cookies]
  (or (get cookies (keyword ID_COOKIE))
      (get cookies (name ID_COOKIE))))

(defn wrap-authenticate [handler]
  (fn [request]
    (let [id-token (-> request :cookies get-id-token :value)
          user (when id-token (decode-or-report id-token))]
      (handler (assoc request :user user)))))
