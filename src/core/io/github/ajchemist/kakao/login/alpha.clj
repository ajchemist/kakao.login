(ns io.github.ajchemist.kakao.login.alpha
  (:require
   [clojure.string :as str]
   [clj-http.client :as http]
   [user.ring.alpha :as user.ring]
   ))


(set! *warn-on-reflection* true)


(def ^{:arglists '([request] [request response raise])}
  client
  (-> http/request
    (user.ring/wrap-meta-response)))


(defn user-me
  "Return {:id <id> :kakao_account ...}"
  [params {:keys [token]}]
  (client
    (merge
      params
      {:url            "https://kapi.kakao.com/v2/user/me"
       :request-method :get
       :as             :json
       :headers        {"Authorization" (str "Bearer " token)}})))


(defn access-token-info
  "Return {:id <id> :kakao_account ...}"
  [params {:keys [token]}]
  (client
    (merge
      params
      {:url            "https://kapi.kakao.com/v1/user/access_token_info"
       :request-method :get
       :as             :json
       :headers        {"Authorization" (str "Bearer " token)}})))


(comment
  (defn routes
    [{:keys [:oauth2/prefix-uri :oauth2/profile :oauth2/complete-handler] :as opts}]
    (let [prefix-uri   (or (util/uri-path-component prefix-uri) "/kakao")
          redirect-uri "/oauth2/callback"
          landing-uri   "/oauth2/complete"]
      [prefix-uri
       {:oauth2/profile (merge
                          profile
                          {:id               :kakao
                           :authorize-uri    "https://kauth.kakao.com/oauth/authorize"
                           :access-token-uri "https://kauth.kakao.com/oauth/token"
                           :redirect-uri     (str (util/unslash-right prefix-uri) redirect-uri)
                           :landing-uri      (str (util/unslash-right prefix-uri) landing-uri)})}
       ["/login"
        {:name :kakao.login/oauth2-launch}]
       ["/oauth2/callback"
        {:name :kakao.login/oauth2-redirect}]
       ["/oauth2/complete"
        {:name :kakao.login/oauth2-landing}]])))


(set! *warn-on-reflection* false)
