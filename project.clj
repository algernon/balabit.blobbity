(defproject com.balabit/balabit.blobbity "0.1.0"
  :description "Simple and stupid byte-format reading DSL."
  :url "https://github.com/algernon/balabit.blobbity"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :profiles {:dev {:plugins [[lein-marginalia "0.7.1"]
                             [lein-guzheng "0.4.5"]]}}
  :aliases {"docs" ["with-profile" "dev" "marg"
                    "src/balabit/blobbity.clj"]
            "test-coverage" ["with-profile" "dev" "guzheng" "balabit.blobbity"
                             "--" "test"]})
