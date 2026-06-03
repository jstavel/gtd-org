# ADR 0005: Asset Storage Invariants and Human-in-the-Loop Ingestion
this is a copy of ADR from ~/org/docs/adr/0005-asset-storage-and-ingestion-invariants.md

*   **Status:** Accepted
*   **Date:** 2026-06-03

## Context
To prevent entropy in the file system and ensure `~/Downloads/` does not become a "digital junkyard," we need to standardize how assets are stored and how they are accepted into the system. The system must be predictable, safe against accidental deletion, and remain fully under the user's (Architect's) control.

## Decision
1. **Object-Store Model:** Assets are stored in
   `assets/objects/ab/cd/<UUID>/original.ext`. The structure uses UUID
   sharding (`ab/cd` – the first 4 characters of the UUID) for optimal
   performance and scalability of the file system.
2. **Strict Invariants:**
    - `objects/`: The immutable "Source of Truth." Each object has its own directory with a unique UUID.
    - `derived/`: Regeneratable data (transcripts, summaries, etc.). It must mirror the UUID structure of `objects/`.
    - `views/`: The navigation layer (symlinks or indexes). It must never contain primary data.
3. **Human-in-the-Loop Workflow:** - `~/Downloads/` is the transit zone. 
    - The worker only detects files, calculates their hashes, and registers them as `TODO` entries in `staging.org`.
    - No physical operation (move/delete) is performed without an
      explicit action flag (`INGEST`/`REJECT`/`IGNORE`) set by the
      user in `staging.org`.
4. **No-Inbox Invariant:** The `assets/inbox/` directory is not
   used. `~/Downloads/` is the only physical entry point, which must
   be regularly "vacuumed" by registration into `staging.org` and
   subsequent execution of user actions.

## Example

``` text
~/org/assets/
  objects/
    ab/
      cd/
        <UUID>/
          original.ext
          metadata.edn        ; optional

  derived/
    ab/
      cd/
        <UUID>/
          transcript.srt
          summary.md
          ocr.txt

  views/
    audio/
    ebooks/
    documents/
    plaud/
    by-year/
    by-project/
```

## Consequences
- **Robustness:** The system is fully auditable, and all changes are logged in `staging.org`.
- **Cleanliness:** `~/Downloads/` is always in a clean state because every item within it has an assigned status in the decision panel.
- **Integrity:** Strict separation of `objects/` and `derived/` allows for safe cleanup and data regeneration without the risk of losing primary value.
- **Control:** The Architect (user) retains full control over system operations; automation serves only as an executive layer following explicit approval.
