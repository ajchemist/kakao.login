(ns io.github.ajchemist.kakao.login.alpha.oauth
  (:require
   [clojure.stacktrace :as stacktrace]
   [clojure.string :as str]
   [clj-http.client :as http]
   [ring.util.codec :as codec]
   [ring.util.response :as response]
   [crypto.random :as random]
   [user.ring.alpha]
   [io.github.ajchemist.kakao.login.alpha.ring :as ring]
   )
  (:import
   java.net.URI
   java.time.Instant
   java.util.Date
   ))


(set! *warn-on-reflection* true)


;;


(defn- valid-profile?
  [{:keys [client-id client-secret]}]
  (and (some? client-id) (some? client-secret)))


(defn- scopes
  [profile]
  (str/join " " (map name (:scopes profile))))


(defn- coerce-to-int
  [n]
  (if (int? n)
    n
    (parse-long n)))




(defn random-state
  []
  (-> (random/base64 9) (str/replace "+" "-") (str/replace "/" "_")))


;;


(defn redirect-uri
  ^String
  [request profile]
  (let [^String relpath (:redirect-uri profile)]
    (if (.getScheme (URI/create relpath))
      relpath
      (str (.resolve (java.net.URI/create (ring/request-url request)) relpath)))))


(defn authorize-uri
  ^String
  [request profile params]
  (let [^String uri (:authorize-uri profile)]
    (str uri
         (if (str/includes? uri "?") "&" "?")
         (codec/form-encode
           (-> params
             (update :response_type #(or % "code"))
             (update :redirect_uri #(or % (redirect-uri request profile)))
             (update :client_id #(or % (:client-id profile)))
             (cond-> (find params :scope) (assoc :scope (scopes profile))))))))


(defn redirect-to-autorize-uri
  [{:keys [session] :as request} profile]
  (let [state (random-state)]
    (-> (response/redirect (authorize-uri request profile {:state state}))
      (assoc :session (assoc session ::state state)))))


(defn- make-launch-handler
  [profile]
  (fn [request]
    (redirect-to-autorize-uri request profile)))



;;


(defn- add-header-credentials
  [params id secret]
  (assoc params :basic-auth [id secret]))


(defn- add-form-credentials
  [params id secret]
  (update params :form-params merge {:client_id id :client_secret secret}))


(defn- format-access-token
  [{{:keys [access_token expires_in refresh_token id_token] :as body} :body}]
  (cond-> {:token      access_token
           :extra-data (dissoc body :access_token :expires_in :refresh_token :id_token)}
    expires_in    (assoc :expires (Date/from (.plusSeconds (Instant/now) (-> expires_in coerce-to-int))))
    refresh_token (assoc :refresh-token refresh_token)
    id_token      (assoc :id-token id_token)))


(defn- get-authorization-code
  [request]
  (get-in request [:query-params "code"]))


(defn- get-access-token-request-form-params
  [request profile]
  {:grant_type   "authorization_code"
   :code         (get-authorization-code request)
   :redirect_uri (redirect-uri request profile)})


(defn get-access-token
  [request
   {:keys [access-token-uri client-id client-secret basic-auth?]
    :or   {basic-auth? false} :as profile}]
  (try
    (format-access-token
      (http/post
        access-token-uri
        (cond-> {:save-request? true
                 :accept        :json
                 :as            :json
                 :form-params   (get-access-token-request-form-params request profile)}
          basic-auth?       (add-header-credentials client-id client-secret)
          (not basic-auth?) (add-form-credentials client-id client-secret))))
    (catch clojure.lang.ExceptionInfo e
      (println (ex-message e) (get-in (ex-data e) [:request :http-url]) (:body (ex-data e))))
    (catch Exception e
      (stacktrace/print-stack-trace e))))


(defn refresh-access-token
  [request
   {:keys [access-token-uri client-id client-secret basic-auth?]
    :or   {basic-auth? false} :as profile}]
  (try
    (format-access-token
      (http/post
        access-token-uri
        (cond-> {:save-request? true
                 :accept        :json
                 :as            :json
                 :form-params   {:grant_type    "refresh_token"
                                 :refresh_token (get-in request [:query-params "refresh_token"])}}
          basic-auth?       (add-header-credentials client-id client-secret)
          (not basic-auth?) (add-form-credentials client-id client-secret))))
    (catch clojure.lang.ExceptionInfo e
      (println (ex-message e) (get-in (ex-data e) [:request :http-url]) (:body (ex-data e))))
    (catch Exception e
      (stacktrace/print-stack-trace e))))


;;


(defn- state-matches?
  [req]
  (= (get-in req [:session ::state])
     (get-in req [:query-params "state"])))


(defn- state-mismatch-handler
  [_]
  {:status 400, :headers {}, :body "State mismatch"})


(defn- no-auth-code-handler
  [_]
  {:status 400, :headers {}, :body "No authorization code"})


(defn- make-redirect-handler
  [profile]
  (let [state-mismatch-handler (:state-mismatch-handler profile state-mismatch-handler)
        no-auth-code-handler   (:no-auth-code-handler profile no-auth-code-handler)]
    (fn [{:keys [session] :or {session {}} :as request}]
      (cond
        (not (state-matches? request))
        (state-mismatch-handler request)

        (nil? (get-authorization-code request))
        (no-auth-code-handler request)

        :else
        (let [access-token (get-access-token request profile)]
          (-> (response/redirect (:landing-uri profile))
            (assoc :session (-> session
                              (assoc-in [::access-tokens (:id profile)] access-token)
                              (dissoc ::state)))))))))


(defn- assoc-access-tokens
  [request]
  (if-let [tokens (get-in request [:session ::access-tokens])]
    (update request :oauth2/access-tokens merge tokens)
    request))


(defn- parse-redirect-url
  [{:keys [^String redirect-uri]}]
  (.getPath (URI. redirect-uri)))


(defn wrap-oauth2
  [handler profiles]
  {:pre [(every? valid-profile? (vals profiles))]}
  (let [profiles  (into [] (map (fn [[id profile]] (assoc profile :id id))) profiles)
        launches  (into {} (map (juxt :launch-uri #(or (:launch-handler %) (make-launch-handler %)))) profiles)
        redirects (into {} (map (juxt parse-redirect-url #(or (:redirect-handler %) (make-redirect-handler %)))) profiles)]
    (fn [{:keys [uri] :as request}]
      (if-let [launch-handler (get launches uri)]
        (launch-handler request)
        (if-let [redirect-handler (get redirects uri)]
          (redirect-handler request)
          (handler request))))))


(defn wrap-access-tokens
  [handler]
  (user.ring.alpha/wrap-transform-request handler assoc-access-tokens))


(set! *warn-on-reflection* false)
