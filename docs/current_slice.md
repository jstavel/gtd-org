# Current Slice: [M1/Slice 1] Local Ingest and File Identity

## Context
This slice focuses on implementing the initial local ingestion capabilities within the `fp` (File Provider) component. The primary goal is to reliably discover audio files within specified directories, handle file paths immutably, and compute their cryptographic SHA-256 hash for content-based identity. This work adheres to ADR 0003 (Immutable Native Paths) and the `fp` component's invariants (ADR 0001, ADR 0002).

## Goal
Develop functions within the `fp` component to:
1. Recursively scan a given directory for audio files.
2. Filter for specific audio file extensions (`.mp3`, `.wav`, `.m4a`).
3. Compute the SHA-256 hash of these files, treating the hash as their immutable content-based identity.
All file path operations must exclusively use `java.nio.file.Path` objects.

## Target Files
- `components/fp/src/jstavel/fp/interface.clj`
- `components/fp/test/jstavel/fp/interface_test.clj`

## Technical Steps

### 1. File Discovery Function (`list-audio-files`)
- Create a public function `jstavel.fp.interface/list-audio-files` that takes a `java.nio.file.Path` representing a directory.
- This function must recursively traverse the directory.
- It must filter files based on the allowed audio extensions (`.mp3`, `.wav`, `.m4a`). The comparison should be case-insensitive.
- The function should return a collection of `java.nio.file.Path` objects representing the discovered audio files.


### 2. SHA-256 Computation Function (`sha256-file`)
- Create a public function `jstavel.fp.interface/sha256-file` that takes a `java.nio.file.Path` representing a file.
- It must read the file content and compute its SHA-256 hash.
- The hash should be returned as a hexadecimal string.

### 3. Integration & Testing
- Add comprehensive unit tests in `components/fp/test/jstavel/fp/interface_test.clj`.
- Test `list-audio-files` with:
    - An empty directory.
    - A directory with no audio files.
    - A directory with mixed file types and various audio files (including different cases for extensions).
    - Directories and files containing spaces and special characters in their names.
    - A nested directory structure.
- Test `sha256-file` with:
    - A known file and expected SHA-256 hash.
    - An empty file.
    - Ensure it correctly handles non-existent files (e.g., throws an appropriate exception or returns nil/error indicator).

## Lazy Processing Requirement
The `list-audio-files` function must return a lazy sequence (lazy-seq)
to ensure constant memory usage regardless of the directory size. It
must not realize the full list of files in memory; instead, it should
stream file paths from java.nio.file.Files/walk and process/filter
them incrementally."

## Definition of Done (DoD)
- [x] `components/fp/src/jstavel/fp/interface.clj` contains public functions `list-audio-files` and `sha256-file`.
- [x] All file path manipulations within `interface.clj` exclusively use `java.nio.file.Path` objects.
- [x] `list-audio-files` correctly identifies and returns only `.mp3`, `.wav`, and `.m4a` files (case-insensitive) as `java.nio.file.Path` objects.
- [ ] `sha256-file` correctly computes and returns the hexadecimal SHA-256 hash of a given `java.nio.file.Path`.
- [x] `components/fp/test/jstavel/fp/interface_test.clj` includes thorough unit tests for both functions covering specified scenarios and edge cases.
- [ ] All tests pass.
- [x] The `dummy-test` in `components/fp/test/jstavel/fp/interface_test.clj` has been removed.
