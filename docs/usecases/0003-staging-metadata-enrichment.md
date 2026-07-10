# Use Case: Staging Worker Metadata Enrichment

- **ID:** staging-metadata-enrichment
- **RFC:** docs/rfc/0001-staging-metadata-decision.org
- **ADR:** 0005 (Asset Storage Invariants), 0008 (Asset Storage Architecture)

### 1. Actors

- **Watcher:** Scans `~/Downloads/` for new `.mp3` files and initiates the pipeline.
- **Metadata Provider:** Extracts technical metadata from filename (date) and ffprobe (duration, bitrate).
- **Orchestrator:** Checks for duplicates, writes enriched TODO entries to `staging.org`.

### 2. Trigger (Precondition)

- A new `.mp3` file exists in the watched directory (`~/Downloads/` or `$ORG_ROOT/Downloads/`).
- The file was not previously registered in `staging.org` (checked by SHA-256 hash).
- The file was not previously ingested into `assets/objects/` (checked by SHA-256 hash).
- `ffprobe` is available on `PATH`.
- `$ORG_ROOT` environment variable is set (default: `~/org/`).

### 3. Flow (Main Scenario)

1. Watcher scans `~/Downloads/` for `.mp3` files (reusing `fp/list-audio-files`).
2. Watcher asks Metadata Provider for metadata for each new file.
3. Metadata Provider extracts:
   - `:CREATED:` — date from filename using regex pattern `\d{4}-\d{2}-\d{2}` (derived from `YYYY-MM-DD HH_MM_SS.mp3` naming convention).
   - `:DURATION:` — duration in `MM:SS` format via ffprobe (`-show_entries format=duration`).
   - `:BITRATE:` — bitrate via ffprobe.
   - `:HASH:` — SHA-256 of file content (reusing `fp/sha256-file`).
4. Watcher identifies the file as `Plaud-voice-record` (based on `.mp3` extension and filename pattern).
5. Watcher prepares the record for Orchestrator with all resolved properties.
6. Orchestrator checks for duplicates:
   - Hash found in `assets/objects/` index → skip (already ingested).
   - Hash found in `staging.org` → skip (already pending).
   - Hash unknown → proceed to write.
7. Orchestrator writes the enriched TODO entry to `staging.org`.

### 4. Staging.org Entry Format

```org
* TODO [12 min] 2026-07-07 16_53_06.mp3
  :PROPERTIES:
  :HASH:       a1b2c3d4e5f6...
  :PLAUD-ID:   
  :CREATED:    2026-07-07
  :DURATION:   04:45
  :BITRATE:    128k
  :SOURCE:     ~/Downloads/2026-07-07 16_53_06.mp3
  :SYNOPSIS:   
  :CLEANUP:
  :KIND:       Plaud-voice-record
  :END:
  :DECISION:
  - [ ] INGEST  (Move to assets/objects/UUID/)
  - [ ] REJECT  (Delete from Downloads)
  - [ ] IGNORE  (Keep in Downloads, remove from TODO)
  :END:
  :LOGBOOK:
  - [2026-07-08 Tue 15:30] Auto-detected by Staging Worker.
  :END:
```

**Property details:**

| Property | Source | Format | Notes |
|----------|--------|--------|-------|
| `:HASH:` | SHA-256 of file | 64 hex chars | Content identity |
| `:PLAUD-ID:` | Empty (future: ID3 tag or `.plaud-mapping.json`) | — | Deferred to future slice |
| `:CREATED:` | Filename regex | `YYYY-MM-DD` | Extracted from `YYYY-MM-DD HH_MM_SS.mp3` |
| `:DURATION:` | ffprobe | `MM:SS` | ffmpeg native format |
| `:BITRATE:` | ffprobe | e.g. `128k` | — |
| `:SOURCE:` | Filesystem path | Full path | Original location in Downloads |
| `:SYNOPSIS:` | Empty | — | User fills after listening |
| `:CLEANUP:` | Empty | — | Confirmation text for REJECT |
| `:KIND:` | Fixed | `Plaud-voice-record` | — |

**Headline format:** `* TODO [N min] filename` where `N` is ceiling(seconds / 60). Duration in headline is always visible — even when `:PROPERTIES:` and `:DECISION:` drawers are folded.

**Decision drawer:**
- `[ ] INGEST` — move file to `assets/objects/<UUID>/raw/`
- `[ ] REJECT` — delete file from Downloads (requires `:CLEANUP: Yes, delete it`)
- `[ ] IGNORE` — keep file in Downloads, remove from TODO

**LOGBOOK:** Every worker action appends a timestamped entry for full traceability.

### 5. Alternative Flows (Exceptions)

- **File already ingested (known hash):** The system skips the file and logs: `"Skipping already ingested file: <filename> (hash: <hash>)"`.
- **File already pending in staging.org:** The system skips the file and logs: `"Skipping already pending file: <filename> (hash: <hash>)"`.
- **ffprobe unavailable or fails:** Duration and bitrate are written as `"N/A"`. The system logs a warning. The TODO entry is still created — metadata can be enriched later.
- **Filename does not match date pattern:** `:CREATED:` is written as `"N/A"`. The system logs a warning. The TODO entry is still created.
- **No access to Downloads directory:** The system logs an error and terminates.
- **$ORG_ROOT not set:** Default to `~/org/`. Log an info message.

### 6. Postconditions (Invariants)

- A new `* TODO` entry exists in `$ORG_ROOT/staging.org` for every new `.mp3` file detected.
- The entry contains all resolved metadata properties (`:HASH:`, `:CREATED:`, `:DURATION:`, `:BITRATE:`, `:SOURCE:`, `:KIND:`).
- The entry contains an empty `:DECISION:` drawer with three checkboxes.
- The entry contains a `:LOGBOOK:` entry recording the auto-detection timestamp.
- The entry contains empty placeholders for `:PLAUD-ID:`, `:SYNOPSIS:`, and `:CLEANUP:`.
- The original file in `~/Downloads/` is never modified.
- `staging.org` appends are atomic.

### 7. Compliance / Audit Requirements

- Every staging operation (new file detected, duplicate skipped, metadata extraction failure) is logged to `journal.log` with timestamp, file hash, and operation result.
- Every new TODO entry in `staging.org` includes a `:LOGBOOK:` entry with auto-detection timestamp.
- The underlying file in `~/Downloads/` is never read-modified — only read for hashing and metadata extraction.

### 8. References

- RFC 0001: Staging.org Metadata Format & Decision Drawer
- ADR-0005: Asset Storage Invariants and Human-in-the-Loop Ingestion
- ADR-0008: Asset Storage Architecture and Pipeline Structure
- `~/org/h0_runway/org-staging.org`: Staging Worker Architect Specification Template
