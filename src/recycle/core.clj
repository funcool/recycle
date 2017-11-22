(ns recycle.core
  (:require [clojure.core.async :as a]
            [recycle.util :as u]))

;; --- Helpers


(defn service?
  [v]
  (::service v))

(defn service-factory?
  [v]
  (::factory v))

;; --- Main API

(def ^:dynamic *timeout*
  "Maximum number of milliseconds to wait for a response from a
  service. This variable by default is nil, meaning the value used
  when creating the service is used."
  nil)

(defn- default-receive
  "A default implementation for `:receive`."
  [& _]
  (ex-info "service do not implement :receive" {}))

(declare initialize-service)
(declare start!)

(defn service
  "Create a service using the spec map as a blueprint.
  The spec map may contain any of the following keys (all of the are
  optional):

   - `:init`:       initialization hook, receives the service spec and optionally some
                    config parameter and it should return the internal state instance;
                    this parameter is optional.
   - `:stop`:       termination hook, receives the service spec, internal state and
                    allows proper resource clearing; this parameter is optional.
   - `:error`:      error hook, receives the service spec, internal state and the
                    exception instance; it should handle it or just return the exception
                    instance as is; this parameter is optional.
   - `:receive`:    function that receives the internal state and variable number of args
                    identifying the message sent to the service.
   - `:timeout`:    default maximum number of milliseconds to wait for a response
                    from the service (default 1min).
   - `:buf-or-n`:   core.async buffer or size of buffer to use for the
                    service communication channel (default 1024).
  "
  [spec]
  (letfn [(factory [{:keys [::instances] :as options}]
            (let [options (dissoc options ::instances)]
              (-> (assoc spec :options options :instances instances)
                  (initialize-service))))]
    {::spec spec
     ::factory factory}))

(defn create
  "Create an instance of the provided service."
  ([sf]
   (create sf nil))
  ([sf options]
   {:pre [(service-factory? sf)]}
   ((::factory sf) options)))

(defn started?
  "Check if service is started. Returns true if it is, false if it's not."
  [{:keys [::status] :as service}]
  {:pre [(service? service)]}
  (= @status ::started))

(defn stopped?
  "Check if service is stopped. Returns true if it is, false if it's not."
  [{:keys [::status] :as service}]
  {:pre [(service? service)]}
  (= @status ::stopped))

(declare initialize-loop)

(defn start!
  [{:keys [::status ::stop-ch ::local ::init ::options] :as service}]
  {:pre [(service? service)]}
  (when (compare-and-set! status ::stopped ::started)
    (vreset! local (init options))
    (vreset! stop-ch (a/chan))
    (initialize-loop service)
    service))

(defn stop!
  [{:keys [::status ::local ::stop-ch ::stop] :as service}]
  {:pre [(service? service)]}
  (when (compare-and-set! status ::started ::stopped)
    (a/close! @stop-ch) ;; Notify the internal loop that the service is stoped
    (stop @local)
    (vreset! stop-ch nil)
    (vreset! local nil)))

(defn with-state
  "A function that allows return a value attached to new local state."
  [value state]
  {::with-state true
   ::local state
   ::value value})

(defn ask!
  [{:keys [::inbox-ch ::timeout] :as service} & args]
  (let [output (a/chan 1)
        timeout (a/timeout timeout)
        message [output args]]
    (a/go
      (let [[val port] (a/alts! [[inbox-ch message] timeout])]
        (if (identical? port timeout)
          (ex-info "put message to service timed out" {})
          (let [[val port] (a/alts! [output timeout])]
            (if (identical? port timeout)
              (ex-info "take message result from service timed out" {})
              val)))))))

(defn ask!!
  [service & args]
  (let [result (a/<!! (apply ask! service args))]
    (if (instance? Throwable result)
      (throw result)
      result)))

;; --- Implementation

(defn- handle-message
  [{:keys [::receive ::local]} [out args]]
  (a/go-loop [result (u/try-on (apply receive @local args))]
    (cond
      (u/chan? result)
      (recur (a/<! result))

      (and (map? result)
           (::with-state result))
      (do
        (vreset! local (::local result))
        (a/>! out (::value result))
        (a/close! out))

      :else
      (do
        (a/>! out result)
        (a/close! out)))))

(defn- initialize-loop
  [{:keys [::inbox-ch ::stop-ch ::instances] :as service}]
  (dotimes [i instances]
    (a/go-loop []
      (let [[msg port] (a/alts! [@stop-ch inbox-ch] :priority true)]
        (when (identical? port inbox-ch)
          (a/<! (handle-message service msg))
          (recur))))))

(defn- initialize-service
  [{:keys [init stop error receive buf-or-n timeout options instances]
    :or {init u/noop
         stop u/noop
         error identity
         buf-or-n 1024
         instances 1
         receive default-receive}
    :as spec}]
  (let [inbox-ch (a/chan (if (integer? buf-or-n) buf-or-n (buf-or-n)))
        stop-ch (volatile! nil)
        timeout (or timeout *timeout* 60000)
        status (atom ::stopped)
        local (volatile! nil)]
    {::service true
     ::instances instances
     ::options options
     ::buf-or-n buf-or-n
     ::init init
     ::stop stop
     ::receive receive
     ::inbox-ch inbox-ch
     ::stop-ch stop-ch
     ::timeout timeout
     ::status status
     ::local local}))


