(ns jstavel.fp.core
  (:require [clojure.string :as str])
  (:import
   (java.nio.file Files Path FileVisitOption LinkOption)
   (java.util.function Predicate)
   (java.security MessageDigest)
   (java.util.stream Stream)))

(defn- get-extension [^Path path]
  "Extracts the lowercase file extension from a java.nio.file.Path object.
  Returns nil if no extension or if it's a hidden file like '.bashrc'."
  (let [file-name (str (.getFileName path))]
    (when-let [dot-idx (str/last-index-of file-name ".")]
      (when (> dot-idx 0) ; Ensure it's not a hidden file like ".bashrc"
        (str/lower-case (subs file-name (inc dot-idx)))))))

(def ^:private audio-extensions
  "Set of supported audio file extensions for discovery (lowercase)."
  #{"mp3" "wav" "m4a"})

(defn list-audio-files
  "Recursively lists audio files (.mp3, .wav, .m4a) in a given directory.
  The search is case-insensitive for file extensions.
  Returns a lazy sequence of java.nio.file.Path objects."
  [^Path root-dir]
  (when (Files/isDirectory root-dir (into-array LinkOption []))
    (let [file-filter (fn [^Path path]
                        (and (Files/isRegularFile path (into-array LinkOption []))
                             (contains? audio-extensions (get-extension path))))
          path-stream ^Stream (Files/walk root-dir Integer/MAX_VALUE (into-array FileVisitOption []))]
      (->> path-stream
           (.iterator)
           iterator-seq
           (filter file-filter)
           lazy-seq))))

(defn sha256-file
  "Computes the SHA-256 hash of a given file.
  Returns the hash as a hexadecimal string."
  [^Path file-path]
  (when (Files/isRegularFile file-path (into-array LinkOption []))
    (let [digest (MessageDigest/getInstance "SHA-256")
          buffer (byte-array 8192)] ; Buffer size for reading file
      (with-open [is (Files/newInputStream file-path (into-array LinkOption []))]
        (loop []
          (let [bytes-read (.read is buffer)]
            (if (not= -1 bytes-read)
              (do
                (.update digest buffer 0 bytes-read)
                (recur))
              (let [hash-bytes (.digest digest)]
                (->> hash-bytes
                     (map (partial format "%02x"))
                     str/join)))))))))
