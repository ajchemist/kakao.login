(ns io.github.ajchemist.kakao.login.alpha.ring)


(defn request-url
  "Ref ring.util.request/request-url, KOE303 issue
  Respect X-Forwarded-Proto"
  [request]
  (str (or
         (get-in request [:headers "x-scheme"])
         (get-in request [:headers "x-forwarded-proto"])
         (name (:scheme request)))
       "://"
       (get-in request [:headers "host"])
       (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))
