(ns jstavel.downloads-cleaner.interface-test
  (:require [clojure.test :as test :refer [deftest is]]
            [jstavel.downloads-cleaner.interface :as downloads-cleaner]
            [babashka.fs :as fs]))

(deftest test-list-files
  (let [test-dir "test-directory"
        _ (fs/create-dir test-dir)
        _ (fs/create-file (str test-dir "/file1.mp3"))
        _ (fs/create-file (str test-dir "/file2.txt"))
        _ (fs/create-file (str test-dir "/file3.mp3"))]
    (is (= (set (downloads-cleaner/list-files test-dir))
           #{"file1.mp3" "file3.mp3"}))
    (fs/delete-tree test-dir)))
