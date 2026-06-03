# Current Slice: [H0/Runway] Implement Core Staging Worker Logic (org-staging)

## Context (incorporating ADR 0006: Local-First Data Storage)
With the `deterministic-sha256-path` and `build-asset-index` functionalities in place, the `org-staging` component is ready for its primary task: scanning the `~/Downloads/` directory and preparing new assets for human review. This slice implements the core "human-in-the-loop" workflow as described in ADR 0005 and `docs/specification.md`, focusing on detection, hashing, and registering `TODO` entries in `staging.org`.

**Crucially, in line with ADR 0006, all asset management in this phase (e.g., checking `assets/objects/` and `staging.org`) refers to *local* file system paths within `$ORG_ROOT`, not cloud storage.** The cloud will serve as a transient processing layer in later stages, but the primary asset store remains local.

## Goal
Implement the main `scan-and-stage-downloads` function that:
1. Monitors the user's `~/Downloads/` directory for new `.mp3` files (MVP decision).
2. Uses the `build-asset-index` to identify files already present in the `assets/objects/` store.
3. Checks `staging.org` to avoid duplicate `TODO` entries for files already registered or explicitly actioned.
4. For each newly discovered `.mp3` file, it creates a `TODO` entry in the **local** `$ORG_ROOT/staging.org` containing its `:HASH:` and `:SOURCE:` properties.
5. Ensures the entire process is idempotent and does not perform any physical file operations (move/delete) in `~/Downloads/`.

## Target Files
- `components/org-staging/src/jstavel/org_staging/core.clj`
- `components/org-staging/src/jstavel/org_staging/interface.clj`
- `components/org-staging/test/jstavel/org_staging/interface_test.clj`
- `docs/current_slice.md` (this file)

## Technical Steps

### 1. Implement `read-staging-org`
- In `components/org-staging/src/jstavel/org_staging/core.clj`:
    - Define `read-staging-org` to parse `staging.org` and extract the `:HASH:` property from all `* TODO` entries.
    - It should return a `set` of these hashes for quick lookup.
    - Handle cases where the **local** `staging.org` does not exist or is empty gracefully (return an empty set).
    - This will likely involve reading the file line by line and using regular expressions to match `* TODO` headings and `:HASH:` properties.

### 2. Implement `append-to-staging-org`
- In `components/org-staging/src/jstavel/org_staging/core.clj`:
    - Define `append-to-staging-org` to safely append a new Org-mode entry string to `staging.org`.
    - Ensure atomic write/append operations to prevent data corruption. `java.nio.file.Files/write` with `StandardOpenOption/APPEND` is suitable.
    - Add a newline before the new entry to ensure it's always on a new line in the **local** `staging.org`.

### 3. Implement `scan-and-stage-downloads`
- In `components/org-staging/src/jstavel/org_staging/core.clj`:
    - Define the `scan-and-stage-downloads` function.
    - It should accept `downloads-path` (e.g., `(Paths/get (System/getProperty "user.home") "Downloads")`), `assets-objects-root` (e.g., `(Paths/get (System/getenv "ORG_ROOT") "assets" "objects")` - **local path**), and `staging-org-path` (e.g., `(Paths/get (System/getenv "ORG_ROOT") "staging.org")` - **local path**) as `java.nio.file.Path` arguments.
    - Use `fp/list-audio-files` to get all audio files from `downloads-path`.
    - Filter the results to include *only* `.mp3` files (case-insensitive).
    - Call `build-asset-index` with `assets-objects-root` to get a set of already ingested asset hashes.
    - Call `read-staging-org` with `staging-org-path` to get a set of hashes already pending in `staging.org`.
    - For each filtered `.mp3` file found in `downloads-path`:
        - Calculate its SHA-256 hash using `deterministic-sha256-path`.
        - Check if this hash is present in the `ingested-asset-hashes` set OR the `pending-staging-hashes` set.
        - If the hash is *not* found in either set:
            - Construct an Org-mode `TODO` entry string including:
                - `* TODO <filename>`
                - `:PROPERTIES:`
                - `:HASH: <calculated-hash>`
                - `:SOURCE: <file-path-as-string>` (full path to the file in `~/Downloads/`)
                - `:END:`
            - Append this entry to the **local** `staging.org` using `append-to-staging-org`.
        - If the hash is found in either set, log an informational message indicating the file is already known and skip it.
    - The function must **not** move, delete, or modify files in `downloads-path`.

### 4. Expose Interface
- In `components/org-staging/src/jstavel/org_staging/interface.clj`:
    - Define `scan-and-stage-downloads`, delegating to `jstavel.org-staging.core/scan-and-stage-downloads`.

### 5. Write Unit Tests
- In `components/org-staging/test/jstavel/org_staging/interface_test.clj`:
    - Create a temporary fixture for a `Downloads` directory, an `assets/objects` directory, and a `staging.org` file. Ensure proper cleanup.
    - Test `read-staging-org`:
        - With an empty `staging.org`.
        - With a `staging.org` containing `TODO` entries with hashes.
        - With a `staging.org` containing entries without hashes (ensure they are skipped).
    - Test `append-to-staging-org`:
        - Append a new entry to an empty file.
        - Append multiple entries to an existing file.
    - Test `scan-and-stage-downloads`:
        - **Empty `Downloads`:** `staging.org` should remain empty.
        - **New `.mp3`:** One `.mp3` in `Downloads`, `staging.org` gets one `TODO` entry.
        - **Multiple New `.mp3`s:** Multiple `.mp3`s, `staging.org` gets multiple `TODO` entries.
        - **Already Ingested:** `.mp3` in `Downloads` whose hash already exists in `assets/objects` -> `staging.org` remains unchanged.
        - **Already Staged (Idempotency):** `.mp3` in `Downloads` whose hash is already in `staging.org` as a `TODO` -> `staging.org` remains unchanged.
        - **Mixed Scenarios:** Combinations of new, already ingested, and already staged files.
        - **Non-MP3 Files:** Ensure `.wav`, `.txt`, etc., are ignored.
    - Verify that `staging.org` is correctly updated and that `Downloads` content is untouched.

## Definition of Done (DoD)
- [] `read-staging-org` and `append-to-staging-org` helper functions are implemented and pass their unit tests.
- [] `scan-and-stage-downloads` function is implemented in `core.clj` and exposed in `interface.clj`.
- [] The worker correctly identifies `.mp3` files in the specified `downloads-path` and ignores other file types.
- [] The worker accurately determines if a file has been ingested (by checking the **local** `assets/objects/` using `build-asset-index`).
- [] The worker accurately determines if a file is already pending in the **local** `staging.org` (using `read-staging-org`).
- [] For each *new and not yet staged* `.mp3` file, a `TODO` entry with correct `:HASH:` and `:SOURCE:` properties is appended to the **local** `staging.org`.
- [] The worker strictly adheres to the "read-only" invariant for `downloads-path` (no move, delete, or modify operations).
- [] All unit tests for `scan-and-stage-downloads` cover empty, new, existing (in assets), existing (in staging), mixed, and ignored file type scenarios, and pass.
- [] The behavior of the `scan-and-stage-downloads` utility is proven to be idempotent.
