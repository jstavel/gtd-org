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
      (let [paths-to-delete (->> stream
                                 .iterator
                                 iterator-seq
                                 vec ; Convert to a vector to ensure it's fully realized before sorting
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

(deftest sha256-file-known-content-test
  (testing "should return the correct SHA-256 hash for a file with known content"
    (let [file-name "test-file.txt"
          file-path (.resolve ^Path @temp-dir file-name)
          content "hello world"
          expected-hash "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"] ; Correct SHA-256 for "hello world"
      (Files/write file-path (.getBytes content) (into-array java.nio.file.OpenOption []))
      (is (= expected-hash (fp/sha256-file file-path)))))

  (testing "should return the correct SHA-256 hash for a larger file"
    (let [file-name "large-test-file.txt"
          file-path (.resolve ^Path @temp-dir file-name)
          content (apply str (repeat 1000 "This is a line of content for the large file.\n")) ; ~50KB
          ; Correct SHA-256 hash of the repeated string content
          expected-hash "dbccad3d95fa31ab43e7912cf0d3289d4818a314f8e389b38bbcce37ea65d210"]
      (Files/write file-path (.getBytes content) (into-array java.nio.file.OpenOption []))
      (is (= expected-hash (fp/sha256-file file-path))))))

(deftest sha256-file-empty-file-test
  (testing "should return the correct SHA-256 hash for an empty file"
    (let [file-name "empty-file.txt"
          file-path (.resolve ^Path @temp-dir file-name)
          expected-hash "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"]
      (Files/createFile file-path (into-array FileAttribute []))
      (is (= expected-hash (fp/sha256-file file-path))))))

(deftest sha256-file-non-existent-file-test
  (testing "should return nil for a non-existent file"
    (let [file-name "non-existent.txt"
          file-path (.resolve ^Path @temp-dir file-name)]
      (is (nil? (fp/sha256-file file-path))))))
