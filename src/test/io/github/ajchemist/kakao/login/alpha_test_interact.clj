(ns io.github.ajchemist.kakao.login.alpha-test-interact
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.java.shell :as jsh]
   [clojure.string :as str]
   [io.github.ajchemist.kakao.login.alpha :as kakao.login]
   [io.github.ajchemist.kakao.login.alpha.oauth :as kakao.login.oauth]
   [user.ring.alpha.model.integrant :as ig.ring]
   [ring.util.response :as response]
   [integrant.core :as ig]
   [muuntaja.core :as muuntaja]
   [reitit.ring.middleware.parameters]
   [reitit.ring.middleware.muuntaja]
   [reitit.ring.middleware.exception]
   [reitit.core :as reitit]
   [reitit.ring]
   [ring.middleware.session]
   [ring.middleware.params]
   [ring.middleware.file]
   ))


(defn pass-show
  [entry]
  (str/trim-newline (:out (jsh/sh "pass" "show" entry))))


(def ring-router
  (reitit.ring/router
    [["/kakao"
      {:middleware [[kakao.login.oauth/wrap-oauth2 {:kakao
                                                    {:client-id        (pass-show "developers.kakao.com/console/app/502435/client_id")
                                                     :client-secret    (pass-show "developers.kakao.com/console/app/502435/client_secret")
                                                     :authorize-uri    "https://kauth.kakao.com/oauth/authorize"
                                                     :access-token-uri "https://kauth.kakao.com/oauth/token"
                                                     :launch-uri       "/kakao/login"
                                                     :redirect-uri     "/kakao/oauth2/callback"
                                                     :landing-uri      "/kakao/oauth2/complete"}}]]}
      ["/login" {:name ::launch :handler (fn [_] (response/response nil))}]
      ["/oauth2/callback" {:name ::redirect :handler (fn [_] (response/response nil))}]
      ["/oauth2/complete"
       {:name ::landing
        :handler
        (fn [{:keys [session :oauth2/access-tokens]}]
          (if (empty? session)
            (-> (response/response "카카오 계정인증 실패.")
              (response/content-type "text/plain"))
            (response/response (kakao.login/user-me nil (:kakao access-tokens)))))}]]]
    {:data {:muuntaja   muuntaja/instance
            :middleware [reitit.ring.middleware.parameters/parameters-middleware
                         reitit.ring.middleware.muuntaja/format-middleware]}}))


(def ring-handler
  (reitit.ring/ring-handler
    ring-router
    (reitit.ring/routes
      (reitit.ring/create-default-handler))
    {:middleware [ring.middleware.session/wrap-session
                  #(ring.middleware.file/wrap-file % "dist")
                  kakao.login.oauth/wrap-access-tokens]}))


(def system
  (-> {[::ig.ring/jetty-server ::jetty-server] {:options {:host    "0.0.0.0"
                                                          :port    8090
                                                          :join?   false}
                                                :handler ring-handler}}
    (ig/prep)
    (ig/init)))


(comment
  (ig/halt! system)



  (reitit/match-by-path ring-router "/kakao/login")
  (reitit/match-by-path ring-router "/kakao/login2")


  (is (= (ring-handler {:uri "/kakao/oauth2/complete" :request-method :get})
         {:status 200, :headers {"Content-Type" "text/plain"}, :body "카카오 계정인증 실패."}))
  )
