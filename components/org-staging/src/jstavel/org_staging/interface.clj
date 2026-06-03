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
