(ns io.github.ajchemist.kakao.login.alpha.util
  (:require
   [clojure.string :as str]
   )
  (:import
   java.net.URI
   ))


(defn unslash-right
  [s]
  (if (str/ends-with? s "/")
    (subs s 0 (dec (count s)))
    s))


(defprotocol URIComponent
  (uri-path-component [x]))


(extend-protocol URIComponent
  URI
  (uri-path-component [x] (.getPath x))

  String
  (uri-path-component [x] (uri-path-component (URI/create x))))
