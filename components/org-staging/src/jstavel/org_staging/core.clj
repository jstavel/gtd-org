(ns jstavel.org-staging.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :refer [sh]]
   [taoensso.timbre :as log]
   [jstavel.fp.interface :as fp])
  (:import
   (java.nio.file Files Path StandardOpenOption)
   (java.nio.file.attribute FileAttribute)
   ))


;; --- Helper functions from previous slices (deterministic-sha256-path and build-asset-index) ---

(defn deterministic-sha256-path
  "Calculates a deterministic SHA-256 hash for a given file or directory path.
   For files, it delegates to `fp/sha256-file`.
   For directories, it creates a temporary deterministic tar archive,
   hashes the archive, and ensures cleanup.
   Uses `tar` command for directory hashing to ensure cross-platform determinism
   and includes modification times for consistency.
   Returns the SHA-256 hash as a string."
  [^Path path]
  (cond
    (Files/isRegularFile path (into-array java.nio.file.LinkOption []))
    (fp/sha256-file path)

    (Files/isDirectory path (into-array java.nio.file.LinkOption []))
    (let [temp-tar-file (Files/createTempFile "dir-hash-" ".tar" (into-array FileAttribute []))
          tar-command ["tar"
                       "--sort=name"
                       "--mtime=@0"
                       "--owner=0" "--group=0"
                       "--numeric-owner"
                       "--pax-option=exthdr.name=%d/PaxHeaders/%f,atime=@0" ; ensure consistent atime if pax headers are used
                       "-cf" (.toString temp-tar-file)
                       "-C" (.toString (.-parent path)) ; Change to parent directory
                       (.getFileName path)] ; Archive the directory itself
          {:keys [exit out err]} (apply sh tar-command)]
      (if (zero? exit)
        (let [hash (fp/sha256-file temp-tar-file)]
          (Files/delete temp-tar-file)
          hash)
        (do
          (log/error "Tar command failed for directory:" path "Error:" err)
          (Files/delete temp-tar-file)
          (throw (ex-info (str "Failed to create tar archive for hashing directory: " path)
                          {:path path :exit exit :out out :err err})))))

    :else
    (throw (ex-info (str "Path is neither a file nor a directory: " path) {:path path}))))


(def ^:private uuid-pattern #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

(defn- is-valid-uuid? [s]
  (boolean (re-matches uuid-pattern s)))

(defn build-asset-index
  "Recursively scans the `assets/objects/` directory and builds an in-memory index.
   The index is a map of SHA-256 hash string -> `java.nio.file.Path` to the `original.ext` file.
   It filters for valid UUID-sharded directories (`ab/cd/<UUID>/`).
   Logs warnings for malformed structures.
   Returns a map of hashes to file paths."
  [^Path assets-objects-root]
  (if (not (Files/exists assets-objects-root (into-array java.nio.file.LinkOption [])))
    (do
      (log/info "Assets objects root does not exist, returning empty index:" assets-objects-root)
      {})
    (try
      (with-open [stream (Files/walk assets-objects-root (into-array java.nio.file.FileVisitOption []))]
        (->> (iterator-seq (.iterator stream))
             (filter #(and (Files/isDirectory % (into-array java.nio.file.LinkOption []))
                           (re-matches #"[0-9a-fA-F]{2}" (.getFileName (.-parent (.parent %)))) ; check 'cd'
                           (re-matches #"[0-9a-fA-F]{2}" (.getFileName (.parent (.parent (.parent %))))) ; check 'ab'
                           (is-valid-uuid? (.getFileName (.parent %))))) ; check UUID
             (reduce
               (fn [acc uuid-dir]
                 (let [raw-dir (.resolve uuid-dir "raw")] ; Per ADR 0005, 'original.ext' is directly under UUID, but specification.md pipeline implies 'raw/'
                   (if (and (Files/exists raw-dir (into-array java.nio.file.LinkOption []))
                            (Files/isDirectory raw-dir (into-array java.nio.file.LinkOption [])))
                     (let [files (->> (Files/list raw-dir)
                                      (iterator-seq)
                                      (filter #(Files/isRegularFile % (into-array java.nio.file.LinkOption []))))]
                       (if (= 1 (count files))
                         (let [file-path (first files)
                               hash (fp/sha256-file file-path)] ; Use fp/sha256-file for content hash
                           (assoc acc hash file-path))
                         (do
                           (log/warn "Malformed raw/ directory, expected exactly one file, found:" (count files) "in" raw-dir)
                           acc)))
                     (do
                       (log/warn "Raw directory not found or not a directory in UUID path:" raw-dir)
                       acc))))
               {})))
      (catch Exception e
        (log/error e "Error building asset index for" assets-objects-root)
        (throw e)))))

;; --- Current Slice Implementation ---

(defn read-staging-org
  "Parses the local `staging.org` file and extracts the `:HASH:` property
   from all `* TODO` entries. Returns a set of these hashes for quick lookup.
   Handles cases where the file does not exist or is empty gracefully."
  [^Path staging-org-path]
  (if (not (Files/exists staging-org-path (into-array java.nio.file.LinkOption [])))
    (do
      (log/info "Staging.org file does not exist, returning empty set:" staging-org-path)
      #{})
    (try
      (with-open [reader (io/reader (.toFile staging-org-path))]
        (let [content (line-seq reader)
              todo-entry-start-pattern #"\* TODO"
              hash-property-pattern #":HASH:\s*([0-9a-fA-F]{64})"]
          (->> content
               (partition-by #(re-matches todo-entry-start-pattern %)) ; Group lines by TODO entries
               (filter #(re-matches todo-entry-start-pattern (first %))) ; Keep only groups starting with TODO
               (keep
                 (fn [entry-lines]
                   (some
                     (fn [line]
                       (when-let [match (re-matches hash-property-pattern line)]
                         (second match)))
                     entry-lines)))
               (set))))
      (catch Exception e
        (log/error e "Error reading staging.org file:" staging-org-path)
        (throw e)))))

(defn append-to-staging-org
  "Safely appends a new Org-mode entry string to the local `staging.org` file.
   Ensures atomic write/append operations and adds a leading newline."
  [^Path staging-org-path entry-string]
  (try
    (let [dir (.getParent staging-org-path)]
      (when (and dir (not (Files/exists dir (into-array java.nio.file.LinkOption []))))
        (Files/createDirectories dir (into-array FileAttribute []))))
    (Files/write staging-org-path
                 (str "\n" entry-string)
                 (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/APPEND]))
    (catch Exception e
      (log/error e "Error appending to staging.org file:" staging-org-path)
      (throw e))))

(defn scan-and-stage-downloads
  "Scans the `downloads-path` for new `.mp3` files, calculates their hashes,
   and appends `TODO` entries to `staging.org` for new, not-yet-ingested files.
   This function adheres to a read-only invariant for `downloads-path`."
  [^Path downloads-path ^Path assets-objects-root ^Path staging-org-path]
  (log/info "Scanning downloads for new assets:" downloads-path)
  (let [audio-files (fp/list-audio-files downloads-path)
        mp3-files (filter #(.endsWith (str/lower-case (.toString (.getFileName %))) ".mp3") audio-files)
        ingested-asset-hashes (build-asset-index assets-objects-root)
        pending-staging-hashes (read-staging-org staging-org-path)]

    (doseq [^Path file mp3-files]
      (let [file-hash (deterministic-sha256-path file)
            filename (.getFileName file)
            source-path (.toString file)]
        (cond
          (contains? ingested-asset-hashes file-hash)
          (log/info "Skipping already ingested file:" filename "(hash:" file-hash ")")

          (contains? pending-staging-hashes file-hash)
          (log/info "Skipping already pending file in staging.org:" filename "(hash:" file-hash ")")

          :else
          (let [org-entry (str "* TODO " filename "\n"
                               ":PROPERTIES:\n"
                               ":HASH: " file-hash "\n"
                               ":SOURCE: " source-path "\n"
                               ":END:")]
            (log/info "New .mp3 file found, adding to staging.org:" filename)
            (append-to-staging-org staging-org-path org-entry)))))))
