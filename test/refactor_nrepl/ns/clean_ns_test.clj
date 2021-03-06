(ns refactor-nrepl.ns.clean-ns-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.config :as config]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.ns.clean-ns :refer [clean-ns]]
            [refactor-nrepl.ns.pprint :refer [pprint-ns]])
  (:import java.io.File))

(defn- absolute-path [^String path]
  (.getAbsolutePath (File. path)))

(defn- clean-msg [path]
  {:path (absolute-path path)})

(def ns1 (clean-msg "test/resources/ns1.clj"))
(def ns1-cleaned (core/read-ns-form-with-meta (absolute-path "test/resources/ns1_cleaned.clj")))
(def ns2 (clean-msg "test/resources/ns2.clj"))
(def ns2-cleaned (core/read-ns-form-with-meta (absolute-path "test/resources/ns2_cleaned.clj")))
(def ns2-meta (clean-msg "test/resources/ns2_meta.clj"))
(def ns3 (clean-msg "test/resources/ns3.clj"))
(def ns3-rebuilt (core/read-ns-form-with-meta (absolute-path "test/resources/ns3_rebuilt.clj")))
(def ns-with-exclude (clean-msg "test/resources/ns_with_exclude.clj"))
(def ns-with-rename (clean-msg "test/resources/ns_with_rename.clj"))
(def ns-with-rename-cleaned (core/read-ns-form-with-meta "test/resources/ns_with_rename_cleaned.clj"))
(def ns-with-unused-deps (clean-msg "test/resources/unused_deps.clj"))
(def ns-without-unused-deps (core/read-ns-form-with-meta
                             (absolute-path "test/resources/unused_removed.clj")))
(def cljs-file (clean-msg "test/resources/file.cljs"))
(def ns-referencing-macro (absolute-path "test/resources/ns_referencing_macro.clj"))
(def cljs-ns (clean-msg "test/resources/cljsns.cljs"))
(def cljs-ns-cleaned (core/read-ns-form-with-meta (absolute-path "test/resources/cljsns_cleaned.cljs")))

(def cljc-ns (clean-msg "test/resources/cljcns.cljc"))
(def cljc-ns-cleaned-clj (core/read-ns-form-with-meta (absolute-path "test/resources/cljcns_cleaned.cljc")))
(def cljc-ns-cleaned-cljs (core/read-ns-form-with-meta :cljs (absolute-path "test/resources/cljcns_cleaned.cljc")))

(def cljc-ns-same-clj-cljs (clean-msg "test/resources/cljcns_same_clj_cljs.cljc"))
(def cljc-ns-same-clj-cljs-cleaned (core/read-ns-form-with-meta (absolute-path "test/resources/cljcns_same_clj_cljs_cleaned.cljc")))

(def ns-with-shorthand-meta (clean-msg "test/resources/ns_with_shorthand_meta.clj"))

(def ns-with-multiple-shorthand-meta (clean-msg "test/resources/ns_with_multiple_shorthand_meta.clj"))

(def ns-with-inner-classes (clean-msg "test/resources/ns_with_inner_classes.clj"))

(def ns-using-dollar (clean-msg "test/resources/ns_using_dollar.clj"))

(deftest combines-requires
  (let [requires (core/get-ns-component (clean-ns ns2) :require)
        combined-requires (core/get-ns-component ns2-cleaned :require)]
    (is (= combined-requires requires))))

(deftest meta-preserved
  (let [cleaned (pprint-ns (clean-ns ns2-meta))]
    (is (.contains cleaned "^{:author \"Trurl and Klapaucius\"
      :doc \"test ns with meta\"}"))))

(deftest rewrites-use-to-require
  (let [requires (core/get-ns-component (clean-ns ns2) :use)
        combined-requires (core/get-ns-component ns2-cleaned :require)]
    (is (reduce
         #(or %1 (= %2 '[clojure
                         [edn :refer :all :rename {read-string rs}]
                         [instant :refer :all]
                         [pprint :refer [cl-format fresh-line get-pretty-writer]]
                         [string :refer :all]
                         [test :refer :all]]))
         false
         (tree-seq sequential? identity combined-requires)))))

(deftest keeps-clause-with-rename
  (let [requires (core/get-ns-component (clean-ns ns2) :use)
        combined-requires (core/get-ns-component ns2-cleaned :require)]
    (is (reduce
         #(or %1 (= %2 '[edn :refer :all :rename {read-string rs}]))
         false
         (tree-seq sequential? identity combined-requires)))))

(deftest test-sort-and-prefix-favoring
  (let [requires (core/get-ns-component (clean-ns ns1) :require)
        imports (core/get-ns-component (clean-ns ns1) :import)
        sorted-requires (core/get-ns-component ns1-cleaned :require)
        sorted-imports (core/get-ns-component ns1-cleaned :import)]
    (is (= sorted-requires requires))
    (is (= sorted-imports imports))))

(deftest throws-exceptions-for-unexpected-elements
  (is (thrown? IllegalArgumentException
               (clean-ns ns-with-exclude))))

(deftest throws-on-malformed-ns
  (is (thrown? IllegalStateException
               (core/read-ns-form-with-meta (.getAbsolutePath
                                   (File. "test/resources/clojars-artifacts.edn"))))))

(deftest preserves-other-elements
  (let [actual (clean-ns ns1)
        docstring (nth actual 2)
        author (nth actual 3)
        refer-clojure (nth actual 4)
        gen-class (nth actual 5)]
    (is (= (nth ns1-cleaned 2) docstring))
    (is (= (nth ns1-cleaned 3) author))
    (is (= (nth ns1-cleaned 4) refer-clojure))
    (is (= (nth ns1-cleaned 5) gen-class))))

(deftest removes-use
  (let [use-clause (core/get-ns-component ns1-cleaned :use)]
    (is (nil? use-clause))))

(deftest combines-multiple-refers
  (let [requires (clean-ns ns2)
        refers '[cl-format fresh-line get-pretty-writer]]
    (is (reduce
         #(or %1 (= %2 refers))
         false
         (tree-seq sequential? identity requires)))))

(deftest combines-multiple-refers-to-all
  (let [requires (clean-ns ns2)
        instant '[instant :refer :all]]
    (is (reduce
         #(or %1 (= %2 instant))
         false
         (tree-seq sequential? identity requires)))))

(deftest removes-unused-dependencies
  (let [new-ns (clean-ns ns-with-unused-deps)
        requires (core/get-ns-component new-ns :require)
        imports (core/get-ns-component new-ns :import)
        clean-requires (core/get-ns-component ns-without-unused-deps :require)
        clean-imports (core/get-ns-component ns-without-unused-deps :import)]
    (is (= clean-requires requires))
    (is (= clean-imports imports))))

(def artifact-ns '(ns refactor-nrepl.artifacts
                    (:require [clojure
                               [edn :as edn]
                               [string :as str]]
                              [clojure.data.json :as json]
                              [clojure.java.io :as io]
                              [nrepl
                               [middleware :refer [set-descriptor!]]
                               [misc :refer [response-for]]
                               [transport :as transport]]
                              [org.httpkit.client :as http]
                              [refactor-nrepl.externs :refer [add-dependencies]])
                    (:import java.util.Date)))

(deftest test-pprint-artifact-ns
  (let [path (.getAbsolutePath (File. "test/resources/artifacts_pprinted"))
        actual (pprint-ns (with-meta artifact-ns nil))
        expected (slurp path)]
    (is (= expected actual))))

(deftest handles-imports-when-only-enum-is-used
  (let [new-ns (clean-ns ns2)
        imports (core/get-ns-component new-ns :import)]
    (is (some #(= 'java.text.Normalizer %) imports))))

(deftest keeps-referred-macros-around
  (let [new-ns (clean-ns (clean-msg ns-referencing-macro))]
    ;; nil means no changes
    (is (nil? new-ns))))

(deftest handles-clojurescript-files
  (let [new-ns (clean-ns cljs-ns)]
    (is (= cljs-ns-cleaned new-ns))))

(deftest handles-cljc-files
  (let [new-ns (str (clean-ns cljc-ns))
        new-clj-ns (core/ns-form-from-string new-ns)
        new-cljs-ns (core/ns-form-from-string :cljs new-ns)]
    (is (= cljc-ns-cleaned-clj new-clj-ns))
    (is (= cljc-ns-cleaned-cljs new-cljs-ns))))

(deftest does-not-use-read-conditionals-when-ns-are-equal
  (is (= (clean-ns cljc-ns-same-clj-cljs)
         cljc-ns-same-clj-cljs-cleaned)))

(deftest respects-no-prune-option
  (config/with-config {:prune-ns-form false}
    (let [new-require (core/get-ns-component (clean-ns ns3) :require)
          expected-require (core/get-ns-component ns3-rebuilt :require)]
      (is (= expected-require new-require)))))

(deftest does-not-remove-ns-with-rename
  (is (= (nthrest ns-with-rename-cleaned 2) (nthrest (clean-ns ns-with-rename) 2))))

;; Order of stuff in maps aren't stable across versions which messes
;; with pretty-printing
(when (= (clojure-version) "1.7.0")
  (deftest test-pprint
    (let [ns-str (pprint-ns (clean-ns ns1))
          ns1-str (slurp (.getAbsolutePath (File. "test/resources/ns1_cleaned_and_pprinted")))]
      (is (= ns1-str ns-str)))))

(deftest preserves-shorthand-meta
  (let [cleaned (pprint-ns (clean-ns ns-with-shorthand-meta))]
    (is (re-find #"\^:automation" cleaned))))

(deftest preservres-multiple-shortand-meta
  (let [cleaned (pprint-ns (clean-ns ns-with-multiple-shorthand-meta))]
    (is (re-find #"\^:automation" cleaned))
    (is (re-find #"\^:multiple" cleaned))))

(deftest does-not-remove-dollar-sign-if-valid-symbol
  (let [cleaned (pprint-ns (clean-ns ns-using-dollar))]
    (is (re-find #"\[\$\]" cleaned))))

(deftest does-not-break-import-for-inner-class
  (let [cleaned (pprint-ns (clean-ns ns-with-inner-classes))]
    (is (re-find #":import.*Line2D\$Double" cleaned))))
