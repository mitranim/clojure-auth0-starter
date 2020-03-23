(ns app.srv
  (:require
    [com.mitranim.forge :as forge]
    [com.stuartsierra.component :as component]
    [org.httpkit.server :refer [run-server]]
    [hiccup.page :refer [html5]]
    [compojure.core :as compojure :refer [GET]]
    [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
    [clojure.walk :refer [keywordize-keys]]
    [app.util :as util :refer [getenv]]
    [app.auth :as auth]
    [clojure.pprint]
    )
  (:import
    [org.httpkit.server HttpServer]))

(set! *warn-on-reflection* true)



(def styles "
* {
  font-family: Menlo, Consolas, 'Helvetica Neue', Roboto, 'Segoe UI', Verdana, sans-serif;
  font-size: 18px;
}

body {
  padding: 1rem;
  max-width: 80ch;
}

pre {
  white-space: pre-wrap;
  word-break: break-all;
}
")


(defn html-head [& content]
  [:head
   [:base {:href "/"}]
   [:meta {:charset "utf-8"}]
   [:link {:rel "icon" :href "data:;base64,="}]
   content])


(defn pp-str [value] (with-out-str (clojure.pprint/pprint value)))


(defn index-page [req]
  (html5
    (html-head
      [:style styles]
      [:title "Index"])
    [:body
     [:h3 "Auth0 Login"]
     [:p
      "Follow the setup instructions in the readme, then try to login. "
      "You should be returned to this page, and it should display user details."]
     [:p "ID token from cookie: "
      [:pre (pp-str (-> req :cookies (get (name auth/ID_COOKIE)) :value))]]
     [:p "Decoded ID token: "
      [:pre (pp-str (:user-meta req))]]
     [:p "Fetched user: "
      [:pre (pp-str (:user req))]]
     [:p [:a {:href "/login"} "Login"]]
     [:p [:a {:href "/logout"} "Logout"]]
     ]))


(defn login-page [req]
  (let [client-id    (getenv "AUTH0_CLIENT_ID")
        domain       (getenv "AUTH0_DOMAIN")
        return-uri   (get-in req [:query-params "return-uri"])
        callback-uri (util/qualify-relative-path req "/auth/callback")]
    (html5
      (html-head
        [:title "Login"]
        [:style "body {background-color: rgba(0, 0, 0, 0.05)}"])
      [:body
       [:div#root {:style "height: 100vh"}]
       [:script {:src "https://cdn.auth0.com/js/lock/10.18/lock.min.js"}]
       [:script (str "
var auth0Lock = new Auth0Lock('" client-id "', '" domain "', {
  auth: {
    redirect: true,
    responseType: 'code',
    params: {
      state: '" (util/perverse-encode {:return-uri return-uri :callback-uri callback-uri}) "'
    },
    redirectUrl: '" callback-uri "',
  },
  container: 'root',
})
auth0Lock.show()
")]])))


(defn logout-page [req]
  (let [client-id  (getenv "AUTH0_CLIENT_ID")
        domain     (getenv "AUTH0_DOMAIN")
        return-uri (get-in req [:query-params "return-uri"])
        search     (:query-string req)
        return-to  (->> (str "/auth/logout" (when search (str "?" search)))
                        (util/qualify-relative-path req)
                        util/uri-encode)
        location   (str "https://" domain "/v2/logout?client_id=" client-id
                        "&returnTo=" return-to)]
  {:status 303
   :headers {"location" location}}))



(def handler
  (->
    (compojure/routes
      (GET "/"              [] index-page)
      (GET "/login"         [] login-page)
      (GET "/logout"        [] logout-page)
      (GET "/auth/callback" [] auth/login-callback)
      (GET "/auth/logout"   [] auth/logout-callback))
    auth/wrap-authenticate
    (wrap-defaults site-defaults)
    forge/wrap-development-features))



(defrecord Srv [^HttpServer http-server]
  component/Lifecycle
  (start [this]
    (when http-server
      (.stop http-server 100))
    (let [port (Long/parseLong (getenv "LOCAL_PORT"))]
      (assoc this :http-server
        (-> (run-server handler {:port port}) meta :server))))
  (stop [this]
    (when http-server
      (.stop http-server 100))
    (assoc this :http-server nil)))
