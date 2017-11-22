(ns recycle.util
  (:require [clojure.core.async.impl.protocols :as ap]))

(defn chan?
  "Check if provided value is like to ba core.async channel."
  [v]
  (satisfies? ap/ReadPort v))

(defmacro try-on
  "Wrap provided code in a try/catch block and return
  the result or the raised exception as value."
  [& body]
  `(try ~@body (catch Throwable e# e#)))

(defn map-second
  "Receives a function f, a collection coll of two-element vectors, and
  returns the same collection but with the second element of each
  vector replaced with the result of calling f with the original
  second element."
  [f coll]
  (map (fn [[k v]] [k (f v)]) coll))

(defn map-values
  "Receives a function f, a map m, and returns the same map but with the
  values replaced with result of calling f with the original value of
  each key."
  [f m]
  (into {} (map-second #(f %) m)))

(defn noop
  "No-op function. Receives any number of arguments and returns nil."
  [& _]
  nil)

(defn throwing
  "Returns a function of any args that raises exception e when called."
  [e]
  (fn [& _]
    (throw e)))
