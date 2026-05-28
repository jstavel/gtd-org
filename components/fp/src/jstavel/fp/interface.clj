(ns jstavel.fp.interface
  (:require [jstavel.fp.core :as core])
  (:import (java.nio.file Path)))

(defn list-audio-files
  "Recursively lists audio files (.mp3, .wav, .m4a) in a given directory.
  The search is case-insensitive for file extensions.
  Returns a lazy sequence of java.nio.file.Path objects."
  [^Path root-dir]
  (core/list-audio-files root-dir))

(defn sha256-file
  "Computes the SHA-256 hash of a given file.
  Returns the hash as a hexadecimal string."
  [^Path file-path]
  (core/sha256-file file-path))
