(ns jstavel.downloads-cleaner.interface
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            ))

(defn list-files
  "Lists files in a directory with optional filtering by extensions."
  [directory-path & {:keys [extensions] :or {extensions [".mp3"]}}]
  (let [xf (comp
            (filter (fn [path]
                      (some #(str/ends-with? (str (.getFileName path)) %) extensions)))
            (map #(str (.getFileName %))))
        ]
    (into #{} xf (fs/list-dir directory-path))))
