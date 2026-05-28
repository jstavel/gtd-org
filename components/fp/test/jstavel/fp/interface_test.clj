(ns jstavel.fp.interface-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [jstavel.fp.interface :as fp])
  (:import (java.nio.file Files Path LinkOption FileVisitOption)
           (java.nio.file.attribute FileAttribute)
           (java.util UUID)
           (java.util.stream Collectors)))

(def ^:private temp-dir (atom nil))

(defn- delete-recursively [^Path path]
  (when (Files/exists path (into-array LinkOption []))
    (with-open [stream (Files/walk path (into-array FileVisitOption []))]
      (let [paths-to-delete (-> stream
                                .collect (Collectors/toList)
                                (sort-by #(.toString %) #(compare %2 %1)))] ; Reverse sort to delete files before directories
        (doseq [p paths-to-delete]
          (Files/delete p))))))

(defn- setup-temp-dir [f]
  (let [path (Files/createTempDirectory (str "fp-test-" (UUID/randomUUID)) (into-array FileAttribute []))]
    (reset! temp-dir path)
    (f) ; Run the tests
    (delete-recursively path))) ; Cleanup after tests

(use-fixtures :each setup-temp-dir)

(deftest list-audio-files-empty-directory-test
  (testing "should return an empty sequence for an empty directory"
    (is (empty? (fp/list-audio-files @temp-dir)))))
