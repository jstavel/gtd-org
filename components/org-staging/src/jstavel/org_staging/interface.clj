(ns jstavel.org-staging.interface
  (:require
   [jstavel.org-staging.core :as core]))

(defn deterministic-sha256-path
  "Calculates a deterministic SHA-256 hash for a given file or directory path.
   See `jstavel.org-staging.core/deterministic-sha256-path` for details."
  [path]
  (core/deterministic-sha256-path path))

(defn build-asset-index
  "Recursively scans the `assets/objects/` directory and builds an in-memory index.
   See `jstavel.org-staging.core/build-asset-index` for details."
  [assets-objects-root]
  (core/build-asset-index assets-objects-root))

(defn scan-and-stage-downloads
  "Scans the `downloads-path` for new `.mp3` files, calculates their hashes,
   and appends `TODO` entries to `staging.org` for new, not-yet-ingested files.
   This function adheres to a read-only invariant for `downloads-path`.
   See `jstavel.org-staging.core/scan-and-stage-downloads` for details."
  [downloads-path assets-objects-root staging-org-path]
  (core/scan-and-stage-downloads downloads-path assets-objects-root staging-org-path))

(defn read-staging-org
  "Parses the local `staging.org` file and extracts the `:HASH:` property
   from all `* TODO` entries. Returns a set of these hashes for quick lookup.
   Handles cases where the file does not exist or is empty gracefully.
   See `jstavel.org-staging.core/read-staging-org` for details."
  [staging-org-path]
  (core/read-staging-org staging-org-path))

(defn append-to-staging-org
  "Safely appends a new Org-mode entry string to the local `staging.org` file.
   Ensures atomic write/append operations and adds a leading newline.
   See `jstavel.org-staging.core/append-to-staging-org` for details."
  [staging-org-path entry-string]
  (core/append-to-staging-org staging-org-path entry-string))
