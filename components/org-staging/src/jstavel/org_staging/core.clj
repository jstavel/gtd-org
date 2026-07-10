(ns jstavel.org-staging.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :refer [sh]]
   [babashka.fs :as fs]
   [taoensso.timbre :as log]
   [jstavel.fp.interface :as fp])
  (:import
   (java.time ZoneId LocalDateTime)
   (java.time.format DateTimeFormatter)))

(defn deterministic-sha256-path
  "Calculates a deterministic SHA-256 hash for a given file or directory path.
   For files, it delegates to `fp/sha256-file`.
   For directories, it creates a temporary deterministic tar archive,
   hashes the archive, and ensures cleanup.
   Uses `tar` command for directory hashing to ensure cross-platform determinism
   and includes modification times for consistency.
   Returns the SHA-256 hash as a string."
  [^java.nio.file.Path path]
  (cond
    (fs/regular-file? path)
    (fp/sha256-file path)

    (fs/directory? path)
    (let [temp-tar-file          (fs/create-temp-file {:prefix "dir-hash-"
                                                       :suffix ".tar"})
          tar-command            ["tar"
                                  "--sort=name"
                                  "--mtime=@0"
                                  "--owner=0" "--group=0"
                                  "--numeric-owner"
                                  "--pax-option=exthdr.name=%d/PaxHeaders/%f,atime=@0" ; ensure consistent atime if pax headers are used
                                  "-cf" (str temp-tar-file)
                                  "-C" (str (fs/parent path)) ; Change to parent directory
                                  (fs/file-name path)] ; Archive the directory itself
          {:keys [exit out err]} (apply sh tar-command)]
      (if (zero? exit)
        (let [hash (fp/sha256-file temp-tar-file)]
          (fs/delete-if-exists temp-tar-file)
          hash)
        (do
          (log/error "Tar command failed for directory:" path "Error:" err)
          (fs/delete-if-exists temp-tar-file)
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
  [^java.nio.file.Path assets-objects-root]
  (if (not (fs/exists? assets-objects-root))
    (do
      (log/info "Assets objects root does not exist, returning empty index:" assets-objects-root)
      {})
    (try
      (->> (fs/list-dir assets-objects-root)
             (filter #(and (fs/directory? %)
                           (is-valid-uuid? (str (fs/file-name %)))                           ; check UUID
                           (re-matches #"[0-9a-fA-F]{2}" (str (fs/file-name (fs/parent %))))             ; check 'cd'
                           (re-matches #"[0-9a-fA-F]{2}" (str (fs/file-name (fs/parent (fs/parent %))))))) ; check 'ab'
             (reduce
               (fn [acc uuid-dir]
                 (let [raw-dir (fs/path uuid-dir "raw")]
                   (if (fs/directory? raw-dir)
                     (let [files (->> (fs/list-dir raw-dir)
                                      (filter fs/regular-file?))]
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
               {}))
      (catch Exception e
        (log/error e "Error building asset index for" assets-objects-root)
        {}))))

;; --- Metadata Extraction Helpers ---

(def ^:private date-pattern #"\d{4}-\d{2}-\d{2}")

(defn- extract-date-from-filename
  "Extract YYYY-MM-DD date from filename.
   '2026-07-07 16_53_06.mp3' -> '2026-07-07'
   Returns 'N/A' if no date pattern found."
  [^String filename]
  (if-let [m (re-find date-pattern filename)]
    m
    "N/A"))

(defn- ffprobe-value
  "Run ffprobe for a single value. Returns trimmed output or nil on failure."
  [^String file-path ^String entry]
  (let [{:keys [exit out]} (sh "ffprobe" "-v" "error"
                                "-show_entries" entry
                                "-of" "csv=p=0"
                                file-path)]
    (when (zero? exit)
      (not-empty (str/trim out)))))

(defn- ffprobe-duration-mmss
  "Read MP3 duration via ffprobe. Returns 'MM:SS' or 'N/A' on failure."
  [^String file-path]
  (if-let [dur-str (ffprobe-value file-path "format=duration")]
    (try
      (let [total-sec (Double/parseDouble dur-str)
            minutes   (long (quot total-sec 60))
            seconds   (long (rem total-sec 60))]
        (format "%02d:%02d" minutes seconds))
      (catch NumberFormatException _
        "N/A"))
    "N/A"))

(defn- ffprobe-bitrate
  "Read MP3 bitrate via ffprobe. Returns e.g. '128k' or 'N/A' on failure."
  [^String file-path]
  (if-let [br-str (ffprobe-value file-path "stream=bit_rate")]
    (str (quot (Long/parseLong br-str) 1000) "k")
    "N/A"))

(defn- filesize-bytes
  "Return file size in bytes as a formatted string, or 'N/A'."
  [^java.nio.file.Path file]
  (try
    (str (fs/size file))
    (catch Exception _
      "N/A")))

(defn- duration-minutes-ceil
  "Convert 'MM:SS' to ceiling minutes. '04:45' -> 5."
  [^String mmss]
  (if (= mmss "N/A")
    0
    (try
      (let [[_ m s] (re-find #"(\d+):(\d+)" mmss)
            total-sec (+ (* (Long/parseLong m) 60) (Long/parseLong s))]
        (int (Math/ceil (/ total-sec 60.0))))
      (catch Exception _
        0))))

(defn- logbook-timestamp
  "Generate a LOGBOOK timestamp in local timezone.
   Example: '[2026-07-08 Tue 15:30] Auto-detected by Staging Worker.'"
  []
  (let [formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd EEE HH:mm")
        now       (LocalDateTime/now (ZoneId/systemDefault))]
    (str "- [" (.format now formatter) "] Auto-detected by Staging Worker.")))

(defn- generate-org-entry
  "Build a complete staging.org entry from enriched metadata."
  [{:keys [hash source-path filename created duration-mmss bitrate filesize kind logbook-line]}]
  (let [headline (str "* TODO " (when (pos? (duration-minutes-ceil duration-mmss))
                                  (str "[" (duration-minutes-ceil duration-mmss) " min] "))
                       filename)]
    (str headline "\n"
         "  :PROPERTIES:\n"
         "  :HASH:       " hash "\n"
         "  :PLAUD-ID:   \n"
         "  :CREATED:    " created "\n"
         "  :DURATION:   " duration-mmss "\n"
         "  :BITRATE:    " bitrate "\n"
         "  :FILESIZE:   " filesize "\n"
         "  :SOURCE:     " source-path "\n"
         "  :SYNOPSIS:   \n"
         "  :CLEANUP:\n"
         "  :KIND:       " kind "\n"
         "  :END:\n"
         "  :DECISION:\n"
         "  - [ ] INGEST  (Move to assets/objects/UUID/)\n"
         "  - [ ] REJECT  (Delete from Downloads)\n"
         "  - [ ] IGNORE  (Keep in Downloads, remove from TODO)\n"
         "  :END:\n"
         "  :LOGBOOK:\n"
         "  " logbook-line "\n"
         "  :END:")))

(defn read-staging-org
  "Parses the local `staging.org` file and extracts the `:HASH:` property
   from all `* TODO` entries. Returns a set of these hashes for quick lookup.
   Handles cases where the file does not exist or is empty gracefully."
  [^java.nio.file.Path staging-org-path]
  (if (not (fs/exists? staging-org-path))
    (do
      (log/info "Staging.org file does not exist, returning empty set:" staging-org-path)
      #{})
    (try
      (with-open [reader (io/reader (.toFile staging-org-path))]
        (let [content (vec (line-seq reader))
              todo-entry-start-pattern #"\* TODO"
              hash-property-pattern #":HASH:\s*([0-9a-fA-F]{64})"]
          (->> content
               (partition-by #(re-matches todo-entry-start-pattern %)) ; Group lines by TODO entries
               (filter #(re-matches todo-entry-start-pattern (first %))) ; Keep only groups starting with TODO
               (keep
                (fn [entry-lines]
                  (some
                   (fn [line]
                     (when-let [match (re-matches hash-property-pattern (clojure.string/trim line))]
                       (second match)))
                   entry-lines)))
               (set))))
      (catch Exception e
        (log/error e "Error reading staging.org file:" staging-org-path)
        (throw e)))))

(defn append-to-staging-org
  "Safely appends a new Org-mode entry string to the local `staging.org` file.
   Ensures atomic write/append operations and adds a leading newline."
  [^java.nio.file.Path staging-org-path entry-string]
  (try
    (let [dir (fs/parent staging-org-path)]
      (when (and dir (not (fs/exists? dir)))
        (fs/create-dirs dir)))
    (with-open [writer (io/writer (.toFile staging-org-path) :append true)]
      (.write writer "\n")
      (.write writer entry-string))
    (catch Exception e
      (log/error e "Error appending to staging.org file:" staging-org-path)
      (throw e))))

(defn scan-and-stage-downloads
  "Scans the `downloads-path` for new `.mp3` files, extracts metadata
   (date from filename, duration/bitrate from ffprobe, hash from content),
   and appends enriched TODO entries to `staging.org` with Decision drawer
   and LOGBOOK."
  [^java.nio.file.Path downloads-path ^java.nio.file.Path assets-objects-root ^java.nio.file.Path staging-org-path]
  (log/info "Scanning downloads for new assets:" downloads-path)
  (let [audio-files (fp/list-audio-files downloads-path)
        mp3-files (filter #(.endsWith (str/lower-case (str (fs/file-name %))) ".mp3") audio-files)
        ingested-asset-hashes (build-asset-index assets-objects-root)
        pending-staging-hashes (read-staging-org staging-org-path)]

    (doseq [^java.nio.file.Path file mp3-files]
      (let [file-hash   (deterministic-sha256-path file)
            filename    (fs/file-name file)
            source-path (str file)
            file-path   (str file)]
        (cond
          (contains? ingested-asset-hashes file-hash)
          (log/info "Skipping already ingested file:" filename "(hash:" file-hash ")")

          (contains? pending-staging-hashes file-hash)
          (log/info "Skipping already pending file in staging.org:" filename "(hash:" file-hash ")")

          :else
          (let [metadata {:hash          file-hash
                          :source-path   source-path
                          :filename      filename
                          :created       (extract-date-from-filename filename)
                          :duration-mmss (ffprobe-duration-mmss file-path)
                          :bitrate       (ffprobe-bitrate file-path)
                          :filesize      (filesize-bytes file)
                          :kind          (if (re-find date-pattern filename)
                                           "Plaud-voice-record"
                                           "audio-file")
                          :logbook-line  (logbook-timestamp)}
                org-entry (generate-org-entry metadata)]
            (log/info "New" (:kind metadata) "found, adding to staging.org:" filename)
            (append-to-staging-org staging-org-path org-entry)))))))
