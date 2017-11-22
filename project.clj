(defproject recycle "0.1.0-SNAPSHOT"
  :description "Clojure library designed to manage application state heavily inspired by stuartsierra's component."
  :url "https://github.com/funcool/recycle"
  :license {:name "BSD (2-Clause)"
            :url  "http://opensource.org/licenses/BSD-2-Clause"}

  :jar-exclusions [#"\.swp|\.swo|user.clj"]

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.logging "0.4.0"]]

  :source-paths ["src"]
  :test-paths ["test"]

  :codeina {:sources ["src/clj"]
            :reader  :clojure}

  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
         :plugins [[funcool/codeina "0.5.0" :exclusions [org.clojure/clojure]]]}})
