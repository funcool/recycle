(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.walk :refer [macroexpand-all]]
            [clojure.pprint :refer [pprint]]
            [clojure.test :as test]
            [clojure.core.async :as a]
            [recycle.core :as r]))

;; --- Development Stuff

(defn run-tests
  ([] (test #"^recycle.tests.*"))
  ([o]
   (repl/refresh)
   (cond
     (instance? java.util.regex.Pattern o)
     (test/run-all-tests o)

     (symbol? o)
     (if-let [sns (namespace o)]
       (do (require (symbol sns))
           (test/test-vars [(resolve o)]))
       (test/test-ns o)))))

;; --- Experiments Code

;; Asychronous counter service
(def counter-service
  (r/service {:init (constantly 0)
             :receive (fn [counter message]
                         (a/go
                           (a/<! (a/timeout 10)) ;; simulate some async work
                           (r/with-state counter (inc counter))))}))

(def adder-service (r/service {:receive (fn [_ nums] (apply + nums))}))
(def greeter-service (r/service {:receive (fn [_ name] (str "Hello " name))}))

;; Hierarchical service
(def hierarchical-service
  (r/service
   ;; Service initialization
   {:init (fn [options]
            (let [adder (r/create adder-service)
                  hello (r/create greeter-service)]
              {:adder (r/start! adder)
               :hello (r/start! hello)}))

    ;; Service resource cleaining
    :stop (fn [{:keys [adder hello] :as local}]
            (r/stop! adder)
            (r/stop! hello))

    ;; Service on message hook
    :receive (fn [{:keys [adder hello] :as local} & [name rest]]
               (a/go
                 (case name
                   :add (a/<! (r/ask! adder rest))
                   :greets (a/<! (r/ask! hello rest)))))}))

(def service-a (r/create counter-service))
(def service-b (r/create hierarchical-service))

(r/start! service-a)
(r/start! service-b)
;; (r/stop! service)

