(defproject org.clojars.algernon/balabit.blobbity "0.1.0-SNAPSHOT"
  :description "Simple and stupid byte-format reading DSL."
  :url "https://github.com/algernon/balabit.blobbity"
  :license {:name "GNU General Public License - v3"
            :url "http://www.gnu.org/licenses/gpl.txt"
            :distribution :repo}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :profiles {:dev {:plugins [[lein-marginalia "0.7.1"]]}}
  :aliases {"docs" ["with-profile" "dev" "marg"]})
